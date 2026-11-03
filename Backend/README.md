# Company OS Backend

This is the Spring Boot modular-monolith backend for the Company Operating System.

## Requirements

- Java 21
- Maven 3.9+
- MySQL 8+
- Redis 7+

## Modules

- `platform/*` — shared contracts and cross-cutting platform concerns
- `business/*` — independently owned business capabilities
- `adapters/*` — replaceable infrastructure/provider adapters
- `backend-app` — deployable Spring Boot application

The module folders and Maven reactor are established now. The first implemented vertical slice is tenant onboarding: the `tenant-management` business module owns validation and use-case rules, while `persistence-mysql` owns SQL and writes the tenant, tenant-local owner, feature entitlements, audit event, and transactional outbox event together.

## Local configuration

The application reads:

```text
DB_URL=jdbc:mysql://localhost:3306/company_os?createDatabaseIfNotExist=true&serverTimezone=UTC
DB_USERNAME=company_os
DB_PASSWORD=change-me
REDIS_HOST=localhost
REDIS_PORT=6379
```

Flyway is enabled and `ddl-auto=validate` is the default. The first migration creates the tenant, feature entitlement, audit, and outbox foundation tables. The full business schema will be added through forward-only module-owned migrations.

## Verify

The complete testing inventory and one-command execution process are maintained in the repository-level [testing folder](../testing/). Use it to distinguish development-blocking P0 tests from post-development regression and hardening tests. The runner includes backend verification, local MySQL/Redis API tests, frontend component tests, and the frontend production build.

```powershell
mvn -q test
```

Build the reactor once after MySQL and Redis are available, then run the deployable `backend-app` module. Run the Boot goal from that module directory so Maven does not try to launch the parent aggregator project:

```powershell
mvn -q -DskipTests install
Set-Location .\backend-app
mvn -q spring-boot:run
```

If the backend JAR has already been built, you can start it directly from `backend-app`:

```powershell
java -jar .\target\backend-app-0.1.0-SNAPSHOT.jar
```

Start local infrastructure with Docker Compose:

```powershell
docker compose -f docker-compose.yml up -d
```

Run the container-backed integration test when Docker is available:

```powershell
mvn -Pintegration -pl backend-app -am test
```

The default `mvn -q test` excludes tests tagged `integration`; it still compiles them. The integration test verifies Flyway migrations, tenant registration, password login, Redis session creation, CSRF protection, and tenant context resolution.

An API-level tenant-isolation test is available for local MySQL and Redis without Docker. It is opt-in and uses the dedicated `company_os_api_tenant_isolation_test` schema:

```powershell
$env:COMPANYOS_LOCAL_INTEGRATION='true'
mvn -q -pl backend-app -am "-Dtest=LocalTenantIsolationIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The test authenticates two tenants and verifies Tenant B cannot list, read, update, or attach Tenant A records across administration, projects, work items, sprints, releases, dependencies, time tracking, notifications, Telegram configuration, reporting, Control Tower, resource workload, or Outbox administration APIs. It also verifies limited employee permissions, disabled-feature denial, and immediate Redis session invalidation after suspension.

Health endpoint:

```text
GET /actuator/health
```

## First API contract

Create a tenant and its initial tenant-local owner. The owner receives an active local credential during this bootstrap registration flow.

```http
POST /api/v1/onboarding/tenants
Content-Type: application/json
```

```json
{
  "tenantKey": "acme-engineering",
  "legalName": "Acme Engineering LLC",
  "displayName": "Acme",
  "timezone": "Asia/Kolkata",
  "ownerEmail": "owner@example.com",
  "ownerDisplayName": "Rahul",
  "ownerPassword": "StrongPassword1!",
  "enabledFeatures": ["PROJECTS", "SCRUM", "TIME_TRACKING"]
}
```

The onboarding endpoint is intentionally public only for this bootstrap flow. The owner password is stored only as a BCrypt hash. Browser security headers, CSRF handling, and Redis-backed rate limiting are configured in the backend; production hardening still needs email verification/invitation delivery, MFA enforcement, and final HTTPS cookie deployment settings before exposing it to real customers.

Before state-changing browser requests, call `GET /api/v1/auth/csrf`, retain the browser-readable `XSRF-TOKEN` cookie set by that response, and send that cookie value in the `X-XSRF-TOKEN` header. Local login is available at `POST /api/v1/auth/login`; it creates a tenant-bound opaque session in Redis and returns an `HttpOnly` session cookie. `GET /api/v1/me` demonstrates that later requests use the verified session tenant rather than a caller-supplied tenant ID. Local HTTP development defaults `SESSION_COOKIE_SECURE=false`; production HTTPS deployments must set `SESSION_COOKIE_SECURE=true`.

The tenant feature list is cache-aside in Redis with a five-minute TTL and tenant-scoped keys. MySQL remains authoritative: feature authorization checks do not use the list cache, and Redis failures fall back to MySQL for configuration reads. Successful feature changes evict the tenant list cache.

Tenant administration APIs are permission-protected:

```text
GET    /api/v1/tenant/users
GET    /api/v1/tenant/users/roles
POST   /api/v1/tenant/users                 invite a user without creating a password
PUT    /api/v1/tenant/users/{userId}/roles  replace role assignments
PATCH  /api/v1/tenant/users/{userId}/status offboard or suspend a user
POST   /api/v1/auth/activate                consume a one-time invitation
GET    /api/v1/tenant/features
PUT    /api/v1/tenant/features/{featureKey}
```

Invitation tokens are hashed in MySQL and are never written to audit or outbox payloads. Set `EXPOSE_INVITATION_TOKEN=true` only for local development/testing; production delivery will use the notification/outbox consumer and must not return the token from the API.

The first tenant-scoped business module is Projects:

```text
GET  /api/v1/projects
POST /api/v1/projects
```

Project APIs require the `PROJECTS` tenant feature and `project.read`/`project.manage` permissions. Project creation writes the project and `PROJECT_CREATED` outbox event in the same transaction. Notifications are delivered asynchronously into the tenant-local in-app notification center at `GET /api/v1/me/notifications`.

Sprint Management is project-scoped:

```text
GET  /api/v1/projects/{projectId}/sprints
POST /api/v1/projects/{projectId}/sprints
```

Sprint APIs require both `PROJECTS` and `SCRUM` entitlements plus `sprint.read`/`sprint.manage` permissions. The database uses composite tenant/project foreign keys so a sprint cannot be attached to a project belonging to another tenant.

Work Management supports both sprint work and standalone employee work:

```text
GET  /api/v1/work-items?projectId={optional}&sprintId={optional}&assigneeUserId={optional}
POST /api/v1/work-items
```

`WORK_MANAGEMENT` is always required. Project and Sprint entitlements are required only when those relationships are used. Supported initial work types are `TASK`, `BUG`, `STORY`, `DAILY_TASK`, and `SIDE_TASK`; new items begin in `TODO` status and emit a `WORK_ITEM_CREATED` outbox event.

Release Management is project-scoped:

```text
GET  /api/v1/projects/{projectId}/releases
POST /api/v1/projects/{projectId}/releases
POST /api/v1/projects/{projectId}/releases/{releaseId}/work-items/{workItemId}
```

Dependency Risk is work-item-scoped:

```text
GET  /api/v1/work-items/{workItemId}/dependencies
POST /api/v1/work-items/{workItemId}/dependencies
```

Both capabilities enforce tenant-composite foreign keys and emit outbox events for later notifications and reporting.

Time Tracking is a central tenant-scoped service. Employees can record time from the Employee Work Hub, a work item, a project, a sprint, or a future timer UI; every source writes to the same `time_entry` table and emits `TIME_ENTRY_CREATED` in the same transaction:

Tenant time-entry policy is stored separately in `tenant_time_policy` and is optional per tenant. The company can enable it at runtime from Administration. When enabled, the backend centrally applies rounding, weekend-entry rules, and the configured daily limit (unless overtime is allowed) to every time-entry source. When disabled, the central time-entry and automatic-timesheet model continues without these policy restrictions.

```text
GET  /api/v1/time-entries       current user's recent entries
POST /api/v1/time-entries       create a quick-hours/timer/bulk entry; send a tenant-user-scoped Idempotency-Key header
GET  /api/v1/me/work-hub        assigned work items plus recent time entries
```

An entry may reference a work item, or a direct project/sprint context, but not both. The API enforces the tenant and authenticated user from the session rather than accepting them from the request. `TIME_TRACKING` must be enabled for the tenant, and related `PROJECTS`, `SCRUM`, or `WORK_MANAGEMENT` features are checked when used. Initial supported sources are `QUICK_HOURS`, `TIMER`, `START_END`, `BULK`, and `SUGGESTED`; company-specific policies and approvals will be layered on top of this central model.

Manager workload visibility is exposed separately from employee time entry:

```text
GET /api/v1/manager/workload?weekStart=2026-09-14
```

The response includes active tenant members, their assigned open work, logged minutes for the selected Monday-to-Sunday week, recent activity, utilization against a default 40-hour weekly capacity, and a derived status of `AVAILABLE`, `BALANCED`, `AT_RISK`, or `OVERLOADED`. A tenant-specific capacity override is stored in `resource_user_capacity`; the capacity-management permission and future UI can change it without changing work-item or time-entry data. The endpoint requires the `RESOURCE_MANAGEMENT` feature and `resource.workload.read` permission.
