# Frontend Architecture and Delivery Plan

**Status:** Frozen frontend planning baseline before React development

This document defines the frontend approach for the multi-tenant Company Operating System. The frontend must reflect the product's feature configuration, tenant isolation, role-based access, Employee Work Hub, project delivery workflow, resource management, time tracking, releases, and Company Control Tower.

The adapted feature research from the existing Sprint Manager reference application is recorded in [Sprint Manager Frontend Reference Review](doc/Sprint%20Manager%20Frontend%20Reference%20Review.md). It informs interaction patterns only; our implementation remains independently designed and built for this product's architecture.

## 1. Frontend objectives

The web application must:

- Give each user a focused experience based on tenant, enabled products/features, role, permissions, and assigned work.
- Make employee input fast: priorities, tasks, timer, quick hours, blockers, and timesheets should require minimal navigation.
- Give managers a clear view of projects, sprint health, people capacity, risks, releases, dependencies, and actual versus planned work.
- Support tenant-specific configuration without requiring a frontend deployment for ordinary feature enablement or policy changes.
- Treat backend authorization as authoritative; frontend visibility is a usability optimization, not a security boundary.
- Provide consistent loading, empty, error, forbidden, stale, and offline-aware states across the product.
- Present a modern, polished, responsive, and visually cohesive experience across the product.

## 1.1 Required product visual direction

The frontend is not intended to be a plain administrative template. The visual system must feel modern, smooth, and premium while remaining suitable for an enterprise work-management product.

Required direction:

- **Feature/module-wise UI structure:** Every major product capability is implemented as an independent frontend feature module with its own routes, views, API hooks, permissions, feature gates, loading states, and tests.
- **Reactive experience:** UI state responds immediately to user actions, background refreshes, notifications, permission changes, and workflow transitions without requiring unnecessary full-page reloads.
- **Responsive design:** Screens must work across desktop, laptop, tablet, and mobile widths. Dense management views may adapt through responsive tables, cards, drawers, and focused mobile workflows rather than simply shrinking desktop layouts.
- **Modern visual language:** Use a consistent design system with strong hierarchy, clean spacing, meaningful typography, polished empty states, clear status indicators, and purposeful visual feedback.
- **Depth and 3D influence:** Use layered surfaces, elevation, perspective, subtle shadows, depth cues, and selective 3D illustrations or objects where they improve orientation or product identity. Operational screens must remain readable and efficient; 3D must not obstruct tables, forms, boards, or accessibility.
- **Gradients:** Use controlled brand gradients for navigation, headers, status emphasis, onboarding, dashboards, and visual landmarks. Gradients must be tokenized so each tenant's branding can be supported safely without reducing contrast.
- **Smooth transitions:** Use short, consistent transitions for navigation, panels, dialogs, filters, status changes, loading states, and feedback. Motion must communicate cause and effect rather than decorate every interaction.
- **Personalized visual configuration:** Theme tokens, density, light/dark mode, tenant branding, and feature-specific visual accents must be configurable without duplicating feature components.

Visual quality must not override usability. The design system must support reduced motion, keyboard navigation, visible focus, sufficient contrast, readable text, and predictable interaction behavior. Expensive blur, animation, shadow, gradient, and 3D effects must be measured and disabled or simplified on constrained devices.

Frontend acceptance criteria for each feature:

1. The feature is isolated under its own module boundary.
2. The feature works across the approved responsive breakpoints.
3. Loading, success, empty, error, forbidden, and disabled-feature states have designed UI.
4. Important state changes have clear visual feedback and smooth but restrained transitions.
5. The feature uses shared design tokens and components rather than introducing unrelated styles.
6. Keyboard, reduced-motion, contrast, and screen-reader behavior are verified.
7. Visual effects do not materially degrade page performance or data readability.

## 2. Default frontend technology direction

The initial frontend is a responsive React web application written in TypeScript.

Recommended baseline:

- React for component-based UI.
- Vite for the client application build and development server.
- React Router for authenticated, tenant-aware navigation.
- TanStack Query for server state, request caching, invalidation, mutation state, and refetching.
- A small dedicated client-state store only for UI/session presentation state; server data must not be copied into an uncontrolled global store.
- Schema-based request and response validation at API boundaries.
- A shared accessible design system with tokens for color, spacing, typography, focus, density, tables, forms, dialogs, menus, notifications, and responsive layouts.

The exact package versions will be pinned in the frontend build when the project is scaffolded. React's current documentation supports TypeScript and component-based composition; Vite provides the build/development layer, while React Router and TanStack Query provide routing and server-state primitives. See the official references at the end of this document.

### Approved experience decisions

- The first client is a responsive web application. A separate native mobile application may be planned later, but the web product must already work well on mobile browsers.
- Light and dark themes are supported from the beginning.
- Tenant branding is supported through controlled theme tokens, including logo, brand colors, and permitted visual accents.
- 3D effects are used selectively in onboarding, dashboards, illustrations, empty states, and product identity. Work-heavy screens remain clear and efficient.
- The initial product language is English, but text, dates, numbers, time zones, and formatting must be localization-ready.
- Navigation is role-, permission-, and feature-aware. Users see only relevant enabled product areas and actions.
- Every feature receives a deliberate desktop, tablet, and mobile layout. Mobile behavior may use cards, drawers, tabs, bottom actions, responsive forms, and focused views instead of shrinking desktop tables.
- Desktop and tablet use the feature-aware sidebar as the primary navigation. Mobile uses a compact primary navigation containing Home, My Work, Notifications, and More, with feature navigation available through the More/drawer pattern.

### Approved baseline color system

The initial product identity uses a calm enterprise navy/indigo foundation with one controlled blue-to-cyan gradient. It is intentionally distinct from the Sprint Manager reference UI and avoids using decorative gradients across dense operational screens.

| Token | Light theme | Dark theme | Intended use |
|---|---|---|---|
| `brand-primary` | `#2563EB` | `#60A5FA` | Primary actions, selected navigation, links, focus accents |
| `brand-secondary` | `#06B6D4` | `#22D3EE` | Secondary highlights, data accents, visual landmarks |
| `brand-gradient` | `linear-gradient(135deg, #2563EB 0%, #4F46E5 48%, #06B6D4 100%)` | Same gradient with controlled contrast | Hero/onboarding areas, selected navigation accents, primary visual landmarks, selected dashboard summaries |
| `surface-base` | `#F6F8FC` | `#0B1020` | Application background |
| `surface-raised` | `#FFFFFF` | `#121A2E` | Cards, panels, dialogs, drawers |
| `surface-subtle` | `#EEF2F7` | `#18233D` | Hover states, grouped sections, table headers |
| `text-primary` | `#152033` | `#F1F5F9` | Main text |
| `text-secondary` | `#526176` | `#A8B4C8` | Supporting text |
| `border` | `#DCE3EE` | `#293754` | Boundaries and dividers |
| `success` | `#15803D` | `#4ADE80` | Success/healthy state |
| `warning` | `#B45309` | `#FBBF24` | Attention/risk state |
| `danger` | `#DC2626` | `#FB7185` | Error/critical state |

Usage rules:

- Gradients are reserved for brand moments and selected/high-level surfaces; normal cards, tables, forms, boards, and work-item details use solid accessible surfaces.
- Status is communicated through text, icon, label, and color together; color alone never conveys workload, risk, or permission state.
- Tenant branding maps to controlled brand tokens after contrast validation. It cannot arbitrarily override success, warning, danger, focus, or accessibility tokens.
- Light and dark themes share the same semantic token names so feature modules do not hard-code colors.

## 3. Product information architecture

### Public and account area

- Registration and company creation.
- Invitation acceptance.
- Sign in, sign out, password reset, email verification, MFA, and session/security settings.
- Tenant selection only where the authenticated account has more than one explicitly assigned tenant; ordinary users remain tenant-local.

### Employee Work Hub

- **Home:** concise role-aware summary of current priority, active timer/continue-work action, today summary, alerts, blockers, and a small relevant-work preview.
- **My Work:** the dedicated employee task workspace for the full assigned queue across Sprint, project, support, operational, and approved side work.
- **My Work tabs:** Today, Assigned to Me, Upcoming, Blockers, and Time/Timesheet when the relevant feature is enabled.
- Start/stop timer and quick hours.
- Notifications and approvals.

Each task card opens a unified **Update Work** panel rather than sending the employee through separate Sprint, Project, Task, and Timesheet pages. It pre-fills the task context and provides work description, quick hours or timer, permitted status/progress update, time category, date, and blocker controls. One save updates the canonical work/time record and refreshes the Employee Hub; related Sprint, project, timesheet, capacity, and reporting screens display the derived result.

Home is intentionally not a second full task-management screen. It offers a short preview and quick resume of the most important work; My Work owns searching, filtering, ordering, bulk review, and full work-queue navigation. The same task detail and Update Work panel are reused from Home, My Work, Sprint, Project, and notification links.

### My Team workspace

`My Team` is the manager-level equivalent of `My Work`. It is visible only to authorized manager roles and defaults to the manager's permitted teams and the current week/current Sprint. It is not the same as the company-wide Control Tower: My Team is for day-to-day people, workload, and delivery decisions within the manager's actual scope.

My Team contains four connected tabs:

- **Overview:** team health, available capacity, planned versus actual effort, overloaded/under-utilized members, unplanned work, blockers, overdue work, and at-risk delivery indicators.
- **Workload:** one row per authorized team member and a day/week/Sprint/month capacity timeline. It compares availability, allocated/committed work, current remaining work, actual logged effort, and forecast. Color indicators identify attention, but are never the only signal.
- **Work:** filterable work list across the manager's permitted projects/Sprints. It shows assignee, priority, status, estimate/remaining/actual effort, due date, Sprint/project, blocker, and whether work was committed or unplanned.
- **Activity:** a chronological feed of explicit work records: assignment, status/progress updates, employee work summaries, recorded time, blockers, approvals, reassignments, and workflow events.

Selecting a member opens a reusable detail drawer/page with `Workload`, `Current Work`, `Activity`, and `Capacity/Allocation` sections. Managers can reassign, reprioritize, adjust allocation, approve, or escalate only when their backend permissions allow it.

There is no standalone Allocation tab. Capacity assistance appears in the manager's natural decisions:

- **My Team:** member cards show current load, free/forecast availability, active work, blockers, and a detail drill-down.
- **Sprint planning:** adding or assigning a work item shows candidate capacity and Sprint impact before commitment.
- **Work-item assignment/reassignment:** the assignee picker shows a concise availability card for each eligible person, with a link to supporting work/capacity detail.
- **Project planning and demand intake:** planners see team capacity, skill match, allocation impact, and likely delivery impact before confirming a plan.

An availability card is decision support, not an automated decision. It explains the basis of its signal—calendar/leave, allocation, committed/current/unplanned work, remaining estimate, actual logged effort, blockers, and forecast date—and allows a manager to open the relevant detail before assigning work.

Privacy and scope rules:

- My Team only returns people and records in the manager's verified tenant/team/project/resource-management scope.
- It shows explicit work records, not keyboard activity, screen tracking, hidden online status, or inferred productivity scores.
- A lack of recent recorded activity is not presented as proof that an employee is inactive.
- Work summaries and time notes follow the underlying sensitive-data/time-policy permissions.

### Automation principles

The product must reduce repetitive work without making silent people or delivery decisions.

**Automatic when deterministic and low-risk:** prefill task context, generate timesheets, roll up central time entries, refresh capacity/risk/forecast values, create activity/audit events, update notifications, identify possible duplicate/overlapping time, and remind owners about missing time, pending approvals, blockers, or approaching due dates.

**Suggest, then let an authorized person decide:** suitable assignees, next ready work, workload rebalancing, missing task information, likely Sprint risk, and likely capacity conflict. Suggestions always show their reason and impact.

**Require explicit confirmation or configured approval:** reassignment, priority change, status completion, active-Sprint scope change, time approval/correction, allocation change, release/date commitment, and customer-visible communication.

Automation rules are tenant-configurable and feature-gated. The UI must display what rule acted, why it acted, what changed, and how an authorized user can review or correct it.

Responsive behavior:

- **Desktop:** roster plus workload timeline, with member detail in a side drawer or full page.
- **Tablet:** collapsible roster and horizontal timeline with a filter drawer.
- **Mobile:** member cards with workload indicators; a selected member opens a focused detail page with internal tabs. Large timeline matrices are replaced by date-range summaries and drill-down views.

### Delivery workspace

- Projects and project overview.
- Requirements, backlog, and work items.
- Teams and team capacity.
- Sprint planning, board, daily progress, review, and retrospective information.
- Dependencies, risks, blockers, decisions, and change history.

### Workforce and time

- People directory and organization hierarchy.
- Skills, availability, allocation, and capacity.
- Time entries, corrections, timesheets, policies, approvals, and actual-versus-planned views.

### Releases and control

- Release planning and readiness.
- Deployment/integration status.
- Company Control Tower for management.
- Reporting, forecast, utilization, risk, dependency, and variance views.

### Administration

- Tenant profile and settings.
- Products and feature entitlements.
- Users, invitations, offboarding, teams, roles, permissions, and access reviews.
- Time policies, approval rules, notification preferences, integrations, audit, and operational settings.

Disabled features must not appear in normal navigation, deep links must show a controlled unavailable/disabled state, and the backend must still enforce the entitlement and authorization decision.

## 4. Frontend module structure

The frontend will be organized by product capability rather than by a single global `components` folder.

```text
src/
  app/                 application bootstrap, providers, routing
  platform/            API client, auth, tenant context, permissions, feature gates
  design-system/       reusable accessible UI primitives and tokens
  shared/               cross-feature utilities, formatting, errors, tables, forms
  features/
    onboarding/
    employee-hub/
    organization/
    products-entitlements/
    projects/
    requirements-work/
    sprints/
    resources/
    time-tracking/
    releases/
    control-tower/
    notifications/
    integrations/
    audit/
  pages/               route composition only; business logic stays in features
  test-support/
```

Each feature should keep its route definitions, API hooks, query keys, schemas, permissions, components, forms, and tests together. Features may consume shared contracts but must not import another feature's private implementation.

## 5. Tenant, identity, entitlement, and authorization behavior

The frontend initializes a verified session context from the backend:

```text
Authenticated session
    -> current tenant
    -> user profile
    -> roles and permission summary
    -> enabled features/products
    -> policy/configuration summary
```

All tenant-aware API calls use the backend-approved tenant context. A route parameter or UI selection must never be treated as proof of tenant access.

The frontend uses three separate checks:

1. **Authentication:** Is the user signed in?
2. **Feature entitlement:** Is this product/module enabled for the tenant?
3. **Authorization:** Is this user allowed to perform this action on this resource?

The UI may hide or disable controls based on the permission summary, but every mutation and protected read is validated again by the backend. Permission or feature changes must invalidate the relevant client cache and refresh navigation/context.

## 6. Data and API interaction model

- Keep API calls in typed feature API modules, not directly inside presentational components.
- Use stable query keys containing tenant and relevant resource identifiers.
- Invalidate or update affected queries after successful mutations.
- Treat backend responses as the source of truth; do not assume optimistic updates succeeded.
- Use optimistic updates only for safe, reversible interactions with rollback behavior.
- Support cursor/page pagination, filtering, sorting, and server-side search for large collections.
- Use route loaders or equivalent prefetching only where it materially improves navigation; avoid loading the whole tenant into the browser.
- Use a common error envelope and map validation, authorization, conflict, rate-limit, and server errors to appropriate UI states.

## 7. Security requirements

- Do not place long-lived access tokens or secrets in ordinary browser storage.
- Follow the backend session/token contract and use secure cookie or equivalent protected transport when defined by that contract.
- Never trust frontend permission checks, hidden routes, disabled buttons, or tenant IDs.
- Redact secrets and sensitive personal data from client logs, telemetry, URLs, and error messages.
- Clear tenant-bound query caches on logout, tenant change, session revocation, or offboarding.
- Handle expired/revoked sessions centrally and prevent failed requests from causing redirect loops.
- Use CSRF protection where cookie-based authentication is used.
- Apply safe file upload validation, download authorization, and content-disposition handling.

## 8. UX states and accessibility

Every important screen must define:

- Initial loading and skeleton behavior.
- Empty state with a useful next action.
- Validation and conflict errors.
- Permission denied state.
- Feature disabled state.
- Stale/background-refresh state.
- Retry behavior for recoverable failures.
- Destructive-action confirmation and undo where safe.

The design system will target keyboard navigation, visible focus, semantic controls, screen-reader labels, sufficient contrast, responsive layouts, reduced motion, and accessible tables/forms/dialogs. Accessibility is tested during component and page development, not only before release.

## 9. Notifications, real-time updates, and cache behavior

The frontend consumes notification and event results through backend APIs. It must not connect directly to Redis or provider systems.

Initial behavior:

- In-app notification center and unread counts.
- Email status represented where relevant.
- Polling or server-sent updates only for screens that need near-real-time changes.
- Query invalidation after known mutation/event updates.
- Tenant-bound cache keys and cache clearing on context changes.

WebSocket/SSE support remains an adapter decision. The product must remain correct when real-time delivery is delayed; the backend remains authoritative.

## 10. Performance and scale expectations

- Lazy-load large feature areas and administration screens.
- Virtualize very large tables/boards where measurement justifies it.
- Avoid loading dashboard data through many independent sequential requests; provide aggregated backend queries where appropriate.
- Debounce search and filter input.
- Preserve URL state for shareable/filterable views where safe.
- Measure page load, API latency, render cost, query cache behavior, and error rates.
- Do not expose tenant-wide datasets merely for client-side filtering.

## 11. Testing strategy

- Unit tests for permissions, feature gates, formatting, reducers/stores, validation, and business-independent utilities.
- Component tests for forms, tables, dialogs, loading/error/empty states, and accessibility behavior.
- API contract tests for request/response schemas and error envelopes.
- Integration tests for authentication, tenant switching, feature enablement, offboarding, and cache invalidation.
- End-to-end tests for the critical onboarding and Employee Work Hub flows.
- Security tests proving disabled features and unauthorized routes/actions cannot be used through the UI or direct API calls.
- Visual regression tests for the design system and high-value pages when the UI stabilizes.

## 12. Frontend delivery sequence

1. Confirm information architecture and navigation for each major persona.
2. Define design tokens and accessible component primitives.
3. Define API contract conventions and shared TypeScript models.
4. Scaffold React/TypeScript application and platform providers.
5. Implement authentication, tenant context, permissions, feature gates, and session failure handling.
6. Build the onboarding vertical slice with the backend:
   `registration -> tenant -> admin -> invitation -> login -> access -> offboarding`.
7. Build Employee Work Hub and notification foundation.
8. Build Projects, Requirements, Teams, Sprints, and Work Items.
9. Build Workforce, Time Tracking, Releases, Reporting, Integrations, and Administration by approved product phases.

The first-use registration screen is implemented at the unauthenticated entry point. It calls the tenant onboarding API, creates the registering user as the tenant-local Company Admin, signs the user in through the normal CSRF/session flow, and then opens the tenant workspace. Demo mode intentionally bypasses registration and uses its fixed demo user only.

## 13. Open implementation decisions

These are implementation choices, not open product/UI design discussions:

- Final component/design-system library versus internal primitives.
- Exact auth transport and refresh behavior according to the backend security contract.
- Whether near-real-time screens use polling, SSE, or WebSocket after measured need.
- Exact API code-generation or hand-written typed-client approach.
- Final browser support matrix and exact mobile-responsive breakpoints.

## 14. Planning freeze

The frontend product planning is frozen. It includes the feature/module structure, application shell, role/feature-aware navigation, responsive web requirement, visual direction, Employee Home and My Work model, My Team workspace, contextual capacity assistance, automation principles, Sprint/Project/Work Item interaction direction, security boundaries, and test expectations.

Future work proceeds to implementation blueprints and development. This document is changed only when a newly approved product requirement requires a real change; implementation details must conform to the frozen baseline rather than reopening UI decisions.

## References

- [React official documentation](https://react.dev/learn)
- [React TypeScript setup guidance](https://react.dev/learn/setup)
- [Vite official guide](https://vite.dev/guide/)
- [React Router official quick start](https://reactrouter.com/tutorials/quickstart)
- [TanStack Query React documentation](https://tanstack.com/query/latest/docs/framework/react/reference/index)
