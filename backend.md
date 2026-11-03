# Backend Architecture and Module Strategy

**Document status:** Living architecture decision record  
**Version:** 0.1  
**Last updated:** 2026-09-13  
**Status:** Working baseline; final implementation details remain open where explicitly marked

## 1. Purpose

This document records how the Spring Boot backend will be structured so that the product can be maintained as a real enterprise platform.

The backend must allow us to:

- Maintain each business capability in an identifiable module.
- Replace infrastructure implementations during maintenance without rewriting business features.
- Enable or disable product features per tenant through configuration.
- Protect tenant data from cross-tenant access.
- Keep database, Redis, authentication, email, and storage dependencies out of unrelated business code.
- Extract a module into a separate service later if scale or ownership requires it.

## 2. Confirmed technology direction

| Area | Direction | Status |
|---|---|---|
| Backend | Spring Boot | Confirmed |
| Primary database | MySQL | Confirmed first implementation |
| Cache/session infrastructure | Redis | Confirmed |
| Frontend | React, developed separately | Confirmed |
| Initial deployment style | One Spring Boot application | Working decision |
| Module changes | Maintenance/build/deployment time | Confirmed |
| Runtime code installation | Not required | Confirmed |
| Tenant identity | Tenant-local users owned by each company | Confirmed |
| Feature access | Tenant/company-specific enablement | Confirmed |

## 2.1 Implemented first vertical slice

The first production-shaped slice is tenant onboarding. It is intentionally small but crosses the full backend boundary:

```text
HTTP request
    -> onboarding controller and DTO validation
    -> tenant-management use case
    -> TenantOnboardingStore port
    -> MySQL persistence adapter
    -> one transaction:
         tenant
         tenant-local owner
         requested feature entitlements
         audit event
         transactional outbox event
```

The initial owner is created as an active tenant-local user with a BCrypt password hash. The endpoint is `/api/v1/onboarding/tenants` and is a bootstrap contract, not a statement that public onboarding is production-ready: rate limiting, email verification, invitation delivery, MFA, and final cookie/CSRF hardening remain required before customer exposure. Login is tenant-bound and stores an opaque session in Redis; later requests derive tenant context from that verified session.

This slice also demonstrates the intended module connection: business code knows the persistence interface, while the MySQL adapter knows SQL. No controller or business rule depends directly on `JdbcTemplate`.

The next implemented vertical slice is Project Management:

```text
POST /api/v1/projects
    -> verified tenant context
    -> PROJECTS entitlement
    -> project.manage permission
    -> ProjectManagementService
    -> ProjectStore port
    -> MySQL project row + PROJECT_CREATED outbox event
       in the same transaction
```

`GET /api/v1/projects` applies the same tenant and entitlement checks with `project.read`. This establishes the standard pattern for the future Sprint, Work, Time Tracking, Resource, and Release modules. The notification consumer also now processes onboarding and invitation events into `GET /api/v1/me/notifications` using an independent delivery record, retry policy, and dead-letter state.

Sprint Management is now the second domain slice. It exposes project-scoped sprint creation and listing, requires both `PROJECTS` and `SCRUM` entitlements, and checks `sprint.manage` or `sprint.read`. Migration `V8__sprints.sql` adds composite tenant-aware foreign keys from sprint to project and creator to tenant user. Sprint creation writes a `SPRINT_CREATED` outbox event in the same transaction.

Work Management is now the third domain slice. It supports one common work-item model for sprint tasks, bugs, stories, daily tasks, and side tasks. Project and sprint relationships are optional, so employees can work from the Employee Hub without moving through multiple screens. When relationships are present, database composite foreign keys enforce that the project, sprint, assignee, and creator belong to the same tenant. The initial APIs are `GET /api/v1/work-items` with optional filters and `POST /api/v1/work-items`; creation writes `WORK_ITEM_CREATED` in the same transaction.

Organization and Workforce is now the next tenant-scoped slice. Migration `V20__organization_workforce.sql` adds business-unit/department hierarchy, teams, and tenant-safe team memberships. The module exposes `/api/v1/tenant/organization/units` and `/api/v1/tenant/organization/teams`, requires the `ORGANIZATION_WORKFORCE` entitlement plus organization/team permissions, and records mutations in audit and transactional outbox tables. Team membership is intentionally separate from tenant roles: roles control permissions, while membership describes where an employee works and whether they are a member, lead, or backup lead.

Team delivery relationships are now explicit in `V21__team_delivery_relationships.sql`. A project can have multiple delivery teams, a sprint can have multiple delivery teams, and a work item can optionally have one owning team. All three relationships use tenant-scoped composite foreign keys and remain optional for existing work. Project/sprint relationship APIs require the organization entitlement and dedicated team relationship permissions; work-item filtering supports `teamId`. Manager workload responses also include active team memberships so capacity and assignment decisions can use the same team model.

Sprint lifecycle is now controlled rather than freely mutable. `PLANNED` can move to `ACTIVE` or `CANCELLED`; `ACTIVE` can move to `COMPLETED` or `CANCELLED`; terminal states cannot be reopened. Completion stores the actor, timestamp, and note, emits `SPRINT_STATUS_CHANGED` through the transactional outbox, and exposes a tenant-scoped progress summary with total, to-do, in-progress, blocked, completed, cancelled, and carry-over counts. Carry-over is calculated as sprint work that is not `DONE` or `CANCELLED`, so the next planning action can explicitly move or re-commit it.

Explicit carry-over is available through `POST /api/v1/projects/{projectId}/sprints/{sprintId}/carry-over`. It validates that the source sprint is completed, the target sprint is planned/active, and both belong to the same project and tenant. It can move all open work or a selected set of work-item IDs, updates the work items in one transaction, records the actor and counts in audit, and emits `SPRINT_WORK_CARRIED_OVER` through the outbox. The planning UI provides a source/target sprint action rather than silently moving unfinished work.

Resource planning now has a tenant-scoped `resource_allocation` model in `V23__resource_allocations.sql`. An allocation reserves a user's weekly minutes against a project and optional sprint/team for a date range. Creation validates active users, date bounds, feature/permission access, same-tenant composite foreign keys, and aggregate overlapping allocation capacity before writing. Allocation mutations create audit and outbox records. Managers can create and review planned allocations from the workload view; actual logged effort remains calculated from the central time-entry system.

Manager workload now joins active allocations for the selected week with central time entries. Each member can report planned allocation minutes, actual-vs-planned percentage, and `UNPLANNED`, `UNDER_PLAN`, `ON_TRACK`, or `OVER_PLAN` status alongside capacity utilization. The allocation query remains tenant-scoped and uses the date/user/status indexes from V23.

Work reassignment is now an append-only, auditable workflow. Updating an assignee validates the new owner is an active user in the same tenant, preserves all existing time entries and work-item identity, writes `work_item_assignment_history`, and emits `WORK_ITEM_REASSIGNED` through the transactional outbox with old owner, new owner, reason, and work context. The notification consumer creates separate in-app notifications for both previous and new owners. Assignment history is available at `GET /api/v1/work-items/{workItemId}/assignment-history`.

Emergency priority and scope changes use an explicit request workflow in `V25__work_scope_change_requests.sql`. A requester submits a priority and/or target sprint with a required reason and emergency flag. The target sprint must belong to the same tenant/project and be planned or active. An approver can approve or reject once; approval updates only the requested work-item fields, never sprint dates, and records the before/after values, decision actor, note, audit event, and outbox event. This preserves the sprint timeline and makes emergency scope decisions reviewable rather than silently mutating planning data.

The workflow is now surfaced in the web application. Employee task details provide a controlled priority-change request form and show request history. The manager workspace provides a tenant-scoped pending approval inbox with emergency markers and approve/reject actions. The backend remains authoritative; the UI does not bypass permission checks or directly mutate work-item scope.

## 3. Meaning of “plugin-like”

The product will use plugin-like modularity for maintenance, ownership, and replacement. It will not initially use a runtime plugin framework that installs Java code while the application is running.

There are four separate concerns:

1. **Business module:** Project, Sprint, Time Tracking, and similar product capabilities.
2. **Infrastructure adapter:** MySQL, PostgreSQL, Redis, email, storage, and messaging implementations.
3. **Tenant feature configuration:** Whether a company is allowed to use a deployed feature.
4. **Runtime plugin installation:** Installing new application code without rebuilding or redeploying.

The first three are required. The fourth is out of scope unless a future requirement explicitly introduces it.

Therefore, a module may be removed or replaced during maintenance by changing the build and deployment, while the running application remains unchanged until the new version is deployed.

## 4. Architectural style

The working architecture is a **feature-oriented modular monolith using ports and adapters**.

```text
React frontend
       |
Spring Boot application
       |
Business feature modules
       |
Ports / interfaces
       |
Infrastructure adapters
       |
MySQL | PostgreSQL | Redis | Email | Storage | Messaging
```

The initial application remains one deployable unit. Modules are separated by package, build, API, and dependency boundaries. Spring Modulith may be used to verify module boundaries, dependencies, events, and module-level tests.

## 5. Proposed repository structure

This is the current target structure. Exact Maven granularity is still open.

The working dependency graph and authorization contract are maintained in [Backend Module Dependency Map.md](Backend%20Module%20Dependency%20Map.md).

```text
backend/
├── app-bootstrap/
│   └── MainApplication.java
│
├── platform/
│   ├── shared-kernel/
│   ├── tenant-context/
│   ├── security-contract/
│   ├── feature-entitlement/
│   ├── audit-contract/
│   └── event-contract/
│
├── modules/
│   ├── tenant-management/
│   ├── identity-access/
│   ├── organization-workforce/
│   ├── product-management/
│   ├── portfolio-demand/
│   ├── project-management/
│   ├── sprint-management/
│   ├── work-management/
│   ├── resource-management/
│   ├── dependency-risk/
│   ├── time-tracking/
│   ├── release-management/
│   ├── reporting-control-tower/
│   ├── notification-collaboration/
│   └── integration-audit/
│
└── adapters/
    ├── persistence-mysql/
    ├── persistence-postgres/
    ├── cache-redis/
    ├── authentication-local/
    ├── authentication-oidc/
    ├── email-provider/
    ├── file-storage/
    └── messaging/
```

The exact feature list must remain aligned with the approved PRD and domain model. A module should be created around a meaningful business capability, not every individual class or table.

## 6. Internal structure of a business module

Each business module should follow a vertical feature structure:

```text
sprint-management/
├── api/
├── application/
├── domain/
├── ports/
├── web/
├── events/
├── configuration/
└── migrations/
```

### Responsibilities

- `domain`: business rules, aggregates, value objects, and domain services.
- `application`: use cases, commands, queries, transaction boundaries, and orchestration.
- `ports`: interfaces required from persistence or external systems.
- `api`: public contracts exposed to other backend modules.
- `web`: controllers and request/response mapping.
- `events`: public domain/application events.
- `configuration`: module bean registration and feature configuration.
- `migrations`: schema changes owned by the module, using the selected migration tool.

The domain should not depend on MySQL, Redis, HTTP clients, or provider-specific SDKs.

## 7. Database connectivity and module wiring

A business module does not create its own database connection. Spring Boot owns the database infrastructure centrally.

```text
SprintService
    |
    v
SprintRepository interface
    |
    v
MySqlSprintRepository
    |
    v
Spring Data / JDBC / JPA adapter
    |
    v
Shared Hikari DataSource
    |
    v
MySQL
```

Example interface inside the Sprint module:

```java
public interface SprintRepository {
    Optional<Sprint> findById(TenantId tenantId, SprintId sprintId);
    Sprint save(Sprint sprint);
}
```

The Sprint application service depends only on `SprintRepository`. The MySQL adapter implements it and is wired by Spring at startup.

The initial application will normally have one centrally managed datasource and connection pool. Tenant context and tenant-aware repository rules ensure that queries operate only within the verified tenant.

## 8. Maven dependency direction

The dependency direction must not point from business modules into infrastructure implementations.

```text
app-bootstrap
├── sprint-management
├── persistence-mysql
└── cache-redis

persistence-mysql
└── sprint-management (public ports/contracts only)

cache-redis
└── cache-contract / platform contracts
```

The important rule is:

```text
Business module → interface/port
Infrastructure adapter → implementation of that port
```

There must not be this dependency:

```text
SprintService → MySqlProjectRepository
```

There should be this dependency:

```text
SprintService → SprintRepository
MySqlSprintRepository → SprintRepository
```

## 9. Maintenance-time provider replacement

### MySQL to PostgreSQL

Today:

```text
app-bootstrap → persistence-mysql
```

Future maintenance release:

```text
app-bootstrap → persistence-postgres
```

The business module should remain unchanged. The replacement adapter owns:

- Database-specific repository implementations.
- Database-specific entities or mappings where needed.
- SQL queries that cannot be portable.
- Index definitions.
- Migration scripts.
- Database integration tests.

The migration is not assumed to be a one-line configuration change. It requires schema conversion, data migration, compatibility testing, performance testing, and deployment validation.

### Redis replacement or removal

Business modules should depend on ports such as:

- `CacheProvider`.
- `SessionStore`.
- `DistributedLock`.
- `IdempotencyStore`.

The Redis adapter implements these ports. A no-op or test implementation may be supplied where appropriate. If Redis is required for production sessions or distributed coordination, another production implementation must be available before Redis is removed.

## 10. Product feature enablement

Feature enablement is different from removing code from the deployment.

The code for a feature may be deployed once, while each tenant receives a different entitlement:

```text
Company A: Projects + Scrum + Time Tracking
Company B: Projects + Resource Management
Company C: Projects + Release Management + Time Tracking
```

Feature access must be enforced in both places:

- Frontend: hide or disable unavailable navigation and actions.
- Backend: reject unavailable API operations regardless of frontend behavior.

Feature configuration should support:

- Tenant/company-level enablement.
- Product/module entitlement.
- Permission checks.
- Dependency validation.
- Configuration versioning.
- Audit history.
- Cache invalidation.
- Default deny when configuration is missing or invalid.

Example dependency:

```text
Time Tracking
├── requires Tenant Management
├── requires Identity and Access
└── may integrate with Project and Sprint
```

The system must not enable a feature if its required dependencies are unavailable.

## 11. Tenant isolation and request context

Every tenant-aware operation must use a verified tenant context.

```text
Access token/session
        |
        v
Authentication filter
        |
        v
Verified TenantContext
        |
        v
Controller → Application Service → Repository
```

Rules:

- A user account is tenant-local.
- A session/access credential is bound to one tenant and one tenant user.
- A tenant ID supplied in a URL or request parameter is untrusted input.
- If a route tenant ID is present, it must match the verified context.
- Missing or conflicting tenant context must fail closed.
- The system must never silently fall back to a default business tenant.
- Background jobs must carry explicit tenant context.
- Events, files, reports, exports, cache keys, and integrations must retain tenant context.
- Redis keys must include tenant scope where the data is tenant-owned.

The initial database strategy is a shared MySQL schema with tenant-owned records carrying tenant identity. A dedicated database or hybrid topology may be introduced later for specific enterprise tiers, but it is not the initial default.

## 12. Authentication and authorization boundaries

Authentication and authorization are related but separate responsibilities.

### Authentication module

- Login and logout.
- Credential verification.
- Session creation and invalidation.
- Password reset.
- External identity providers.
- Token/session validation.

### Authorization module

- Tenant membership.
- Product access.
- Project and team access.
- Roles and permissions.
- Feature entitlements.
- Approval authority.

The first implementation uses tenant-owned RBAC tables: `tenant_role`, `tenant_role_permission`, and `tenant_user_role`. Permission keys are loaded into the verified session as `PERM_*` authorities, and feature modules call the centralized authorization contract. The initial onboarding owner receives a `COMPANY_ADMIN` role. Tenant administration can invite users, activate them through a one-time token, replace role assignments, suspend/offboard users, and revoke their Redis sessions. Feature entitlements are read and changed through a tenant-scoped service; business modules must call `requireEnabled` before exposing feature-owned operations.

Authentication providers should be replaceable, but authorization rules remain owned by the product.

## 13. Redis and caching rules

MySQL remains the source of truth. Redis is an infrastructure service for sessions, cache, locks, idempotency, and selected frequently read data.

The default read pattern is cache-aside:

```text
Read cache
 ├── hit → return cached value
 └── miss → read MySQL → populate cache → return value
```

Feature entitlement list responses use this pattern with a tenant-scoped Redis key and a five-minute TTL. This cache is only a read optimization for the administration/configuration list. `FeatureEntitlementService.requireEnabled(...)` always queries the authoritative MySQL store, so a stale or unavailable Redis value cannot grant access to a disabled feature. A Redis read/write failure falls back to MySQL for list reads; after a successful entitlement change, the tenant list key is evicted on a best-effort basis and naturally expires within the TTL if Redis is unavailable.

For writes:

```text
Write MySQL transaction
        ↓
Commit successfully
        ↓
Invalidate affected Redis keys
        ↓
Publish retryable invalidation/outbox event when required
```

Rules:

- MySQL is authoritative.
- Cache keys are tenant-scoped where applicable.
- Every cache has an explicit TTL and serialization policy.
- Sensitive authorization caches are invalidated immediately after relevant changes.
- Cache consistency must not rely only on token expiry.
- Cache stampede protection is required for hot keys.
- Cache failures must have an explicit fail-open or fail-closed policy per cache type.
- No raw Java native serialization for untrusted or long-lived cache data.

## 14. Database and migration rules

- MySQL 8+ and InnoDB are the first target.
- Use UTC consistently.
- Use Flyway for versioned database migrations.
- Do not use `ddl-auto=update` in production.
- Keep domain entities independent from database-specific column definitions where practical.
- Keep native SQL inside persistence adapters.
- Add tenant-aware indexes and unique constraints deliberately.
- Use composite indexes beginning with tenant identity for tenant-owned tables where query patterns require it.
- Use integration tests against the supported database.
- PostgreSQL support requires a separate adapter and migration/test profile if it is approved later.

Detailed persistence, hybrid JPA/JDBC, and migration rules are maintained in [Database Persistence and Migration Standards.md](Database%20Persistence%20and%20Migration%20Standards.md).

## 14.1 Hybrid JPA/JDBC persistence standard

The backend will use a hybrid persistence model:

- JPA/Hibernate is the default for transactional domain operations and entity lifecycle management.
- JDBC is used selectively for complex queries, reporting and analytics, bulk operations, imports/exports, and performance-sensitive access.
- Both approaches remain behind module repository and application-service abstractions.
- Tenant scoping is enforced centrally and must be present in both JPA and JDBC paths.
- JPA and JDBC use the same centrally managed `DataSource` and transaction boundary for the initial relational deployment.
- The application service owns the transaction boundary; repositories should not create independent connections or transactions.

Spring documents that `JpaTransactionManager` can expose a JPA transaction to JDBC code using the same `DataSource`, subject to the configured JPA dialect supporting the underlying connection. [Spring JPA transaction integration](https://docs.spring.io/spring-framework/reference/6.2/data-access/orm/jpa.html)

### JPA is preferred for

- Aggregate/entity lifecycle changes.
- Invariant enforcement through domain operations.
- Standard create, update, and delete use cases.
- Relationships and optimistic locking.
- Transactional workflows where entity state must be tracked.

### JDBC is preferred for

- Large read-only projections.
- Control-tower and reporting queries.
- Multi-table analytics.
- Bulk insert/update operations.
- Import and export pipelines.
- Keyset/pagination queries optimized for a known access pattern.
- Database-specific queries isolated inside the persistence adapter.

Spring Boot supports both JPA/Hibernate and direct JDBC access, while `JdbcTemplate`/`JdbcClient` provide parameterized access and exception translation. [Spring Boot SQL databases](https://docs.spring.io/spring-boot/reference/data/sql.html) [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)

### Hybrid safety rules

- Do not update the same entity through JDBC while a managed JPA instance for that entity remains in the same persistence context unless the use case explicitly controls synchronization.
- Flush JPA changes before a JDBC query that must observe those changes.
- After JDBC writes affecting entities already loaded by JPA, refresh or clear the relevant persistence context before reading those entities again.
- Prefer separate read/write paths when possible: JPA for domain writes and JDBC projections for reporting.
- JDBC bulk writes must preserve tenant predicates, authorization assumptions, audit requirements, timestamps, version fields, and cache invalidation behavior.
- Native SQL must use named parameters and remain inside the adapter.
- Every JDBC repository method must have a tenant-isolation test and query plan/performance evidence where it is performance-sensitive.
- Do not use JDBC merely to avoid learning or correctly configuring JPA; select it for a documented access pattern.

### Persistence decision record

The choice between JPA and JDBC is made per use case, not globally per module. A module may use both, but each repository method must document its persistence style and reason.

## 15. Cross-module communication

Modules may communicate through:

- Public application interfaces.
- Commands and queries.
- Versioned application/domain events.
- Outbox-backed integration events for reliable asynchronous processing.

Modules must not:

- Import another module's internal classes.
- Modify another module's tables through direct SQL.
- Use another module's repositories directly.
- Depend on global mutable static state.

Direct synchronous calls are allowed when a use case requires an immediate result, but dependency direction must remain explicit and acyclic. Events are preferred when the originating module should not know all consumers.

## 16. Reference project lessons

The external Spring Boot reference project was reviewed only for learning. We will not copy its structure.

Useful ideas to evaluate separately:

- Hikari connection pooling.
- Redis deployment modes.
- Graceful infrastructure shutdown.
- Audit request context.
- Retry and failure handling.

Patterns not to copy into this product:

- Global `controller`, `service`, `entity`, and `repository` folders for all features.
- A single POM containing every infrastructure dependency without boundaries.
- Direct business-code dependency on `RedisDao` or database classes.
- Generic reflection-based entity updates as the main business API.
- Tenant selection directly from an unverified request parameter.
- Default-tenant fallback for business requests.
- MySQL credentials in properties files.
- Production schema changes through Hibernate `ddl-auto=update`.

## 17. Testing requirements for modularity

The central test control center is maintained in [testing/](testing/) and contains the master inventory, development-versus-release gate policy, execution checklist, result history, and one-command runner. Executable tests remain beside the backend modules so Maven can discover them using standard conventions.

Each module should have:

- Domain unit tests.
- Application/use-case tests.
- API contract tests where applicable.
- Tenant-isolation tests.
- Authorization tests.
- Persistence adapter integration tests.
- Cache behavior tests where the module uses caching.
- Module-boundary verification in CI.

The build must fail when a module accesses another module's internal package or creates an unauthorized dependency cycle.

## 18. Open decisions before implementation

These items must be decided before code structure is frozen:

1. Exact Maven module boundaries versus package-level modules.
2. Whether Spring Modulith will be mandatory in the build.
3. Exact JPA/JDBC repository conventions and review checklist.
5. Final shared-database versus database-per-tenant topology.
6. Exact authentication implementation and external identity support.
7. Whether optional features need separate deployable artifacts or only tenant entitlements.
8. Module dependency manifest and feature versioning format.
9. Production outbox operations: metrics, alerting, replay tooling, and dead-letter administration.
10. Production deployment packaging: executable JAR, container image, or another target.

## 18.1 Additional concerns requiring focused decisions

The following concerns were identified during security and architecture research. They are tracked here so they are not lost, but they are not final implementation decisions yet.

### Authorization model growth

Simple roles will not be enough for all product scenarios. The authorization design may need a combination of RBAC and resource/attribute-based rules:

- Platform administrator versus company administrator.
- Product, project, team, and resource scope.
- Manager approval scope.
- Temporary access and delegated access.
- User offboarding and role demotion.
- Cross-team or cross-project collaboration.

The policy model must remain centrally testable and must not be duplicated in individual controllers.

### Concurrency and state transitions

Idempotency does not solve every duplicate or race condition. We also need explicit concurrency rules for:

- Two users editing the same task or sprint.
- Simultaneous time entry or timesheet approval.
- Resource allocation over capacity.
- Sprint closure and reopening.
- Release status transitions.
- User offboarding while work is being assigned.

Possible controls include optimistic locking, unique constraints, transactional state machines, and database-level constraints.

### Asynchronous processing and delivery guarantees

The system must define delivery behavior for jobs and events:

- At-most-once, at-least-once, or effectively-once processing.
- Outbox storage and retry policy.
- Dead-letter handling.
- Duplicate event handling.
- Tenant context propagation.
- Ordering requirements.
- Safe replay after a failure.

“Exactly once” should not be assumed merely because a message broker or Redis is present.

### Data governance and privacy

The product will contain employee, customer, project, time, and audit data. We need policies for:

- Data classification.
- Retention and deletion.
- Legal hold.
- Tenant export.
- Tenant deletion.
- Personal-data access and correction.
- Audit-log retention.
- Data residency if customers require it.
- Backup encryption and restore isolation.

Tenant deletion and backup restoration must not accidentally expose data to another tenant.

Retention is tiered: detailed completed-Sprint operational activity remains in the hot application experience for four months by default, then is archived or purged only after final summaries and canonical facts are preserved. Audit/security evidence, time/approval records, legal holds, and business records follow separate protected policies. Retention workers must be tenant-scoped, bounded, idempotent, resumable, and auditable; they must not load all history into memory. See [Data Retention, Archiving, and Log Policy](Data%20Retention%2C%20Archiving%2C%20and%20Log%20Policy.md).

### API and contract evolution

Modules and frontend clients will evolve independently. Before implementation, define:

- API versioning policy.
- Backward-compatible change rules.
- Deprecation period.
- Event schema versioning.
- Idempotency behavior across API versions.
- Database migration rollback and forward-only rules.

### Operational security

Security must also cover the deployed environment:

- Network segmentation between the application, database, Redis, and external services.
- Separate application and migration credentials.
- Secret rotation without source-code changes.
- Dependency and container patching.
- Centralized alerting.
- Backup and disaster-recovery exercises.
- Security incident response and emergency session revocation.

## 18.2 Events, notifications, email, and push

The product needs an explicit event and notification subsystem. These are related but different concepts.

### Business events

A business event records that something meaningful happened:

- `SprintStarted`.
- `WorkItemAssigned`.
- `TimesheetSubmitted`.
- `UserOffboarded`.
- `ReleaseAtRiskDetected`.
- `FeatureEnabledForTenant`.

Events allow other modules to react without the originating module directly knowing every consumer. They may trigger audit records, cache invalidation, reporting updates, integrations, or notifications.

Events must carry:

- Event ID.
- Event type and version.
- Tenant ID.
- Actor or system source.
- Aggregate/resource ID.
- Occurred-at timestamp.
- Correlation and causation IDs where applicable.
- A safe, minimal payload.

### Notifications

A notification is a user-facing message derived from an event or direct application action. The notification system should decide:

- Which users or teams receive it.
- Which channel is appropriate.
- Whether the recipient has disabled the channel.
- Whether the notification is urgent or informational.
- Whether duplicate notifications should be suppressed.
- Whether quiet hours or tenant policies apply.

The system should not send email or push directly from a controller or inside the main database transaction.

Recommended flow:

```text
Business operation
        ↓
Database transaction
        ↓
Transactional outbox event
        ↓
Event worker / notification orchestrator
        ↓
Recipient and preference evaluation
        ↓
Email / in-app / web push / mobile push adapter
        ↓
Delivery status, retry, and dead-letter handling
```

### Required and optional channels

| Capability | Initial position |
|---|---|
| Internal application events | Required for modular integration and reliable side effects |
| In-app notification center | Required product capability |
| Transactional email | Required for invitations, email verification, password reset, and important workflow messages |
| General email notifications | Configurable by tenant, user, and notification type |
| Web push | Optional channel adapter |
| Mobile push through FCM/APNs | Optional channel adapter when mobile clients require it |
| SMS or messaging apps | Future optional adapters |
| External webhooks/integration events | Required where the integration strategy requires them |

Push notifications should not be forced on every tenant or user. Push requires device/browser registration, token lifecycle management, permission handling, provider failure handling, and user preferences.

### Provider abstraction

Business modules must use notification contracts rather than provider SDKs:

```text
NotificationOrchestrator
        ↓
EmailSender / PushSender / InAppNotificationStore interfaces
        ↓
SMTP / SES / SendGrid / FCM / APNs / Web Push / MySQL adapters
```

This allows an email or push provider to be replaced during maintenance without changing Project, Sprint, Time Tracking, or other business modules.

### Reliability and security rules

- Use the transactional outbox so an event is not lost after the database transaction commits.
- Separate event publication from consumer processing. A single business event may have independent notification, audit, reporting, cache, and integration delivery records.
- Store one delivery record per applicable consumer with `UNIQUE(event_id, consumer)`.
- Use `event_id + consumer` as the technical idempotency key and enforce it with database uniqueness and consumer-side idempotent writes.
- Use exponential retry backoff, maximum attempts, transient/permanent/schema failure classification, and a dead-letter state for failed delivery.
- Treat `SUCCESS`, `RETRY`, `DEAD_LETTER`, and `PROCESSING` as delivery state, not as one global event state. A failed Telegram delivery must not roll back a successful in-app delivery.
- Claim deliveries with a bounded processing lease. A worker crash must make an abandoned `PROCESSING` row eligible for recovery after the lease expires.
- Use a separate delivery record and consumer key for every channel/consumer, such as `notification.in_app` and `notification.telegram`; enforce `UNIQUE(event_id, consumer)` in MySQL.
- Classify malformed or unsupported event payloads as permanent failures and send them to dead letter; retry only transient provider or infrastructure failures.
- Store delivery attempts and final status without storing unnecessary sensitive payloads.
- Scope events, delivery records, notification records, preferences, and delivery keys to the tenant.
- Every asynchronous consumer must establish an explicit verified tenant execution context; it must not inherit a request thread context.
- Do not put passwords, session IDs, access tokens, or sensitive personal data into notifications or event payloads unless explicitly required and protected.
- Support tenant branding, templates, locale, timezone, channel preferences, quiet hours, and unsubscribe rules where applicable.
- Keep authentication and security emails separately protected from ordinary notification preferences.
- Track correlation IDs so a notification can be traced back to the originating operation.

The current implementation has a generic dispatcher with independent in-app and Telegram consumers. It creates or claims one delivery row per consumer, uses exponential backoff with a maximum of five attempts, recovers stale processing leases after five minutes, and preserves tenant context from the outbox row. Company administrators can inspect tenant-scoped delivery records through `GET /api/v1/tenant/outbox/deliveries` and replay a dead-letter delivery through `POST /api/v1/tenant/outbox/deliveries/{deliveryId}/replay`. Replay only resets the delivery state; the normal dispatcher performs the next attempt. Operational metrics are exposed through the authenticated actuator metrics endpoint, including `companyos.outbox.delivery.count`, `companyos.outbox.delivery.overdue`, `companyos.outbox.refresh.failures`, and `companyos.outbox.refresh.last_epoch_seconds`. Metric labels intentionally exclude tenant IDs and payload data to prevent cardinality and privacy problems. The remaining operational work is alert policy and additional channel adapters.

### Tenant-configurable external channels

Notification delivery must be configurable per tenant. A company may configure one or more channels and may use different providers from another company.

```text
Tenant A → Email + Slack
Tenant B → Telegram + Email
Tenant C → WhatsApp + Custom Webhook
Tenant D → Email + Mobile Push
```

The platform should introduce three separate concepts:

1. **Channel type:** Email, Slack, Telegram, WhatsApp, mobile push, web push, or webhook.
2. **Provider connection:** The tenant's configured credentials, OAuth installation, phone number, bot, workspace, or endpoint.
3. **Notification policy:** Which events go to which recipients and channels, with preference, fallback, severity, and quiet-hour rules.

Business modules must request a provider-neutral notification. They must not know whether delivery happens through Slack, Telegram, WhatsApp, or email.

```text
Project/Sprint/Time Module
        ↓
NotificationOrchestrator
        ↓
Channel adapter interface
        ├── Email adapter
        ├── Slack adapter
        ├── Telegram adapter
        ├── WhatsApp adapter
        ├── Push adapter
        └── Custom webhook adapter
```

### Connection configuration

Tenant administrators should be able to add, test, disable, rotate, and remove connections through the administration portal. Connection records must be tenant-scoped and must include:

- Channel and provider type.
- Connection status and last health-check result.
- Credential reference, never a plain credential value in ordinary configuration tables.
- Allowed scopes or capabilities.
- Destination/workspace/channel/chat/phone details where applicable.
- Created-by, updated-by, and audit history.
- Configuration version.
- Webhook registration state where applicable.

Credentials and provider tokens must be encrypted or stored in a secret-management system. They must not be included in logs, event payloads, exports, or normal API responses.

### Provider-specific findings

- **Email:** Support SMTP and at least one API-based provider through adapters. Email providers expose delivery, bounce, complaint, and failure events, so delivery status should be stored. Amazon SES, for example, supports configuration sets and event destinations for these outcomes.
- **Slack:** Support a simple incoming-webhook connection for basic outbound messages and an OAuth installation flow when the tenant needs workspace/channel selection, granular scopes, or richer API features. Slack OAuth grants scopes and workspace-specific tokens; webhook URLs and tokens must be treated as secrets.
- **Telegram:** Support a bot-based connection with tenant-specific bot token and destination chat configuration. If inbound commands or delivery callbacks are later required, use a verified HTTPS webhook with a secret token and idempotent update processing.
- **WhatsApp:** Use the official WhatsApp Business Platform/Cloud API or an approved provider adapter. Expect business-account and phone-number setup, access tokens, approved message templates for applicable notifications, opt-in/consent requirements, and delivery-status webhooks. This is a higher-complexity connector and should not be made a mandatory first implementation.
- **Customer-owned systems:** Provide a generic outbound webhook adapter first. It must support HTTPS-only destinations, signed requests, timestamp/replay protection, allow-listed events, retries, idempotency, delivery logs, and a dead-letter state. A full customer-written Java plugin is not required initially.

### Recommended rollout

| Capability | Recommendation |
|---|---|
| In-app notification center | Core first implementation |
| Transactional email | Core first implementation |
| Generic signed outbound webhook | Core integration foundation for customer-owned systems |
| Slack | Early optional connector; webhook first, OAuth when richer integration is required |
| Telegram | Optional connector after the generic channel model is proven |
| WhatsApp | Later connector because of account, template, consent, and provider requirements |
| Web/mobile push | Optional connector when web/mobile notification requirements are confirmed |
| SMS and other messaging platforms | Future adapters |

The connector framework should be implemented before all individual providers. This prevents the business modules from becoming dependent on the first provider we happen to support.

## 19. Research references

- [Spring Modulith](https://spring.io/projects/spring-modulith/)
- [Spring Modulith module verification](https://docs.spring.io/spring-modulith/reference/verification.html)
- [Spring Modulith application events](https://docs.spring.io/spring-modulith/reference/events.html)
- [Event and Outbox Architecture](Event%20and%20Outbox%20Architecture.md)
- [Spring Modulith module testing](https://docs.spring.io/spring-modulith/reference/testing.html)
- [Spring Boot custom auto-configuration](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Java ServiceLoader](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/ServiceLoader.html)
- [OSGi module specification](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.module.html)
- [Slack OAuth installation](https://api.slack.com/authentication/oauth-v2)
- [Slack incoming webhooks](https://api.slack.com/messaging/webhooks)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Amazon SES configuration sets and event publishing](https://docs.aws.amazon.com/ses/latest/dg/using-configuration-sets.html)
- [Meta WhatsApp Cloud API reference collection](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api)

## 20. Security baseline

Security is a cross-cutting platform capability. Every feature module must use the shared security controls rather than implementing its own authentication, tenant checks, or permission logic.

Detailed threat-model and authorization rules are maintained in [Security Architecture and Threat Model.md](Security%20Architecture%20and%20Threat%20Model.md).

### 20.0 Security target and freeze status

The security foundation is now frozen at the architecture level:

- Target **OWASP ASVS 5.0 Level 2** for the production application. OWASP describes Level 2 as the recommended target for most applications handling sensitive data.
- Use the **OWASP API Security Top 10** as the minimum API threat checklist, especially broken object/function authorization, broken authentication, resource consumption, SSRF, security misconfiguration, and unsafe API consumption.
- Use Spring Security for the security filter chain, authentication integration, CSRF, session/security-context handling, and request authorization.
- Apply explicit browser response protections: configurable CSP, `X-Frame-Options: DENY`, strict referrer policy, and stateless server-side session handling.
- Use centralized, server-side, default-deny authorization with tenant and resource scope.
- Treat security controls as shared platform modules consumed by every business feature.
- Security acceptance must include automated negative-path tests, dependency/secret scanning, threat modeling, and pre-production security testing.

### Adopted default assumptions

Unless a customer contract, regulatory requirement, or later product decision overrides them, use these defaults:

- General B2B SaaS security profile.
- MFA mandatory for platform administrators and company administrators.
- Local authentication for the first implementation.
- Replaceable OIDC/SSO adapter designed from the beginning, with SSO implementation added when required.
- Shared MySQL schema with strict tenant isolation for the initial deployment.
- Email and in-app notifications as the first notification channels.
- External notification channels implemented as replaceable adapters.
- Audit retention and session duration configurable through deployment/company policy rather than hard-coded permanently.

The following details remain intentionally open for later policy configuration:

- Exact MFA factors and enrollment/recovery flow.
- Exact session TTL, idle timeout, and concurrent-session policy.
- Final rate limits and adaptive abuse controls.
- Compliance, residency, and customer-specific retention requirements.
- WAF, API gateway, and cloud security products.

No application can honestly be declared permanently “SQL-injection proof.” The design should make unsafe behavior difficult, reject unsafe patterns in review and CI, and verify the controls with negative tests.

### 20.1 Authentication

- Use Spring Security as the security filter and authentication framework.
- Authenticate through a replaceable authentication adapter.
- Bind every session/access credential to exactly one tenant and one tenant-local user.
- Store session state in Redis where required, with tenant-scoped keys and explicit TTLs.
- Prefer an opaque session identifier in a `Secure`, `HttpOnly`, and appropriate `SameSite` cookie for the browser application.
- Do not store authentication tokens, session IDs, or refresh credentials in browser `localStorage` or `sessionStorage`.
- Rotate the session identifier after login and privilege changes.
- Revoke sessions immediately on logout, user offboarding, credential reset, privilege reduction, or security incident.
- Store passwords only as adaptive password hashes. Argon2id is the preferred baseline; bcrypt or PBKDF2 may be used where compatibility or compliance requires it.
- Add login throttling, account lockout or progressive delay, password reset protection, and MFA support. MFA must be enforceable for privileged users and designed so it can be enforced for all users according to the selected security policy.
- Public login, onboarding, and invitation activation now use Redis-backed atomic rate-limit buckets. Login applies both IP and normalized tenant-user identity limits; onboarding and activation apply IP limits. Limits, windows, enablement, and fail-closed behavior are deployment-configurable, and rate-limit keys are hashed rather than storing raw IPs or emails in Redis key names.
- Do not log passwords, session IDs, access tokens, refresh tokens, or complete authorization headers.

### 20.1.1 HTTP request data placement

HTTP location does not make a value trustworthy or confidential. Headers, paths, query parameters, and JSON bodies are all untrusted until validated and all travel only over HTTPS in deployed environments.

Use these conventions:

| Location | Use for | Do not use for |
|---|---|---|
| Secure cookie or `Authorization` header | Session credential or bearer credential according to the client/auth contract | Tenant selection, business fields, passwords beyond the login request, or ordinary business input |
| Request headers | Credentials, correlation/trace ID, locale/timezone preference, idempotency key, content negotiation, and safe request metadata | Sensitive business payloads, secrets other than the credential itself, or authorization decisions based on a caller-supplied tenant ID |
| Path parameters | Non-sensitive resource identity and hierarchy, such as `/projects/{projectId}/work-items/{workItemId}` | Access tokens, passwords, private notes, email addresses, customer data, or any data that should not appear in URLs/logs/history |
| Query parameters | Non-sensitive filters, pagination, sorting, and view options for reads | Credentials, secrets, sensitive personal data, large/complex commands, or state-changing business payloads |
| JSON request body | Validated create/update/action command fields, including sensitive business values when the API requires them | Authentication credentials outside specific auth endpoints, or unbounded/unknown fields directly bound to entities |

Rules:

- The verified tenant and user come from the authenticated credential/session, never from an ordinary header, path, query parameter, or body field.
- Sensitive business data may be sent in a JSON body over HTTPS when required. It is protected by authorization, validation, data classification, log redaction, access controls, and encryption at rest where applicable; placing it in a header provides no security advantage.
- Credentials and sensitive values must never appear in URLs because URLs commonly reach browser history, proxies, access logs, analytics, referrers, bookmarks, and support screenshots.
- Route/query identifiers are selectors, not authority. The backend resolves them only within the verified tenant and authorization scope.
- `GET` requests are read-only and use path/query parameters; JSON bodies are used for create/update/action commands and selected search endpoints where a complex validated filter body is necessary.

### 20.2 Authorization

- Default deny: an authenticated user receives no business access unless a policy grants it.
- Enforce authorization on the backend; hiding a frontend page is not security.
- Check authentication, tenant membership, product entitlement, role, permission, and resource ownership as required by the operation.
- Protect every object-level operation against IDOR/BOLA vulnerabilities. Knowing an ID must not grant access.
- Separate platform-admin privileges from tenant-admin privileges.
- Support the product's tenant, product, project, team, and resource-level access model.
- Centralize policy evaluation and expose a clear authorization interface to feature modules.
- Audit role grants, role removals, permission changes, feature changes, impersonation, and administrative actions.

### 20.3 Tenant isolation

- Derive tenant context from verified authentication/session data, never from an untrusted request parameter alone.
- If a URL or request contains a tenant ID, compare it with the verified tenant context and reject mismatches.
- Reject missing tenant context for tenant-scoped business operations; never use a default business tenant.
- Require tenant-aware repository methods and query specifications.
- Include tenant identity in tenant-owned unique constraints and indexes where appropriate.
- Apply tenant checks to database reads, writes, reports, exports, files, notifications, integrations, jobs, events, and cache keys.
- Propagate tenant context explicitly to asynchronous jobs and event consumers.
- Test two or more tenants in every cross-tenant security test suite.

### 20.4 SQL injection and database safety

- Use prepared statements and parameter binding for all values.
- Use Spring Data query binding, JDBC named parameters, or another parameterized API.
- Never concatenate user input into SQL, JPQL, HQL, filter expressions, or database commands.
- For dynamic sorting, filtering fields, table names, or directions, use an explicit server-side allow-list. SQL parameters cannot safely represent arbitrary identifiers.
- Do not expose a generic reflection-based update endpoint as a substitute for feature-specific validation and authorization.
- Keep native SQL inside persistence adapters and review it as security-sensitive code.
- Use a least-privileged application database account.
- Keep migration privileges separate from normal request privileges where practical.
- Add injection tests for search, filtering, sorting, reporting, import, patch, and export endpoints.

### 20.5 Input and output protection

- Validate request DTOs at the API boundary and validate business invariants inside the domain/application layer.
- Apply size, length, range, enum, format, and pagination limits.
- Use allow-list validation for identifiers, sort fields, file types, and integration destinations.
- Prevent mass assignment by mapping request DTOs to allowed commands instead of binding arbitrary JSON directly to entities.
- Return safe error responses without stack traces, SQL, credentials, filesystem paths, or internal class names.
- Use context-appropriate output encoding and safe rendering for user-generated content.
- Treat uploaded files as untrusted: validate content, size, extension, storage path, malware policy, and download authorization.

### 20.6 Browser and API security

- Require HTTPS in deployed environments and configure secure transport headers.
- Use CSRF protection when browser authentication uses cookies. `SameSite` is defense in depth, not a universal replacement for CSRF protection.
- Configure CORS with an explicit allow-list; never use unrestricted origins with credentials.
- Use security headers including a reviewed Content Security Policy, frame protection, content type protection, and restrictive referrer policy.
- Ensure state-changing operations use `POST`, `PUT`, `PATCH`, or `DELETE`, never `GET`.
- Apply request body, upload, query, pagination, and batch-size limits.
- Add rate limits for login, password reset, invitations, exports, expensive searches, and public endpoints.
- Use idempotency keys for operations that may be retried, such as time submission, payments if later added, invitations, and imports.
- Protect outbound integrations against SSRF by validating destination URLs and restricting network access.

### 20.6.1 Idempotency policy

Idempotency is an API-design decision, not a requirement for every endpoint.

Use an idempotency key when a client, network retry, job retry, or message redelivery could accidentally perform the same business operation more than once. Typical candidates include:

- Creating a time entry or submitting a timesheet.
- Sending an invitation.
- Creating an import or export job.
- Starting a long-running workflow.
- Processing a webhook or external integration event.
- Creating a release or deployment operation.
- Payment or billing operations if introduced later.

Usually do not require an idempotency key for:

- Safe `GET`, `HEAD`, and `OPTIONS` requests.
- Pure reads and searches.
- Updates that already use a strong resource version or conditional request design.
- Deletes where the operation is already safely repeatable and the API contract explicitly defines repeated deletion behavior.

For each API that uses idempotency, the design must define:

1. The idempotency-key header and format.
2. The tenant and authenticated-user scope of the key.
3. The request fingerprint; reusing a key with a different payload must fail.
4. The retention period and cleanup policy.
5. Whether the original successful response is replayed.
6. How validation and failed requests are recorded.
7. How the key is reserved atomically before work begins.
8. How the idempotency record and business transaction remain consistent.
9. How concurrent requests using the same key are handled.
10. How retries behave after timeout, server failure, or partial downstream processing.

The initial implementation uses a tenant-scoped idempotency port backed by Redis. MySQL remains the source of truth for business records, but idempotency keys and replay responses stay out of business tables:

```text
API handler
    ↓
IdempotencyService
    ↓
IdempotencyStore interface
    └── Redis implementation (initial and required implementation)
```

An idempotency key is not a replacement for database unique constraints, transaction boundaries, optimistic locking, or authorization. Those controls must still exist where the business operation requires them.

### Redis idempotency operating rules

- Keys are hashed under the `company-os:idempotency:v1:` namespace; raw tenant IDs and caller keys are not used as Redis key names.
- Processing reservations expire after a bounded TTL; completed responses expire after a bounded replay window. No idempotency key is permanent.
- The stored response is limited to the small API response needed for replay. Large files, reports, secrets, or arbitrary request bodies must never be placed in the idempotency value.
- Reservation uses Redis `SET` with `NX` and expiry. Completion and rollback release use atomic server-side scripts that compare the request hash before changing or deleting a key.
- Production must provide enough dedicated Redis capacity for the configured replay window. A correctness-sensitive idempotency namespace must not share an eviction policy that can silently evict active idempotency keys; use a dedicated Redis deployment or a `noeviction` policy with capacity alerts.
- Monitor `used_memory`, `evicted_keys`, `expired_keys`, rejected writes, Redis availability, and idempotency conflict/in-progress rates. An eviction or Redis loss can remove the replay record; Redis-only idempotency therefore provides bounded retry protection, not an absolute exactly-once guarantee across data loss.
- If Redis is unavailable, the write path fails closed rather than writing the business record without idempotency protection.

## 21. Resource capacity and manager workload

Resource visibility is derived from existing work and time data rather than creating a separate manual allocation screen:

```text
Tenant members + assigned open work + central time entries
                         |
                         v
              manager workload view
```

The first manager endpoint is:

```text
GET /api/v1/manager/workload?weekStart={optional Monday date}
```

For each active tenant member it returns assigned open work, recent activity, weekly logged minutes, configured weekly capacity, utilization percentage, and a derived load status. The initial default capacity is 2,400 minutes (40 hours) per week. A `resource_user_capacity` row can override capacity per tenant user; this keeps the model ready for part-time employees, contractors, and future leave/availability rules without hard-coding every employee as eight hours per day.

The status is intentionally a management signal, not a staffing decision: `AVAILABLE` means no open assigned work and no logged time in the selected week; `BALANCED` is below 80% utilization; `AT_RISK` is 80–99%; and `OVERLOADED` is 100% or more. The UI must show the underlying counts and minutes so managers can inspect why a status was derived. Later capacity policies can replace these defaults through configuration.

## 22. Release, dependency, and control-tower foundations

Release and dependency data are separate tenant-owned aggregates linked to existing project and work-item records:

```text
Project -> Release -> Work items
Work item -> Dependency -> Work item
```

Initial endpoints:

```text
GET/POST /api/v1/projects/{projectId}/releases
POST     /api/v1/projects/{projectId}/releases/{releaseId}/work-items/{workItemId}
GET/POST /api/v1/work-items/{workItemId}/dependencies
GET      /api/v1/manager/control-tower
```

The control tower is currently a tenant-safe read model over active projects, active sprints, open work, dependency risks, release risk, weekly logged time, and active members. It is feature-gated by `REPORTING`; release and dependency operations require their respective entitlements. This keeps the management view useful while leaving room for later materialized reporting tables when scale requires them.

### 20.7 Redis security

- Use TLS and Redis authentication/ACLs in deployed environments.
- Prefix keys with environment, tenant, purpose, and version where appropriate.
- Do not treat cached authorization data as the only source of truth.
- Invalidate security-sensitive caches after permission, role, feature, or user-status changes.
- Do not place passwords or raw access tokens in ordinary application caches.
- Define fail-open versus fail-closed behavior per cache type. Authentication, authorization, and revocation caches must fail closed or use a safe authoritative fallback.
- Add tests for tenant key collisions and stale authorization data.

### 20.8 Secrets and cryptography

- Do not commit database passwords, Redis passwords, private keys, keystore passwords, or API credentials to source control.
- Use environment-specific secret management and rotate secrets.
- Encrypt sensitive data at rest only where required by the data classification and threat model; manage encryption keys separately from encrypted data.
- Use approved cryptographic libraries and algorithms; do not invent encryption or token formats.
- Redact sensitive values in logs, tracing, metrics, exception messages, and audit records.

### 20.9 Audit, monitoring, and incident response

- Record security-relevant events with timestamp, tenant, actor, target, action, result, correlation ID, and source context.
- Protect audit records from ordinary tenant users and from modification by normal business paths.
- Never record passwords, complete tokens, session IDs, or sensitive payloads in audit logs.
- Monitor failed logins, unusual authorization failures, cross-tenant mismatch attempts, privilege changes, bulk exports, and repeated rate-limit violations.
- Define alerting, incident response, session revocation, credential rotation, and evidence-retention procedures.

### 20.10 Security verification in CI/CD

- Unit-test authentication and authorization policies.
- Run cross-tenant negative-path tests for every tenant-owned feature.
- Test horizontal privilege escalation and vertical privilege escalation.
- Test disabled feature access directly through the API.
- Run dependency vulnerability scanning, secret scanning, static analysis, and API security tests.
- Run integration tests against MySQL and Redis test instances.
- Test cache invalidation after permission and tenant-membership changes.
- Perform threat modeling for each major feature and before significant architecture changes.
- Plan independent penetration testing before production launch and after major security changes.

Security verification should be mapped to versioned ASVS requirements so that security work can be tracked as pass/fail evidence rather than as an informal checklist.

## 21. Employee Work Hub and central time tracking

Time entry is a core employee workflow, not a separate reporting afterthought. The system keeps one central time-entry model so an employee can log work from whichever context is most convenient:

```text
Employee Work Hub / Work Item / Project / Sprint / Timer
                         |
                         v
                 central time_entry
                         |
                         v
                timesheets and reporting
```

The first API slice is:

```text
GET  /api/v1/time-entries
POST /api/v1/time-entries
GET  /api/v1/me/work-hub
```

The Work Hub returns the signed-in employee's assigned work items and recent time entries, allowing daily tasks and sprint tasks to be handled without duplicating updates across multiple screens. The backend remains the source of truth: the tenant and user are taken from the authenticated session, feature entitlements are checked centrally, and all writes use a transaction that persists the time entry and `TIME_ENTRY_CREATED` outbox event together.

The initial model supports quick hours, timer, start/end, bulk, and suggested sources. Customer-specific time policies, approval cadence, overtime/weekend rules, and enabled entry methods remain configurable; they are not hard-coded into the core time-entry contract. Time-entry creation now requires a tenant-and-user-scoped `Idempotency-Key` backed by Redis; the original entry is replayed on a retry, a different payload using the same key is rejected, and only the first request emits the outbox event. Redis availability is required for this write path so the service fails closed rather than risk duplicate business writes.

Time approval governance is now optional and tenant-configurable through the existing time-policy API and Administration screen. Companies can enable approval, choose daily/weekly/monthly cadence metadata, or auto-approve. When approval is required and auto-approval is off, new entries are created as `PENDING`; authorized managers can inspect tenant-scoped pending entries through `GET /api/v1/manager/time-entries/pending` and decide through `POST /api/v1/manager/time-entries/{entryId}/approval`. Approval decisions are stored with actor, timestamp, note, tenant-safe indexes, and independent outbox events. The Manager workspace includes the approval inbox.

Time categories are optional tenant configuration through `GET/POST/PATCH /api/v1/tenant/time-categories`. Existing tenants with no category records continue using `GENERAL`. After a tenant creates its own catalog, only enabled category keys are accepted on new time entries; the Employee Work Hub and Sprint Workspace load the enabled catalog at runtime. Category configuration is tenant-scoped, permission-protected, and indexed for reporting.

Timesheet periods now sit above the same central entries. `GET /api/v1/me/timesheet` lazily creates the current tenant/user period using the configured daily, weekly, or monthly cadence; `POST /api/v1/me/timesheet/{id}/submit` submits it without requiring employees to manually create a separate timesheet. Managers review submitted periods through `GET /api/v1/manager/timesheets/pending` and approve/reject through `POST /api/v1/manager/timesheets/{id}/decision`. An approved period updates its pending/rejected entries, emits a `TIMESHEET_APPROVED` outbox event, and locks new entries for that user's dates until an authorized manager reopens it through `POST /api/v1/manager/timesheets/{id}/reopen`. Rejected periods can be corrected and resubmitted. The `timesheet_period` table is tenant/user scoped, has composite foreign keys and period/status indexes, and stores only workflow metadata; entry totals remain derived from `time_entry` so there is no duplicated hour ledger.

When approval is disabled or auto-approval is enabled, submission moves the period directly to `APPROVED`. This keeps company policy configurable while preserving one consistent employee workflow. Period actions are tenant-safe, transactional, permission-protected, and represented in the outbox for notifications, audit, reporting, or future integrations.

Policy-invalid entries are rejected before Redis idempotency reservation, avoiding temporary idempotency garbage for requests that cannot be written.

## 22. Deferred verification backlog

The following verification work is intentionally retained for the hardening phase and must not be forgotten:

- Browser click-through and responsive mobile viewport verification.
- Frontend unit, component, and end-to-end tests.
- Broader negative authorization coverage for additional roles, resource scopes, and edge-case policy combinations remains part of release hardening.
- Centralized authorization denial tests now cover missing tenant context, missing permissions, exact permissions, and the explicit administrator wildcard. Authentication tests verify suspended and offboarded users cannot create sessions, and the API suite verifies that suspending a user revokes all of that user's tenant-scoped Redis sessions.
- An opt-in local MySQL/Redis API isolation suite now covers Administration, Projects, Work Items, Sprints, Releases, Dependencies, Time Tracking, Notifications, Telegram channel configuration, Reporting, Control Tower, Resource Workload, and Outbox administration. It verifies tenant B cannot read tenant A records, mutate tenant A records, or attach tenant A records through foreign-key relationships. It also verifies a limited employee can use explicitly granted work/time permissions but cannot perform administrative or feature-management actions, and that disabled feature access is denied.
- Tenant-scoped composite foreign-key migration is implemented for roles, invitations, notifications, and project creators; existing local data passed consistency checks. The live API suite passed with four tests and zero failures while using the dedicated `company_os_api_tenant_isolation_test` schema.
- Concurrent update, retry, and idempotency tests.
- Feature-list cache invalidation, bounded TTL, MySQL fallback, and the rule that authorization bypasses the cache are covered by unit tests. Full Redis outage and multi-node invalidation tests remain release-hardening work.
- Outbox retry, consumer isolation, and dead-letter tests.
- Performance/load tests and database query/index verification.
- Data-retention, archival, and log-cleanup tests.
- Browser cookie and CSRF verification over the deployed HTTPS topology.

The current local verification already covers the positive flows for onboarding, authentication, tenant isolation, projects, sprints, work items, time tracking, Work Hub, workload, Control Tower, Administration, Releases, and Dependencies. The deferred backlog is a release-hardening requirement, not a replacement for feature development.

Hardening now includes unit coverage that verifies new tenants receive `report.read`, reporting stops when the `REPORTING` feature is disabled, reporting requires the `report.read` permission, and report ranges longer than twelve months are rejected before storage access.

Redis idempotency hardening also covers same-key concurrent processing, payload reuse with a different hash, and fail-closed behavior when Redis is unavailable.

## 23. Notification and Collaboration status

The first notification vertical slice is complete:

- tenant-safe in-app notification listing;
- unread/read state backed by `read_at`;
- tenant-scoped mark-read endpoint;
- frontend notification center with unread badge and popover;
- outbox-driven notification creation remains the source of truth.

The following remain part of the later notification expansion:

- notification preferences and enabled delivery channels;
- provider abstraction for email, Slack, Teams, WhatsApp, Telegram, and customer webhooks;
- outbox consumer delivery status, retry, and dead-letter administration;
- tenant-safe event and delivery audit screens.

## 24. Reporting and Export status

The first reporting vertical slice is complete:

- tenant-scoped delivery report grouped by project;
- date range validation with a maximum twelve-month window;
- open and completed work counts;
- logged time totals for the selected period;
- active and at-risk release counts;
- JSON endpoint for the Control Tower and CSV export endpoint;
- Control Tower date filters and CSV export action;
- reporting feature entitlement and `report.read` permission checks.

The reporting query uses JDBC behind the reporting store abstraction. It does not expose cross-tenant rows because the tenant context is resolved from the authenticated session and applied to every aggregate query.

The reporting vertical slice now also includes a project delivery forecast read model:

- `GET /api/v1/manager/reports/forecast` returns planned allocation minutes, actual logged minutes, work completion, active sprints, active releases, at-risk releases, blocked dependencies, and a forecast status;
- planned allocation is calculated for the selected period from overlapping active weekly resource allocations;
- actual task time includes both direct project entries and work-item entries resolved through the tenant-scoped work item;
- forecast status is derived as `NO_PLAN`, `ON_TRACK`, `OVER_PLAN`, `AT_RISK`, or `COMPLETE`;
- the aggregation is a single JDBC read model with tenant predicates on every source table and the same reporting entitlement/permission checks as the delivery report.

This is a management signal, not an automatic schedule promise. Future forecasting can add velocity, remaining estimate, release target-date projection, and configurable company policy without changing the current delivery write workflows.

Release readiness is available through `GET /api/v1/manager/reports/release-readiness` and is intentionally a separate read model. It combines linked work completion, open scope, blocked work, target-date distance, and release lifecycle status. It reports `NO_TARGET`, `NO_SCOPE`, `ON_TRACK`, `AT_RISK`, `READY`, `RELEASED`, or `CANCELLED`; it does not change release status automatically.

Forecast thresholds are tenant-configurable through `GET/PUT /api/v1/tenant/forecast-policy` and the Administration screen. The policy is disabled by default and preserves the safe default evaluator until a company enables it. When enabled, a company can configure:

- days before a release target that should trigger risk;
- minimum completion percentage required inside that risk window;
- project actual-effort percentage above plan that should become `OVER_PLAN`.

Writes require `tenant.settings.manage`, are validated server-side, and are stored in `tenant_forecast_policy` with tenant and actor foreign-key protection. Forecast policy changes affect read-model status calculations only; they never mutate projects, sprints, releases, work items, or time entries.

Work items now support an optional `estimateMinutes` value through the create/update APIs. `0` on update clears an estimate. Existing work items remain valid with no estimate. Project and release forecast responses calculate:

- estimated scope and remaining estimated minutes;
- completed-time velocity using the last 28 days divided into four weeks;
- projected remaining days when both remaining estimate and recent velocity exist.

When either input is missing, the UI explicitly reports that a projection is unavailable. It does not infer a deadline from incomplete data.

Historical forecast calibration is available as an opt-in tenant policy. When enabled together with the forecast policy, the reporting layer uses completed, estimated work from the trailing 90 days to compare estimated minutes with actual logged minutes. The factor is tenant- and project-scoped, bounded between `0.25x` and `4.0x`, and only applied when evidence exists. Completion timestamps are captured when a work item first enters `DONE`; reopening the item clears the current completion timestamp so a later completion is measured again. The calibration evidence is available through `GET /api/v1/manager/reports/forecast-calibration`, while forecast and release-readiness rows expose the applied factor and sample size. Sparse history leaves the projection uncalibrated rather than fabricating precision.

The next phase is continued production hardening, followed by concurrency, cache failure, retention, load, and browser/mobile verification. Rate limiting is implemented and has passed a local MySQL/Redis smoke test: repeated onboarding attempts returned `429` with a `Retry-After` header after the configured threshold.

## 25. Telegram notification channel

Telegram is now an optional tenant-configured notification channel:

- `TELEGRAM_NOTIFICATIONS` is a separately enabled tenant feature;
- company admins configure the bot token and destination chat ID from Administration;
- bot tokens are encrypted with AES-GCM using `NOTIFICATION_SECRET_KEY` before MySQL storage;
- API responses never return the bot token;
- the provider sends through the outbox notification flow and uses Telegram's HTTPS Bot API `sendMessage` endpoint;
- disabled or unconfigured tenants do not make external Telegram calls;
- Telegram delivery failures flow through the existing outbox retry/dead-letter path.

Required deployment secret:

```text
NOTIFICATION_SECRET_KEY=<base64-encoded 32-byte AES key>
```

The local configuration contract was verified with a temporary token while delivery remained disabled. An external Telegram message was not sent during local verification.

## 26. Phase 1 demand, readiness, and configurable planning

Demand intake and requirements readiness are now separate tenant-scoped business modules. They are independently feature-gated with `DEMAND_INTAKE` and `REQUIREMENTS_READINESS` and expose:

- `GET/POST /api/v1/demands` for client, internal, idea, and incident demand;
- `POST /api/v1/demands/{id}/decision` for review, approval, rejection, or deferral;
- `GET/POST /api/v1/requirements` for development requirements linked optionally to a demand;
- `POST /api/v1/requirements/{id}/checks` for readiness evidence;
- `POST /api/v1/requirements/{id}/decision` for the development-readiness decision.

The READY gate requires the business, UX, technical, and acceptance contexts plus all required readiness checks. Both modules write tenant-scoped records and transactional outbox events through their persistence ports.

Planning workflows are configurable per tenant and project. `PLANNING_WORKFLOWS` controls the capability, while `workflow.read` and `workflow.manage` control access. A workflow contains a methodology, ordered statuses, terminal markers, and explicit allowed transitions. Projects receive a tenant-local default workflow and can be assigned another active workflow. Work-item status changes consult the assigned project workflow when enabled; tenants without the feature retain the standard status behavior. Workflow creation and assignment are maintenance/configuration operations, not runtime code installation.

Migration `V34__configurable_workflows.sql` adds tenant-safe workflow definitions, statuses, transitions, and project assignment. It seeds a default workflow for existing tenants and onboarding now creates the same default graph for new tenants. All workflow foreign keys include tenant scope, and the migration was verified on local MySQL through the tenant-isolation integration test.

## 27. Phase 1 quality and operations

Quality gates and operations incidents are separate feature modules so release control does not become hidden inside release or work-item code. `QUALITY_GATES` enables quality checks; `OPERATIONS_INCIDENTS` enables production response. Both are tenant-configurable and can be disabled independently.

Quality gates support QA, security, compliance, and approval types with required/optional flags, evidence, explicit pass/fail decisions, and a separate waiver permission. Incidents support S1-S4 severity, owner assignment, investigation/mitigation/resolution lifecycle, mitigation notes, and root-cause capture. The APIs are under `/api/v1/quality/gates` and `/api/v1/quality/incidents`.

Migration `V35__quality_and_operations.sql` stores these records with tenant-first indexes and composite foreign keys to projects, releases, and tenant users. Mutations publish transactional outbox events so notifications, audit consumers, reporting, and future external integrations can react independently.

Basic CSV portability is also available for work items through `GET /api/v1/work-items/export.csv`. It requires the dedicated `work.export` permission, applies the same tenant and feature checks as normal reads, supports the normal project/sprint/assignee/team filters, and emits a bounded UTF-8 CSV response. This is the first migration/export foundation; larger asynchronous import jobs and provider-specific migrations remain hardening work.

## 28. Phase 1 backlog hierarchy and acceptance criteria

Work items now support an optional parent-child hierarchy and explicit acceptance criteria. The capability remains part of the existing work-management feature rather than introducing a separate runtime module:

- create and update APIs accept `parentWorkItemId`, `estimateMinutes`, and `acceptanceCriteria`;
- parent relationships are tenant-safe, indexed for tenant/parent traversal, and cannot point to the same row;
- the service validates that a parent exists in the same project and rejects cyclic or excessively deep hierarchies before writing;
- acceptance criteria are bounded and returned with work-item summaries so the Employee Work Hub and Sprint Workspace can show the same definition of done;
- existing callers and existing work items remain compatible because all new fields are optional.

Migration `V37__work_item_hierarchy_and_acceptance.sql` adds the parent reference, acceptance text, tenant-first hierarchy indexes, and a tenant-safe composite foreign key. Local MySQL migration and tenant-isolation regression passed after the change. Richer board configuration is covered by the V45 Phase 1 slice below; tenant product and project-to-product scope are covered in the following sections.

## 29. Phase 1 tenant product access

The product boundary is now represented explicitly instead of treating every feature entitlement as a product. A tenant can create multiple products, attach enabled feature keys to each product, and grant tenant-local users product membership with a product role such as `MEMBER`, `PRODUCT_ADMIN`, or `VIEWER`.

- `PRODUCT_MANAGEMENT` is an independently enabled tenant feature;
- `GET/POST /api/v1/products` lists and creates tenant products;
- `PUT /api/v1/products/{productId}/features` changes the product's module selection;
- `GET/PUT /api/v1/products/{productId}/members/...` reads and changes tenant-local product access;
- all product, feature, user, and creator relationships use tenant-first predicates and composite foreign keys;
- existing module APIs are not silently changed to require a product membership. Product membership is now available as the explicit access layer; product-to-resource enforcement will be introduced per module after the product model is adopted by that module.

Migration `V38__tenant_product_membership.sql` and the local MySQL tenant-isolation integration test were verified. The frontend Products workspace supports product creation, module selection, and employee access management in responsive web layouts.

Projects can now optionally belong to a tenant product through `productId`. Migration `V39__project_product_scope.sql` adds a nullable tenant-safe project-to-product relationship, preserving existing unscoped projects while allowing product-scoped projects to participate in the company hierarchy. Supplying a product requires the `PRODUCT_MANAGEMENT` entitlement; the relationship is enforced by MySQL and returned in project summaries.

## 30. Phase 1 backlog ordering and portfolio hierarchy

Migration `V40__work_item_backlog_order.sql` adds a tenant/project/Sprint-scoped `backlog_position` with a supporting composite index. New work is appended deterministically, and the authorized `PATCH /api/v1/work-items/{workItemId}/rank` operation reassigns sibling positions inside a transaction while recording audit and outbox events. The Sprint Workspace exposes guarded move-up/move-down actions only for the unfiltered backlog view; filtered status views do not silently reorder the full backlog.

The missing management hierarchy is now a separate `portfolio-management` module. Migration `V41__portfolio_program_initiative.sql` adds tenant-safe portfolios, programs, and initiatives, including optional program/product relationships, status/priority/target-date fields, composite foreign keys, tenant-first indexes, permissions, and the `PORTFOLIO_PLANNING` entitlement. The APIs are:

- `GET/POST /api/v1/portfolios`;
- `GET/POST /api/v1/portfolios/{portfolioId}/programs`;
- `GET/POST /api/v1/portfolios/{portfolioId}/initiatives`.

Creation writes audit and transactional outbox records. The frontend Portfolios workspace exposes the hierarchy in a responsive view. The migration was applied and validated locally; the live tenant-isolation API test remains dependent on the local Redis session service being available.

## 31. Phase 1 migration/import basics

Migration `V42__migration_import_jobs.sql` adds tenant-scoped import jobs and row-level validation errors with bounded progress counters, status, timestamps, composite foreign keys, and tenant-first indexes. `MIGRATION_IMPORT` is independently enabled and `migration.read`/`migration.execute` are separate permissions.

The first import path is `POST /api/v1/migrations/work-items` with a maximum of 500 rows and a required Redis-backed `Idempotency-Key`. It supports dry-run validation, duplicate source-key detection, required/range/enum/length checks, and an all-or-nothing execution mode that delegates each accepted row to the canonical Work Management service. Replays with the same tenant/actor/key and request hash return the original job; a reused key with different content is rejected, and concurrent processing is rejected. Job status is queryable through `GET /api/v1/migrations/jobs/{jobId}`. Job start, validation, rejection, and completion write audit/outbox records. The frontend Imports workspace keeps row entry, validation, execution, and progress visible without database access.

This is the Phase 1 migration foundation, not a promise of arbitrary legacy-system mapping. CSV upload, configurable field mapping, resumable background execution, external-ID reconciliation, and provider-specific migration packs remain explicit hardening work.

Live MySQL verification also protects migration integrity: `row_number` is quoted because it is reserved in MySQL, and the import job has a tenant-first unique key so the `(tenant_id, job_id)` child foreign key is valid and cannot cross tenants.

## 32. Phase 1 integration foundation

Migration `V43__integration_connections_and_events.sql` adds tenant-scoped integration connections and external event records. `INTEGRATIONS` remains independently enabled, connection read/manage and event-ingest permissions are separate, secrets are encrypted with the integration secret key, and API responses expose only whether a secret is configured.

The initial APIs are `GET/POST /api/v1/integrations/connections` and `POST /api/v1/integrations/connections/{connectionId}/events`. Supported connection boundaries are GitHub, GitLab, CI/CD, and generic webhook. CI/CD event types include build/deployment start and completion, environment changes, and rollback. Events retain source system, external ID, repository, branch, environment, timestamp, source URL, and JSON payload. A tenant/connection/event-type/external-ID uniqueness key makes retries acknowledge a duplicate instead of creating another event. Accepted events publish tenant-scoped audit and outbox records.

This establishes the source-of-truth and traceability foundation; provider OAuth installation, repository/work-item linking, provider-specific delivery observability, and full GitHub/GitLab synchronization remain subsequent connector hardening.

The connector boundary now also supports public signed webhook intake at `POST /api/v1/integrations/webhooks/{connectionId}`. Providers send `X-Integration-Timestamp` (Unix seconds), `X-Integration-Signature: sha256=<HMAC-SHA256(secret, timestamp + '.' + rawBody)>`, and the event JSON. The endpoint is CSRF-exempt only for this exact signed path, rate-limited by IP/connection, rejects signatures older than five minutes, and uses the existing tenant/connection/event-type/external-ID uniqueness key for replay deduplication. It never trusts a tenant ID from the request body and does not make outbound calls from provider-supplied URLs.

`integration_connection` also has a tenant-first unique key for the composite `(tenant_id, connection_id)` foreign key used by external events. The authentication credential query aggregates role and permission rows in Java rather than using MySQL `GROUP_CONCAT`, preventing the enterprise permission set from being truncated by the database's group-concatenation limit.

## 34. Phase 1 retention and data lifecycle controls

`RETENTION_MANAGEMENT` is an independently enabled tenant feature. Migration `V46__retention_policy_and_holds.sql` adds:

- tenant policy with protected floors: completed operational activity at least 120 days, notifications/delivery detail at least 30 days, and audit evidence at least 365 days;
- `PREVIEW_ONLY` and explicitly enabled `PURGE` modes, disabled by default;
- bounded cleanup preview and execution APIs under `/api/v1/tenant/retention-policy`;
- legal holds that block all cleanup until an authorized administrator releases them;
- cleanup-run evidence with counts and status;
- tenant lifecycle permissions `retention.read`, `retention.manage`, and `retention.execute`.

The cleanup path removes only eligible in-app notifications, successful outbox delivery detail, orphaned old outbox events, and old tenant audit rows after the protected floor. It does not delete work items, sprint facts, time entries, timesheets, approvals, allocations, dependencies, or other canonical business records. Deletion is limited to a 1,000-row batch per execution so repeated runs are resumable and database load is bounded. Archive-to-object-storage is intentionally not claimed by this Phase 1 path; `PREVIEW_ONLY` is the safe mode until a deployment supplies an archive provider.

## 33. Phase 1 tenant lifecycle and data control

Tenant lifecycle is now an explicit, reversible control surface rather than an implicit database flag:

- `GET /api/v1/tenant/lifecycle` reads the current tenant state;
- `POST /api/v1/tenant/lifecycle/status` supports `ACTIVE`, `SUSPENDED`, and `DELETION_SCHEDULED`;
- suspension revokes every tenant session from the Redis tenant index immediately;
- deletion scheduling creates a 30-day grace window and an auditable lifecycle event while preserving the current admin session so it can be cancelled; this Phase 1 endpoint does not perform irreversible deletion;
- `GET /api/v1/tenant/lifecycle/export` returns a tenant-scoped JSON export with sensitive credential/token/connector-secret fields omitted and a per-table safety bound;
- `tenant.lifecycle.read`, `tenant.lifecycle.manage`, and `tenant.data.export` are separate permissions and are granted to the initial Company Admin role.

Migration `V44__tenant_lifecycle_controls.sql` adds lifecycle timestamps/reason, lifecycle history, permissions, and existing Company Admin grants. The export adapter discovers only tables carrying `tenant_id`, uses parameter binding for tenant values, validates metadata-derived identifiers, and preserves tenant isolation. A production-scale asynchronous export job and irreversible deletion worker remain operational hardening items, requiring backup/legal-hold approval before implementation.

## 35. Phase 1 board configuration and live control-plane verification

Migration `V45__project_board_configuration.sql` adds tenant-scoped project board configuration for grouping, swimlanes, visible card fields, and bounded WIP limits. `PROJECTS` remains the base entitlement and `project.board.manage` protects mutation. Board configuration is stored as project configuration data; it does not install runtime code or bypass the tenant/project authorization boundary.

The local control-plane integration suite verified the combined path against MySQL and Redis: a tenant can save its board configuration, execute and replay a bounded work-item import without creating a second job, receive and deduplicate a signed external event, preview retention with a legal hold, export data without connector secrets, and suspend the tenant so its existing session is denied on the next protected request. The dedicated schema is currently validated through Flyway version 46.
