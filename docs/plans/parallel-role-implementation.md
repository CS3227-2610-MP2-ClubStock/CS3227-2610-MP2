<!-- plan-to-docs-document: parallel-role-implementation -->
# Parallel Member and Exco Implementation Roadmap

| Field | Value |
| --- | --- |
| Status | Approved; replacement issues created |
| Source plan | Finalized 14-issue conversation plan, approved titles without task-code prefixes |
| Document ID | `parallel-role-implementation` |
| Target repository | `CS3227-2610-MP2-ClubStock/CS3227-2610-MP2` |

## Scope and baseline

This is the active delivery plan for all remaining ClubStock implementation through final
acceptance. It replaces the delivery sequence in the removed
`shared-backend-javafx-foundation.md`, except for implemented SLICE-001. It includes backend
services, controllers, FXML screens, integration and packaging for both roles.

The [project overview](../ProposedProjectPlan.md) maps the original phases to this roadmap.
The [shared application design](shared-application-design.md) preserves confirmed technical
and product decisions. Requirements and role specifications remain authoritative.

Baseline inspected on 2026-09-23, from `feature/12-persistence`:

- Core domain classes and lifecycle tests exist.
- SLICE-001 provides SQLite schema version 1, adapters, restoration, transaction management,
  repository ports, ID generation, application errors and persistence tests.
- `TransactionOutcome` distinguishes `COMMITTED`, `CONFIRMED_ROLLBACK` and
  `COMMIT_OUTCOME_UNKNOWN`; preserve all three in subsequent resource compensation.
- JavaFX still displays Hello World. Authentication/session services, workflow services and
  feature screens remain to be implemented. Existing tests were inspected, not rerun for this
  documentation change; this baseline is not a fresh CI certification.

Retain SLICE-001 and existing data. Do not rebuild persistence as a new shared-foundation task.
Keep SQLite, FXML, role-first login, password policy, soft removal, date rules and managed
image evidence as confirmed in the design reference.

## Ownership and collaboration rules

Keith owns Member services and screens; Darryl owns Exco services and screens. Every feature
includes its own controller, FXML, service behavior, focused tests and opposite-role result.

| Area | Keith | Darryl |
| --- | --- | --- |
| Shared startup | Authentication, hashing, session state and authorization helpers | Application startup/composition, navigation, controller factory, shared CSS and packaging |
| Authentication views | Member login | Role selection, Exco setup/login, role shells and logout wiring |
| Accounts and equipment | Member catalogue and safe availability DTOs | Member administration, types/items and shared availability policy |
| Requests | Submission, previews, own requests and cancellation | Pending queue, rejection, approval and allocation |
| Loans | Shared Loan queries and Member Loan screen | Exco Loan screen |
| Reports | Return/loss submission, image store/recovery and Member forms | Pending reports, evidence display and authoritative verification |

Shared ownership means agreed contracts and cross-review; each implementation task has one
owner. Darryl owns composition-root edits and Keith owns session/authorization edits. Feature
authors implement needed repository extensions after coordinating edits to the shared adapter.
Both review changes to domain rules, schema, repository contracts and shared query contracts.

Use small feature branches and integrate completed increments into the shared development
baseline. A branch should contain one owned feature or contract increment, rather than an
entire role's implementation. Rebase or merge the shared baseline regularly. Cross-role
review checks compatibility; it does not require the reviewer's next feature to be complete.

## Contracts that enable parallel development

Publish the minimum Java interfaces and immutable DTOs before their consumers start. Include
method parameters, result fields, errors and authorization expectations in each contract PR.
Agree contracts for the next feature pair as work progresses; do not design every final API
before beginning implementation.

| Contract/component | Owner | Consumer and boundary |
| --- | --- | --- |
| AuthenticationService, SessionManager, password hasher and authorization helpers | Keith | Both UIs and every service; principal carries role and Member ID where applicable |
| Navigator and view/controller registration | Darryl | Both roles; role-protected routes, injected controllers and separate content hosts |
| AvailabilityPolicy | Darryl | Inventory, Member catalogue, request previews and allocation; count eligible items within caller's existing unit of work |
| MemberCatalogService | Keith | Member catalogue; type identity/name and available count, never unassigned item IDs |
| MemberAccountService and InventoryService | Darryl | Exco administration; reuse authorization and protected-reference checks |
| MemberRequestService | Keith | Submit/preview/list own/cancel; request identity, type, quantity, dates, status and approved quantity |
| ExcoRequestService and ApprovalService | Darryl | Pending queue/reject/allocate; include Member, request details and current stock; approval takes explicit item IDs |
| LoanQueryService | Keith | Both Loan screens; own/all unresolved queries, assigned IDs and computed overdue flag |
| MemberLoanService and ManagedDamageImageStore | Keith | Reporting forms and Exco evidence access; commands for good/damaged/lost branches and opaque stored image references |
| VerificationService | Darryl | Exco pending queues and good/damaged/lost outcomes; consumes shared report and image contracts |

Keep the existing UI → controller → service → domain/repository boundary. Split the former
combined catalogue/request service responsibilities by role as shown above. Reuse domain
transitions and shared availability/date policies rather than copying rules into controllers.
Authorization is enforced by services even when UI routing is bypassed. Re-read mutable
preconditions in the same write transaction as their changes; availability helpers must not
start a nested transaction.

Use real temporary SQLite fixtures for development/tests: active accounts, offered inventory,
pending requests, allocated Loans and pending reports. Build valid states through domain
factories/restoration and the existing unit of work. Reuse fixture builders rather than
maintaining separate Member and Exco stores.

Test doubles may support controller development before services exist. Keep seeded data and
doubles in tests or dedicated development harnesses. Production registers only real integrated
features. Refresh affected views after successful commands and on navigation/role entry so
previously displayed state is not treated as current authorization or availability.

## Milestones and individually owned work

Milestones are integration checkpoints, not global barriers. Either person can start a later
feature once its contracts and fixtures exist. A complete producer workflow is needed for
joint acceptance, not for the other person's independent development.

| Milestone | Keith's tasks | Darryl's tasks | Joint acceptance |
| --- | --- | --- | --- |
| A — Usable startup | K-A: hashing, authentication/session services, authorization helpers and Member login | D-A: FXML startup/composition, role selection, Exco setup/login, role shells, navigation, logout and CSS | Fresh Exco setup, subsequent login, fixture Member login, role guards and logout |
| B — Accounts and discovery | K-B: Member catalogue service/screen and zero-stock display | D-B1: Member administration service/screens; D-B2: type/item management service/screens and AvailabilityPolicy | Exco creates credentials and offered inventory; Member logs in and sees accurate counts without item IDs |
| C — Requests and Loans | K-C1: request submission, warning/confirmation, own requests and cancellation; K-C2: shared Loan queries, Member assigned results and Loan screen | D-C1: pending queue and manual rejection; D-C2: explicit selection and atomic allocation; D-C3: Exco Loan screen | Submit → partial approval → independent Loans; exhaustion rejection and cross-role visibility |
| D — Reporting and verification | K-D: good/damaged return and loss forms/services, image storage and startup recovery | D-D: pending-report queries/screens, evidence display and verification services | Each report reaches Exco and resolves only its corresponding Loan/item |
| E — Final acceptance | K-E: Member journeys, ownership restrictions and evidence failure cases | D-E: Exco journeys, allocation failures, packaged verification and Developer Guide completion | Complete workflow, persistence after restart and supported-platform packaging |

### Immediate work and actual dependencies

1. Keith and Darryl agree authentication/principal and navigation contracts together. Keith
   publishes service contracts; Darryl publishes route and controller-injection contracts.
2. K-A and D-A proceed concurrently. Darryl develops screens against authentication doubles;
   Keith tests authentication directly against SQLite. Integrate them when ready, without
   waiting for inventory, approval or verification.
3. D-B1 and D-B2 use real authorization for acceptance. K-B can develop with catalogue
   fixtures while Darryl implements inventory; integrate the shared availability policy
   before accepting catalogue counts. Member account UI is not required for K-A tests.
4. K-C1 and D-C1/D-C2 use agreed request DTOs and seeded pending requests. Exco development
   does not wait for the Member submission UI. K-C2 starts against seeded allocated Loans;
   Darryl uses its contract for D-C3 before the real query service is finished.
5. Keith publishes the report/image-store contract before K-D and D-D. Darryl develops against
   consistent pending-report fixtures while Keith implements submission and evidence storage.
   Joint report acceptance uses the real submission, storage and verification services.
6. Darryl wires each finished feature incrementally. Only registered features must initialize
   at startup; incomplete future features do not become startup prerequisites. Register image
   recovery before exposing reporting routes, and document real startup failures safely.

Within each role, prefer the workflow order above for usable increments. Overlap later work
when contracts permit it; do not replace these checkpoints with a strict K-A → D-A → K-B chain.

## Verification and acceptance

Each feature PR covers the successful operation, wrong role/owner, invalid or repeated/stale
transitions, rollback without partial records, DTO privacy and the other role's observable
result. Test business rules using real SQLite adapters; use pure tests for calculations and
limited JavaFX controller/FXML smoke checks for UI integration.

At milestones A–D, run the completed pair with real services and one shared temporary database.
Switch roles through logout/login because there is only one active session. Fixtures allow
independent development but do not replace these integrated acceptance checks.

Final scenarios include:

- Fresh Exco setup; Exco creates a Member; Member logs in; logout invalidates guarded routes
  and service operations. Inactive/unknown Members cannot authenticate.
- Inventory offering/release and accurate catalogue counts; protected removal of referenced
  Members/items; no unassigned Equipment IDs exposed to Members.
- Warned but valid zero-stock requests, own-request cancellation and manual rejection.
- Partial approval, explicit available-item selection, one Loan per item and automatic
  rejection of other pending requests when allocation exhausts stock. Repeated/stale
  approvals and duplicate allocation must not partially change data.
- Both roles see unresolved Loans; only overdue ON_LOAN records receive an indicator.
- Independent good/damaged returns and loss reports; Exco can disagree with a reported
  return condition, choose damaged availability or confirm loss.
- Invalid evidence, storage failures and database rollback leave valid records. Unknown
  commit outcomes preserve potentially referenced evidence; COMMITTED cleanup failures
  retain evidence and reload committed state. Startup reconciliation preserves referenced
  files and removes abandoned managed files.
- Restart restores the same state; FXML/CSS and SQLite load from the packaged application.

Run focused tests during development. For final acceptance run `./gradlew test`,
`./gradlew build`, `./gradlew javadoc` and `./gradlew shadowJar`. Extend the existing
Windows/macOS/Linux matrix with temporary-directory `--verify-install` checks and a limited
display-backed JavaFX smoke check. Never test against the user's real data directory.

## Requirements, assumptions and design organization

Stated requirements come from ProjectRequirements.md, Shared.md and the two role specs.
Confirmed decisions and the finalized ownership split are retained below. Proposed service
names identify agreed component boundaries, not already implemented public Java APIs.
Exact method signatures/DTO declarations are published by each owner with consumer review.

Repository inspection confirms the implemented persistence baseline and current Hello World
UI. No additional migration, policy, performance target or new business behavior is inferred.
Availability runs in the caller unit of work; completed producer UIs are not prerequisites for
fixture-based development. Milestones A-E organize integration rather than start gates.

The major features below preserve the detailed component responsibilities, interfaces,
collaborators, state and failure behavior. The issue specifications add each role's UI and
verification scope. All planned views consume their service DTOs through injected controllers,
retain only presentation state, refresh after successful actions/on entry and display safe
service errors without changing data on a failed command. Acceptance tooling uses temporary
data, deterministic fixtures and real services for final integration; failures produce a
recorded defect and prevent acceptance rather than altering production state.

## Confirmed decisions

- Persistence uses SQLite rather than a JSON snapshot.
- Authentication and the JavaFX shell are delivered together at milestone A; account
  administration follows in the Exco track.
- Login is role-first. Exco supplies only a password; Members supply a Member ID and password.
- One in-process session exists at a time. Explicit logout returns to role selection; there
  is no remembered login or idle timeout.
- JavaFX views use FXML with thin controllers and a central controller/view factory.
- Exco separately creates, renames, offers, unoffers, and conditionally deletes
  EquipmentTypes.
- Exco enters each physical item's existing case-sensitive Equipment ID.
- Member and EquipmentItem removal is soft: Members become inactive and items become retired,
  while IDs and resolved records remain stored.
- An active Loan query contains `ON_LOAN`, `RETURN_PENDING`, and `LOST_PENDING` Loans.
  Completed Loans remain stored but are excluded from the default active query.
- Plaintext passwords are not trimmed. They require at least eight Unicode code points and at
  least one non-whitespace code point.
- UTC `Instant`s are persisted for timestamps. The system `ZoneId` captured at application
  startup determines the current local date for overdue checks and local display.
- Rejection reasons, cancellation reasons, verification notes, and audit metadata are not
  added.
- Exco password setup is one-time. Password change and recovery are deferred.

## Architecture and Constraints

### Dependency structure

The implementation uses these package-level boundaries:

```text
clubstock.domain                 Existing entities, value objects, and lifecycle rules
clubstock.application            Use-case services, session policy, query DTOs, and errors
clubstock.application.port       Repository, transaction, clock/ID, and image-store ports
clubstock.infrastructure.sqlite SQLite migrations, row mappers, and repository adapters
clubstock.infrastructure.file   Managed damage-image storage
clubstock.ui                     JavaFX application, navigation, controllers, and view models
```

The UI depends on application services and immutable query DTOs. Application services depend
on ports and domain objects. SQLite and file adapters implement the ports. Domain code does
not depend on JavaFX, JDBC, or filesystem APIs.

### Application composition

`ClubStockApplication` creates one application context during JavaFX startup. The context
captures the application `Clock` and system `ZoneId`, opens the SQLite database, runs
migrations, creates repository adapters and the services for integrated features, and supplies
controllers through a controller factory. Image storage and recovery are registered when
reporting is integrated. Each feature is wired incrementally; startup does not wait for
unimplemented future features. Startup either produces a fully usable context or
shows a fatal startup error; it never continues with a partially initialized backend.

The default data directory is `${user.home}/.clubstock`. The system property
`clubstock.dataDir` overrides it for tests and controlled deployments. The directory contains:

```text
clubstock.db
damage-images/
staging/
```

### Stable application errors

Application services throw one application-level exception carrying a stable error code and
a safe display message. Initial codes are:

- `VALIDATION_FAILED`
- `AUTHENTICATION_FAILED`
- `AUTHORIZATION_DENIED`
- `NOT_FOUND`
- `CONFLICT`
- `NO_AVAILABLE_STOCK`
- `PERSISTENCE_FAILURE`
- `IMAGE_STORAGE_FAILURE`

Controllers map these codes to field feedback or dialogs. SQL text, filesystem paths outside
the managed data directory, hashes, and stack traces are never shown as user-facing messages.

### Identifier and ordering policy

- Member IDs and Equipment IDs remain Exco-supplied, trimmed, immutable, and case-sensitive.
- EquipmentType IDs, Request IDs, and Loan IDs are UUID version 4 strings generated through
  an injected `IdGenerator`.
- EquipmentType name uniqueness uses the existing locale-independent folded comparison key.
- Pending requests are ordered by `requestedAt` ascending and then Request ID ascending so
  equal timestamps are deterministic without implying approval priority.

## Implemented Baseline: SQLite Persistence and Transaction Boundary

**Behavior:** Persist every shared entity in one local database and execute repository-wide
invariants through explicit transactions.

**Mapped requirements:** `N1`, `N2`, `F9.3`, plus the persistence needs of `F1`–`F8`.

### Domain restoration APIs

- **Responsibility:** Recreate a previously validated entity without pretending that it is a
  newly submitted request, a newly started Loan, or a new item.
- **Interface:** Retain the implemented static `restore(...)` factories on mutable aggregates.
  Retain read access for persistence-relevant state that is currently internal, including
  EquipmentItem verification-pending state. Retain guarded `deactivate` and `retire` lifecycle
  methods for the confirmed soft-removal policy.
- **Collaborators:** SQLite row mappers and existing typed identifiers/value objects.
- **State and data:** Restoration accepts every persisted field, validates nullability and
  state combinations, and preserves original `Instant`s and `LocalDate`s.
- **Failure behavior:** Impossible rows raise a data-integrity error during loading. They do
  not get normalized, reset, or silently skipped.

Required restoration invariants include:

- an approved request has an approved quantity in `1..requestedQuantity`, while other request
  statuses do not;
- `LOST` equipment is `UNAVAILABLE`;
- verification-pending equipment is `UNAVAILABLE`, not retired, and referenced by an
  unresolved return or loss workflow;
- retired equipment is `UNAVAILABLE` and not verification-pending;
- inactive Members cannot authenticate;
- a Loan's reported return condition is present only for the return branch; and
- report rows match the owning Loan and its lifecycle branch.

### `SchemaMigrator` and `SqliteDatabase`

- **Responsibility:** Open the configured database, apply schema versions in order, and
  supply correctly configured JDBC connections.
- **Interface:** `initialize()`, `read(...)`, and `write(...)` operations; migrations are
  versioned SQL resources and tracked with `PRAGMA user_version`.
- **Collaborators:** Xerial SQLite JDBC, repository adapters, and the composition root.
- **State and data:** Use `org.xerial:sqlite-jdbc:3.53.4.0`, enable foreign keys on every
  connection, set a bounded busy timeout, and begin write transactions immediately.
- **Failure behavior:** A failed migration rolls back. Unsupported future versions, corrupt
  databases, unavailable paths, and native-driver failures stop startup with
  `PERSISTENCE_FAILURE` and never replace existing data.

### Repository ports and SQLite adapters

- **Responsibility:** Load and persist aggregates and execute indexed queries without leaking
  JDBC types into the application layer.
- **Interface:** Repository ports for Exco account, Members, EquipmentTypes, EquipmentItems,
  LoanRequests, Loans, DamageReports, and LossReports; each port operates within the current
  unit of work.
- **Collaborators:** Transaction manager, domain restoration factories, services, and row
  mappers.
- **State and data:** Repositories expose identity lookup, service-specific query operations,
  insert/update operations, and existence/reference checks. They do not expose a general
  mutable collection.
- **Failure behavior:** Constraint failures become `CONFLICT`; unavailable or malformed
  storage becomes `PERSISTENCE_FAILURE`. An adapter never commits independently of the
  service transaction that called it.

The first schema contains:

| Table | Required persisted data and constraints |
| --- | --- |
| `exco_account` | Singleton key constrained to one row; nullable password hash for first-run setup. |
| `members` | Member ID primary key, name, encoded password hash, active flag, optional removal timestamp. |
| `equipment_types` | Generated ID, display name, unique folded name, offered flag. |
| `equipment_items` | Equipment ID, type foreign key, condition, availability, verification-pending flag, retired flag/timestamp. |
| `loan_requests` | Request ID, Member/type foreign keys, quantity, dates, details, requestedAt, status, approved quantity. |
| `loans` | Loan ID, source-request/Member/item foreign keys, startedAt, end date, status, optional reported return condition. |
| `damage_reports` | Loan primary/foreign key, relative image key, format, byte size, description. |
| `loss_reports` | Loan primary/foreign key and description. |

Foreign keys use restrictive deletion. Soft-removed Members/items retain their relationships.
EquipmentType hard deletion is permitted only after it is unoffered and no item, request, or
Loan references it.

### `TransactionManager` and `UnitOfWork`

- **Responsibility:** Make one JDBC transaction the boundary of each state-changing use case.
- **Interface:** Execute a callback with repositories bound to one connection and either
  commit its complete result or roll it back. A failed execution distinguishes
  `CONFIRMED_ROLLBACK`, `COMMIT_OUTCOME_UNKNOWN`, and `COMMITTED` (commit succeeded
  before cleanup failed), as implemented in `TransactionOutcome`. Resource-owning workflows
  must use that outcome for compensation.
- **Collaborators:** Every command service and the SQLite database adapter.
- **State and data:** Read-only queries use read operations; writes acquire their transaction
  before re-reading preconditions so stale UI state cannot authorize a transition.
- **Failure behavior:** Domain, authorization, constraint, or pre-commit I/O failures roll back.
  A commit error is reported as `CONFIRMED_ROLLBACK` only when non-durability is established;
  otherwise it is `COMMIT_OUTCOME_UNKNOWN`. A cleanup failure after successful commit carries
  `COMMITTED`. Services discard local aggregate instances after failure and reload stored
  state; they never infer rollback solely from an exception.

### Interactions and data flow

At startup, the database directory is created, SQLite loads, and migrations run. The singleton
Exco row is seeded only for a fresh database. A missing singleton in an existing database is
an integrity failure; startup must not silently recreate it. A service command starts a write
unit of work, reloads all records used by its preconditions, applies domain operations, persists every affected
record, and commits once. Query services map restored domain data to immutable DTOs before the
unit of work closes.

### Acceptance criteria

- Fresh startup creates schema version 1 and exactly one unconfigured Exco account.
- Repeated startup is idempotent and retains data.
- Every valid domain lifecycle state round-trips through SQLite without timestamp changes.
- Invalid stored state stops loading with a data-integrity error.
- Unique IDs, folded type names, references, and one-report-per-Loan constraints are enforced.
- An injected failure in a multi-record command leaves every table unchanged.

## Authentication, Sessions and Account Administration

**Behavior:** Support first-run Exco setup, normal Exco and Member authentication, one active
session, logout, and backend Member-account management.

**Mapped requirements:** `F1`, `F9.1.9`, `F9.3.1`, and `N1`.

### `Pbkdf2PasswordHasher`

- **Responsibility:** Validate plaintext password policy, create salted password hashes, and
  verify login attempts without retaining plaintext.
- **Interface:** `hash(char[])` and `matches(char[], PasswordHash)`.
- **Collaborators:** Java `SecureRandom`, `PBEKeySpec`, `SecretKeyFactory`, and account services.
- **State and data:** PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt, 32-byte
  result, and encoding `pbkdf2-sha256$600000$<salt-base64>$<hash-base64>`.
- **Failure behavior:** Reject fewer than eight Unicode code points or all-whitespace input.
  Clear input arrays and `PBEKeySpec` contents in `finally` blocks. Malformed stored encodings
  are persistence failures rather than failed-password responses.

### `AuthenticationService` and `SessionManager`

- **Responsibility:** Route first-run setup and login, hold one authenticated principal, and
  clear it on logout.
- **Interface:** Query Exco setup requirement; complete setup with password confirmation;
  authenticate Exco; authenticate Member by Member ID; query current principal; logout.
- **Collaborators:** Exco/Member repositories, password hasher, transaction manager, and
  JavaFX authentication controllers.
- **State and data:** A principal contains `EXCO` or `MEMBER`; Member principals also contain
  the authenticated Member ID. Sessions exist only in memory.
- **Failure behavior:** Wrong ID, wrong password, inactive Member, and absent Member return the
  same `AUTHENTICATION_FAILED` response. Setup is rejected after a hash exists. Logout is
  idempotent.

### `MemberAccountService`

- **Responsibility:** Let Exco list, create, rename, replace credentials for, and remove
  Members while enforcing references and uniqueness.
- **Interface:** Exco-only commands and immutable Member summaries.
- **Collaborators:** Session manager, Member/request/Loan repositories, password hasher, and
  transaction manager.
- **State and data:** Member ID is immutable. Removal sets inactive state and removal time
  only when no `PENDING` request and no `ON_LOAN`, `RETURN_PENDING`, or `LOST_PENDING` Loan
  references the Member.
- **Failure behavior:** Non-Exco calls are denied. Duplicate IDs, invalid fields, invalid
  passwords, and unresolved references fail before mutation. Reusing a deactivated Member ID
  is not allowed.

### Interactions and data flow

Selecting Exco on a fresh installation queries the singleton row and routes to setup. Setup
validates matching password entries, hashes once, and commits the hash. Later Exco login and
Member login verify supplied characters against the stored hash and establish the matching
principal. Services consult the central session at each operation; UI route visibility is not
treated as authorization.

### Acceptance criteria

- No plaintext password is persisted or logged.
- Separate accounts receive different salts for the same password.
- Exco setup can complete exactly once and subsequent Exco login uses that credential.
- Only active Exco-created Members can log in.
- Logout clears the principal and makes authenticated service calls fail.
- Member deactivation obeys unresolved-reference checks and blocks later login.

## Catalogue, Inventory and Request Decisions

**Behavior:** Provide all shared backend operations needed to manage inventory, browse safe
catalogue summaries, submit and decide requests, and atomically allocate items.

**Mapped requirements:** `F2.1`, `F2.2`, `F3`, `F4`, `F5`, and the related `F9` rules.

### `InventoryService`, `MemberCatalogService`, and `AvailabilityPolicy`

Darryl owns `InventoryService` and the shared `AvailabilityPolicy`; Keith owns
`MemberCatalogService`. The policy computes available counts within the caller's unit of work.
The following responsibilities are divided between those services by role.

- **Responsibility:** Manage EquipmentTypes and individual EquipmentItems for Exco, and expose
  Member-safe offered-type availability summaries.
- **Interface:** Exco create/rename/offer/unoffer/delete type; add/release/retire item; list
  inventory. Member list offered types with calculated available counts.
- **Collaborators:** Session manager, equipment repositories, request/Loan reference queries,
  UUID generator, and transaction manager.
- **State and data:** New types are unoffered. New items are `GOOD`/`UNAVAILABLE`; Equipment
  IDs are entered by Exco. Counts include only non-retired `AVAILABLE` items.
- **Failure behavior:** Duplicate IDs/names, wrong roles, release of lost/retired items,
  retirement with an unresolved Loan, and deletion of a referenced type fail without changes.
  Member DTOs never contain Equipment IDs.

### `MemberRequestService` and `ExcoRequestService`

Keith owns submission, own-request queries, previews and cancellation in
`MemberRequestService`. Darryl owns the pending queue and manual rejection in
`ExcoRequestService`. Both use the same repositories and domain transitions.

- **Responsibility:** Submit Member requests, expose own request history, provide the Exco
  pending queue, cancel owner requests, and reject pending requests.
- **Interface:** Member submit/list and cancel-by-Request-ID operations, and Exco list/reject
  operations.
- **Collaborators:** Session manager, Member/type/item/request repositories, UUID generator,
  clock, and transaction manager.
- **State and data:** Submission validates an active Member and offered type, generates a
  Request ID and `requestedAt`, but does not reserve inventory. Exco rows include the opaque
  Request ID, Member ID and display name, EquipmentType ID and display name, requested
  quantity, current calculated availability, requested start and end dates, `requestedAt`, and
  optional details. Member rows include the opaque Request ID, EquipmentType ID and display
  name, requested quantity, requested start and end dates, status, and approved quantity when
  applicable, while excluding unassigned item data.
- **Failure behavior:** Wrong ownership/role, missing or unoffered types, invalid quantity or
  dates, and non-pending transitions fail before mutation. Zero availability produces a
  warning flag in the submission preview but does not invalidate confirmation.

### `ApprovalService`

- **Responsibility:** Approve one pending request by assigning explicit available items and
  applying every resulting change atomically.
- **Interface:** Exco-only approval taking a Request ID and nonempty set of Equipment IDs.
- **Collaborators:** Request, item, Loan repositories; ID generator; clock; transaction
  manager; and domain lifecycle methods.
- **State and data:** Selected IDs are unique, match the requested type, are non-retired and
  `AVAILABLE`, and do not exceed requested quantity. One Loan is generated per item with the
  request end date and current `Instant`.
- **Failure behavior:** Stale selection, no selection, no stock, type mismatch, excess count,
  duplicate allocation, or a non-pending request rolls back. If the committed allocation
  leaves zero available items of the type, every other request for that type that is pending
  in the same transaction becomes `REJECTED`.

### Interactions and data flow

Catalogue queries calculate availability from current item rows. Member request submission
stores only the type and quantity. Approval reloads the request and selected items under one
write transaction, invokes guarded domain transitions, inserts the resulting Loans, then
recounts availability and rejects competing pending requests if it reached zero. The commit
becomes visible to both roles together.

### Acceptance criteria

- Member catalogue queries show offered types and accurate counts without Equipment IDs.
- Exco can manage types/items under the documented lifecycle and removal restrictions.
- Request submission at zero stock remains valid after an explicit warning state.
- Member and Exco queries return only their authorized fields and records.
- Approval creates one independently managed Loan per selected item.
- Partial approval closes the request and does not retain an unfulfilled pending remainder.
- Exhaustion rejection affects only other currently pending requests of the same type.
- Any failed approval leaves the request, items, Loans, and competing requests unchanged.

## Loans, Reports and Verification

**Behavior:** Query active individual Loans, submit one return or loss branch per item, manage
damage evidence, and complete Exco verification atomically.

**Mapped requirements:** `F6`, `F7`, `F8`, and related `F9` restrictions.

### `LoanQueryService`

- **Responsibility:** Expose role-filtered unresolved Loan views and calculate overdue state.
- **Interface:** Member own-active-Loans query and Exco all-active-Loans query.
- **Collaborators:** Session manager, Loan/item/type/Member repositories, clock, and system
  `ZoneId`.
- **State and data:** Rows cover `ON_LOAN`, `RETURN_PENDING`, and `LOST_PENDING`. Member rows
  include only their assigned Equipment IDs. Overdue is true only for `ON_LOAN` after its end
  date and does not alter stored status.
- **Failure behavior:** Unauthenticated or cross-Member access is denied. Missing referenced
  data is reported as a persistence-integrity failure.

### `ManagedDamageImageStore`

- **Responsibility:** Validate and copy damage evidence into application-owned storage.
- **Interface:** Stage a selected source path, whether absolute or relative, validate and copy
  its contents, finalize the copy under a generated relative key, discard abandoned staged
  files, conditionally delete finalized files after transaction failure, and resolve a stored
  reference for Exco view.
- **Collaborators:** Member Loan workflow, `DamageImageReference`, transaction manager, and
  startup cleanup.
- **State and data:** Accept decoded JPEG or PNG content from 1 byte through 5 MiB. A selected
  source path identifies an external file only for validation and copying and is never
  persisted. Generated names, not original filenames, become storage keys. Database rows store
  relative, non-traversing storage keys only.
- **Failure behavior:** Missing, oversized, unsupported, or malformed source files are rejected;
  an absolute source path from a desktop file picker is valid. Absolute or traversing persisted
  storage keys are rejected. A finalized file is deleted only after rollback is confirmed or a
  committed-reference check confirms that no damage report references it. An ambiguous commit
  outcome retains the finalized file for startup reconciliation. A failure never removes an
  image referenced by a committed report. `COMMITTED` cleanup failures retain evidence
  and must not be presented as a rolled-back submission; reload the committed Loan state.

### `MemberLoanService`

- **Responsibility:** Submit good returns, damaged returns, or loss reports for one Loan owned
  by the authenticated Member.
- **Interface:** Three explicit commands rather than a generic status setter.
- **Collaborators:** Session manager, Loan/item/report repositories, image store, and
  transaction manager.
- **State and data:** Good return needs no report; damaged return requires an image and
  description; loss requires a description. Each command changes Loan status and item
  availability together.
- **Failure behavior:** Wrong owner, non-`ON_LOAN` status, repeated branch submission, missing
  evidence, image failure, and database failure with confirmed rollback leave both records
  unchanged. For damaged returns, the file is finalized before database commit. After a commit
  error, cleanup deletes the finalized file only when rollback is confirmed or an independent
  committed-reference query confirms that the report was not committed. If the commit outcome
  or reference check remains ambiguous, cleanup retains the finalized file. At startup,
  recovery removes abandoned staged files and reconciles finalized managed images against
  committed damage-report references, deleting unreferenced finalized files while preserving
  every referenced image.

### `VerificationService`

- **Responsibility:** List pending return/loss work and apply Exco's authoritative outcome.
- **Interface:** Query pending returns/losses; verify good; verify damaged as available or
  unavailable; confirm lost.
- **Collaborators:** Session manager, Loan/item/report repositories, image store, and
  transaction manager.
- **State and data:** Verification completes the matching Loan and updates authoritative item
  condition/availability in the same transaction. The Member report remains advisory.
- **Failure behavior:** Non-Exco access, stale status, wrong verification branch, missing
  report data, or invalid item state rolls back. No reversal or lost-item recovery operation
  is provided.

### Interactions and data flow

Loan queries join the stored typed references into presentation DTOs. A Member return/loss
command reloads and checks both ownership and current state, holds the item, creates applicable
evidence, and commits. Exco later loads the pending queue and completes the Loan plus item
outcome in one transaction. Other Loans created from the same request are never touched. A
confirmed rollback permits immediate deletion of newly finalized evidence; an ambiguous commit
error retains it unless an independent committed-reference query proves that no report row was
committed. On startup, image recovery derives the live finalized-image set from committed damage
reports and removes generated managed files that have no committed reference, including files
left by a process interruption between image finalization and database commit.

### Acceptance criteria

- Both roles see independently managed unresolved Loans with correct Equipment IDs.
- Pending return/loss records remain active but are not marked overdue.
- Good, damaged, and lost submissions enforce their distinct evidence requirements.
- No report changes authoritative item condition before Exco verification.
- Exco may disagree with the reported return condition and choose either damaged availability.
- Confirmed rollback and image failures cannot produce a partial lifecycle transition or broken
  report reference.
- An ambiguous commit outcome never deletes finalized evidence that may be referenced by a
  committed damage report; unresolved files are retained for startup reconciliation.
- Startup recovery removes abandoned staged files and unreferenced finalized managed images
  without deleting an image referenced by a committed damage report.

## JavaFX Authentication and Role Shell

**Behavior:** Replace the demonstration window with a packaged FXML application that performs
real authentication and routes users into isolated role shells.

**Mapped requirements:** `F1.1.2`, `F1.1.4`, `F1.2.3`, `F9.1.9`, `N1.5`, `N2.3`, and
`N2.4`.

### `Launcher`, `ClubStockApplication`, and `ApplicationContext`

- **Responsibility:** Start JavaFX without classpath-launcher issues and construct the integrated
  backend once, adding feature wiring as each implementation lands.
- **Interface:** Normal launch plus a noninteractive `--verify-install` mode used by packaged
  smoke checks.
- **Collaborators:** Database, image store, services, navigator, FXML loader, and primary
  stage.
- **State and data:** The launcher and application move into named packages. The Gradle
  `application.mainClass` is updated accordingly.
- **Failure behavior:** Backend or resource initialization failure shows a startup error and
  exits without presenting a login screen. Verification mode returns nonzero on driver,
  migration, or packaged-resource failure.

### `Navigator` and controller factory

- **Responsibility:** Load FXML, inject controllers, replace the primary content, and enforce
  session-aware routes.
- **Interface:** Routes for role choice, Exco setup, Exco login, Member login, Member home,
  and Exco home; logout always returns to role choice.
- **Collaborators:** FXML resources, authentication service, session manager, and application
  context.
- **State and data:** Member-home routes require a Member principal; Exco-home routes require
  an Exco principal. Back navigation cannot restore an authenticated view after logout.
- **Failure behavior:** Missing/malformed FXML or an unauthorized route is caught centrally,
  reported safely, and never exposes the other role's scene.

### Authentication controllers and role shells

- **Responsibility:** Gather credentials, show validation, invoke authentication, and host the
  role-specific navigation area.
- **Interface:** FXML-bound event handlers and immutable view state. Password confirmation is
  required for initial Exco setup.
- **Collaborators:** Authentication service and navigator only; controllers do not call JDBC
  or repositories.
- **State and data:** Password fields are cleared after every attempt. The initial shells show
  the authenticated role, a home view, a content host for future screens, and logout.
- **Failure behavior:** Invalid credentials use one generic response. Expected service errors
  keep the user on the current screen; unexpected startup/resource errors use the central
  fatal-error path.

### Styling and accessibility baseline

- **Responsibility:** Provide consistent, reusable layout and interaction conventions for
  later feature screens.
- **Interface:** Shared CSS classes and reusable FXML fragments where they remove duplication.
- **Collaborators:** All authentication and shell views.
- **State and data:** Controls have labels, sensible focus order, keyboard activation, visible
  focus, and error text not conveyed by color alone. JavaFX DatePicker later supplies
  locale-aware display while persistence remains ISO.
- **Failure behavior:** Missing styles do not bypass authentication; packaged-resource tests
  detect missing required CSS/FXML before release.

### Interactions and data flow

Startup initializes the application context and shows role selection. Exco selection checks
whether setup is required and routes accordingly. Successful setup/login establishes a
principal before navigating to the matching shell. Logout clears the session first and then
returns to role selection. Future feature controllers will be added inside the role-specific
content hosts without changing this authentication boundary.

### Acceptance criteria

- Fresh launch routes Exco to setup and later launches route Exco to login.
- Member login accepts only active Exco-created accounts.
- Member and Exco shells are separate and reject cross-role navigation.
- Logout clears credentials/session state and prevents back-navigation reuse.
- FXML and CSS load from both Gradle execution and the fat JAR.
- Packaged verification initializes SQLite and resolves all required resources on Windows,
  macOS, and Linux CI runners.

## Boundaries and retained defaults

- One application process and one active session; multi-process coordination is outside scope.
- Completed records remain persisted for references; completed-history screens are not required.
- Removed Member and Equipment IDs remain reserved permanently.
- Date pickers use runtime locale; service DTOs and persistence use `LocalDate`/ISO semantics.
- PBKDF2 parameters are encoded with each hash; future credential migration is outside this delivery.
- Reservations, self-registration, extensions, active-Loan cancellation, additional Exco
  accounts, lost-item recovery, return-report reversal, audit trails and long-term history
  screens remain outside scope.

Verification and delivery ownership are defined in the roadmap. No section in this reference
requires finishing all backend workflows before starting JavaFX feature work.


## Delivery issues

### `SLICE-001` — Implement authentication, sessions and Member login

#### Context

Provide the trusted identity boundary for both roles and a working Member login. Publish authentication and principal contracts early so shell work can proceed concurrently.

#### Delivery ownership

- Backlog number: 01
- Owner: Keith
- Milestone: A
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F1.1` — Authentication/accounts
- `F1.2` — Authentication/accounts
- `F9.1.9` — Authorization/integrity
- `N1` — Shared source of truth
- `N2` — Separation of concerns

#### Scope

- Implement salted PBKDF2 hashing/verification and the retained password policy.
- Implement singleton Exco setup/login, Member login, one in-memory session, logout and service authorization helpers.
- Deliver the Member-login controller/FXML with safe validation and credential clearing.

#### Out of scope

- Exco authentication views and navigation implementation (02).
- Member administration (03), remembered login, recovery and self-registration.

#### Components

- AuthenticationService: setup requirement, one-time setup, Exco/Member authentication and logout.
- SessionManager/principal and authorization helpers: role plus Member identity where applicable.
- Password hasher and Member-login view/controller.

#### Interfaces provided

- AuthenticationService: setup requirement, one-time setup, Exco/Member authentication and logout.
- SessionManager/principal and authorization helpers: role plus Member identity where applicable.
- Password hasher and Member-login view/controller.

#### Interfaces consumed

- Existing account repositories and TransactionManager.
- 02 navigation and controller-injection contract.

#### Acceptance criteria

- [ ] Exco setup succeeds once, with matching password confirmation; subsequent login uses the stored credential.
- [ ] Passwords are not trimmed, contain at least eight Unicode code points and a non-whitespace code point; salted PBKDF2 follows the retained design.
- [ ] Unknown/inactive Members and incorrect credentials receive the same generic failure; no plaintext or hashes appear in UI/logs.
- [ ] Member login shows errors, clears password fields after attempts and routes only to the Member shell.
- [ ] Logout clears the session and subsequent guarded service calls fail.

#### Test scenarios

- Same password produces distinct salts; test Unicode, length, whitespace, confirmation and wrong-password boundaries.
- Test setup repetition, active/inactive/unknown Member login and service access after logout.
- Exercise Member-login feedback and navigation using a double, then real shell integration.

#### Dependencies

##### Start prerequisites

- Completed persistence issue #12.
- 02 navigation interface is needed only for Member-login wiring; service work starts immediately.

##### Fixture or test-double work

- Temporary SQLite account fixtures and a fake navigator.

##### Integration checks before closure

- 01 and 02 use real services for both authentication flows and protected shells.
- A fixture Member suffices; completed Member-administration UI (03) is not required.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.

#### Predecessor issues

[#13](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/13), [#20](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/20)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-001`
- Backlog number: 01

<!-- plan-to-docs:parallel-role-implementation:SLICE-001 -->

### `SLICE-002` — Build the JavaFX shell and Exco authentication flow

#### Context

Replace Hello World with an operational FXML shell early, without waiting for every backend workflow.

#### Delivery ownership

- Backlog number: 02
- Owner: Darryl
- Milestone: A
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F1.1` — Authentication/accounts
- `F1.2.3` — Authentication/accounts
- `F9.1.9` — Authorization/integrity
- `N1.5` — Shared source of truth
- `N2` — Separation of concerns

#### Scope

- Create named launcher/application/context, role selection, Exco setup/login, Member/Exco content hosts and logout wiring.
- Provide central navigation/controller injection, safe error presentation and shared CSS/accessibility basics.
- Wire real features incrementally and verify initial packaged FXML/CSS loading.

#### Out of scope

- Member-login implementation (01), role feature screens, comprehensive packaged verification (14).

#### Components

- ApplicationContext/ClubStockApplication: initialize integrated dependencies once.
- Navigator/controller factory and view registration: role-protected FXML routes.
- Role selection, Exco authentication views, role shells and CSS.

#### Interfaces provided

- ApplicationContext/ClubStockApplication: initialize integrated dependencies once.
- Navigator/controller factory and view registration: role-protected FXML routes.
- Role selection, Exco authentication views, role shells and CSS.

#### Interfaces consumed

- 01 authentication/session/principal contracts.
- Existing SQLite initialization and Gradle launcher configuration.

#### Acceptance criteria

- [ ] Fresh Exco selection opens setup; configured accounts open login.
- [ ] Routes require matching principals; logout prevents direct/back-navigation reuse.
- [ ] Startup errors display safely and stop unusable startup.
- [ ] Controllers never access JDBC/repositories; views have labels, keyboard operation, visible focus and textual errors.
- [ ] Incomplete future features are not startup dependencies; completed features can register incrementally.

#### Test scenarios

- Fresh/repeat launch, invalid credentials and successful login for both roles.
- Attempt wrong-role/direct routes and navigation after logout.
- Load FXML/CSS using production injection and the initial packaged shell.

#### Dependencies

##### Start prerequisites

- Completed #12 for real persistence startup.
- Agree authentication and navigation contracts with 01; its full implementation is not a start blocker.

##### Fixture or test-double work

- Authentication doubles and minimal role content views.

##### Integration checks before closure

- Wire 01 real authentication and Member-login view.
- Verify FXML through the production factory and initial fat-JAR resource loading.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.

#### Predecessor issues

[#20](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/20)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-002`
- Backlog number: 02

<!-- plan-to-docs:parallel-role-implementation:SLICE-002 -->

### `SLICE-003` — Implement Exco Member administration

#### Context

Let Exco create and maintain the accounts Members use while protecting unresolved references.

#### Delivery ownership

- Backlog number: 03
- Owner: Darryl
- Milestone: B
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F1.3` — Authentication/accounts
- `F9.1.9` — Authorization/integrity
- `F9.3.1` — Authorization/integrity
- `N2` — Separation of concerns

#### Scope

- Implement safe Member listing, creation, name editing, password replacement and soft deactivation.
- Deliver list/create/edit/deactivate views with validation and refresh.

#### Out of scope

- Shared authentication implementation (01), self-registration, Member ID editing and audit/history screens.

#### Components

- MemberAccountService and safe Member summaries.
- Member-administration views/controllers.

#### Interfaces provided

- MemberAccountService and safe Member summaries.
- Member-administration views/controllers.

#### Interfaces consumed

- 01 authorization and password hasher.
- Member/request/Loan repositories and TransactionManager; 02 view registration.

#### Acceptance criteria

- [ ] Only Exco manages Members; Member IDs remain immutable and permanently reserved after removal.
- [ ] Duplicate IDs and invalid fields/passwords fail without mutation.
- [ ] PENDING requests and ON_LOAN/RETURN_PENDING/LOST_PENDING Loans block deactivation.
- [ ] UI shows validation/reference failures, refreshes successful changes and never exposes credential hashes.

#### Test scenarios

- Create, rename, replace password and deactivate accounts under Exco and Member principals.
- Exercise each blocking and resolved reference state and duplicate/reused IDs.
- Create through UI, log in as Member, then verify deactivation blocks login.

#### Dependencies

##### Start prerequisites

- Completed #12.
- Stable 01 authorization/hashing and 02 view-registration contracts.

##### Fixture or test-double work

- Seeded Members and pending/unresolved reference fixtures; session/service doubles for controllers.

##### Integration checks before closure

- An account created through this UI logs in through 01; a deactivated account cannot.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.

#### Predecessor issues

[#13](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/13)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-003`
- Backlog number: 03

<!-- plan-to-docs:parallel-role-implementation:SLICE-003 -->

### `SLICE-004` — Implement Exco inventory and shared availability calculation

#### Context

Provide Exco inventory management and one reusable availability calculation for both tracks.

#### Delivery ownership

- Backlog number: 04
- Owner: Darryl
- Milestone: B
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F2.1` — Domain identities and states
- `F2.2` — Domain identities and states
- `F3.2` — Inventory/discovery
- `F9.2` — Authorization/integrity
- `F9.3` — Authorization/integrity
- `N1` — Shared source of truth

#### Scope

- Manage type create/rename/offer/unoffer/protected deletion and item add/release/protected retirement.
- Publish AvailabilityPolicy operating inside the caller unit of work.
- Deliver inventory/type screens with condition, availability and actionable failure feedback.

#### Out of scope

- Member catalogue screen (05), approval (08), verification (12), bulk import and reservations.

#### Components

- InventoryService and Exco inventory DTOs.
- AvailabilityPolicy and Exco inventory/type views.

#### Interfaces provided

- InventoryService and Exco inventory DTOs.
- AvailabilityPolicy and Exco inventory/type views.

#### Interfaces consumed

- 01 authorization, existing equipment/reference repositories, transactions and 02 view registration.

#### Acceptance criteria

- [ ] New types are unoffered; new manually identified items are GOOD/UNAVAILABLE.
- [ ] Fold-equivalent names/duplicate IDs are rejected; protected type deletion and item retirement preserve references.
- [ ] Lost/retired items cannot be released; unresolved items cannot be retired.
- [ ] Counts include only eligible non-retired AVAILABLE items within the existing unit of work.
- [ ] UI exposes inventory state, reports rejected operations and refreshes after success.

#### Test scenarios

- Create/rename/offer/unoffer types and add/release/retire items across valid and invalid states.
- Test uniqueness, unresolved references and wrong-role operations without partial writes.
- Compare real catalogue counts after inventory mutations.

#### Dependencies

##### Start prerequisites

- Completed #12; stable authorization/view contracts.
- Agree availability contract with consumers early; no nested transactions.

##### Fixture or test-double work

- Equipment, request and unresolved-Loan fixtures.

##### Integration checks before closure

- 05 uses the real policy and reflects Exco-created/offered/released stock.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 05: [#27](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/27) — Implement the Member equipment catalogue.

#### Predecessor issues

[#14](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/14)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-004`
- Backlog number: 04

<!-- plan-to-docs:parallel-role-implementation:SLICE-004 -->

### `SLICE-005` — Implement the Member equipment catalogue

#### Context

Let Members discover offered equipment without seeing unassigned physical IDs.

#### Delivery ownership

- Backlog number: 05
- Owner: Keith
- Milestone: B
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F3.1` — Inventory/discovery
- `F3.3` — Inventory/discovery
- `F9.1.1` — Authorization/integrity
- `N1` — Shared source of truth
- `N2` — Separation of concerns

#### Scope

- Implement Member-safe type/count queries and the catalogue screen.
- Display zero-stock types clearly and refresh on re-entry.

#### Out of scope

- Inventory administration (04) and submission-time warning/confirmation (06).

#### Components

- MemberCatalogService and type-ID/name/count DTOs.
- Member catalogue controller/FXML.

#### Interfaces provided

- MemberCatalogService and type-ID/name/count DTOs.
- Member catalogue controller/FXML.

#### Interfaces consumed

- 04 AvailabilityPolicy, 01 authorization, equipment repositories and 02 navigation.

#### Acceptance criteria

- [ ] Only offered types appear, with accurate available counts.
- [ ] Zero-stock types remain visible and clearly marked.
- [ ] Neither DTOs nor UI expose unassigned Equipment IDs.
- [ ] Re-entry refreshes counts; unauthorized, empty and service-error states are handled safely.

#### Test scenarios

- Positive/zero stock, offered/unoffered types and retired/unavailable items.
- Inspect DTO privacy, role enforcement and empty/error rendering.
- Create/release equipment as Exco and verify Member catalogue refresh.

#### Dependencies

##### Start prerequisites

- Completed #12.
- Stable availability, authorization and view-registration contracts.

##### Fixture or test-double work

- Offered/unoffered type fixtures and a stub availability provider.

##### Integration checks before closure

- Use real 04 inventory/policy and verify changes become visible after role switch.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 04: [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) — Implement Exco inventory and shared availability calculation.

#### Predecessor issues

[#14](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/14)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-005`
- Backlog number: 05

<!-- plan-to-docs:parallel-role-implementation:SLICE-005 -->

### `SLICE-006` — Implement Member request submission, tracking and cancellation

#### Context

Let Members request types without reservations, track outcomes and cancel eligible requests.

#### Delivery ownership

- Backlog number: 06
- Owner: Keith
- Milestone: C
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F4.1` — Request behavior
- `F4.2` — Request behavior
- `F4.3` — Request behavior
- `F9.1` — Authorization/integrity
- `F9.3` — Authorization/integrity
- `N2` — Separation of concerns

#### Scope

- Implement submission preview, explicit zero-stock confirmation, submission, own-request queries and owner cancellation.
- Deliver request form and own-request screen including approved quantities.

#### Out of scope

- Exco decisions (07/08), assigned Loan screen (09), reservations and reopening requests.

#### Components

- MemberRequestService, preview and own-request DTOs.
- Request-entry and own-request controllers/FXML.

#### Interfaces provided

- MemberRequestService, preview and own-request DTOs.
- Request-entry and own-request controllers/FXML.

#### Interfaces consumed

- Authorization, 04 availability policy, Member/type/request repositories, clock/IDs and 02 navigation.

#### Acceptance criteria

- [ ] Valid submission creates a generated/timestamped PENDING request without changing inventory.
- [ ] Missing/unoffered types, inactive Members, nonpositive quantity and reversed dates fail.
- [ ] Zero-stock requests remain valid after visible warning/confirmation.
- [ ] Members see only their requests with type, quantity, dates, status and approved quantity when applicable.
- [ ] Only owners cancel PENDING requests; stale/invalid attempts leave state unchanged and UI refreshes after success.

#### Test scenarios

- Positive/zero stock, past/same-day dates, optional blank details and invalid form inputs.
- Owner/non-owner/Exco cancellation and terminal-state rejection.
- Cross-role submission, rejection and partial-approval results with no unassigned IDs.

#### Dependencies

##### Start prerequisites

- Completed #12; stable authorization, availability, request DTO and navigation contracts.
- Completed Exco queue/approval UI is not required to begin.

##### Fixture or test-double work

- Offered types and requests in all states, including partial approval.

##### Integration checks before closure

- Real submissions appear in 07; rejection/08 approval appears in own-request results.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 04: [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) — Implement Exco inventory and shared availability calculation.
- Backlog 07: [#29](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/29) — Implement the Exco pending-request queue and rejection.
- Backlog 08: [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30) — Implement Exco approval and item allocation.

#### Predecessor issues

[#15](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/15), [#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-006`
- Backlog number: 06

<!-- plan-to-docs:parallel-role-implementation:SLICE-006 -->

### `SLICE-007` — Implement the Exco pending-request queue and rejection

#### Context

Expose a deterministic Exco queue and safe rejection independently of Member submission UI development.

#### Delivery ownership

- Backlog number: 07
- Owner: Darryl
- Milestone: C
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F4.4` — Request behavior
- `F5.4` — Request decisions
- `F9.1` — Authorization/integrity
- `F9.3` — Authorization/integrity
- `N2` — Separation of concerns

#### Scope

- Implement pending queue queries, manual rejection and the queue/rejection screen.
- Expose a request-selection contract for approval integration.

#### Out of scope

- Member submission/cancellation (06), allocation (08) and rejection reasons.

#### Components

- ExcoRequestService and pending-request DTOs.
- Queue/rejection controller/FXML and request selection.

#### Interfaces provided

- ExcoRequestService and pending-request DTOs.
- Queue/rejection controller/FXML and request selection.

#### Interfaces consumed

- Authorization, availability policy, request/Member/type repositories and 02 view registration.

#### Acceptance criteria

- [ ] Queue is ordered by requestedAt then Request ID without implying approval priority.
- [ ] Rows include Member, type, quantities, dates, requestedAt, optional details and current availability.
- [ ] Only Exco can query/reject; only PENDING requests can be rejected.
- [ ] Stale/repeated failures preserve state; UI refreshes after rejection and handles already-decided requests.

#### Test scenarios

- Ordering ties, complete DTO fields and recalculated counts.
- Wrong-role, repeated/nonpending rejection and injected rollback.
- Submit as Member, reject as Exco, verify Member outcome.

#### Dependencies

##### Start prerequisites

- Completed #12 and stable shared contracts; 06 completion is not a start blocker.

##### Fixture or test-double work

- Pending requests with tied timestamps, multiple Members/types and different stock levels.

##### Integration checks before closure

- 06 submissions appear in the queue and Exco rejection appears in Member results.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 04: [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) — Implement Exco inventory and shared availability calculation.
- Backlog 06: [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28) — Implement Member request submission, tracking and cancellation.

#### Predecessor issues

[#15](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/15)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-007`
- Backlog number: 07

<!-- plan-to-docs:parallel-role-implementation:SLICE-007 -->

### `SLICE-008` — Implement Exco approval and item allocation

#### Context

Approve a pending request through explicit item selection and one atomic allocation transaction.

#### Delivery ownership

- Backlog number: 08
- Owner: Darryl
- Milestone: C
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F5.1` — Request decisions
- `F5.2` — Request decisions
- `F5.3` — Request decisions
- `F6.1` — Loan behavior
- `F9.2` — Authorization/integrity
- `F9.3` — Authorization/integrity

#### Scope

- Implement ApprovalService, result DTO and item-selection/approval UI.
- Re-read preconditions, allocate selected items, create individual Loans and reject competing requests on exhaustion.

#### Out of scope

- Automatic selection, reservations, waitlists, reopening, remainder requests and Member Loan presentation (09).

#### Components

- ApprovalService and approval result DTO.
- Item-selection controller/view and transactional allocation orchestration.

#### Interfaces provided

- ApprovalService and approval result DTO.
- Item-selection controller/view and transactional allocation orchestration.

#### Interfaces consumed

- Authorization, 04 availability policy, request/item/Loan repositories, clock/IDs and 07 request selection.

#### Acceptance criteria

- [ ] Only Exco approves PENDING requests with a nonempty unique same-type eligible selection within requested quantity.
- [ ] One transaction updates quantity/status, changes each selected item to ON_LOAN and creates one Loan per item.
- [ ] Partial approval closes the request without a remainder; zero availability rejects only other pending same-type requests.
- [ ] Validation/stale-state failures and confirmed rollback cause no partial allocation.
- [ ] UI supports explicit IDs, explains no-stock/conflict errors and refreshes after success.

#### Test scenarios

- Full/partial selection; zero, duplicate, excess, wrong-type, unavailable/retired and stale selection.
- Competing approvals cannot allocate the same item twice.
- Inject failures after request/item/Loan/exhaustion updates; inspect all affected rows.

#### Dependencies

##### Start prerequisites

- Completed #12 and stable authorization/availability/request-selection contracts.
- 06 Member UI completion is not a start blocker.

##### Fixture or test-double work

- Seeded pending requests and valid/invalid available-item selections.

##### Integration checks before closure

- Approve real 06 requests from 07; verify results in 06, 09 and 10.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 04: [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) — Implement Exco inventory and shared availability calculation.
- Backlog 06: [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28) — Implement Member request submission, tracking and cancellation.
- Backlog 07: [#29](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/29) — Implement the Exco pending-request queue and rejection.
- Backlog 09: [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) — Implement shared Loan queries and the Member Loan screen.
- Backlog 10: [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32) — Implement the Exco active-Loan screen.

#### Predecessor issues

[#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-008`
- Backlog number: 08

<!-- plan-to-docs:parallel-role-implementation:SLICE-008 -->

### `SLICE-009` — Implement shared Loan queries and the Member Loan screen

#### Context

Provide shared role-filtered Loan queries and display each assigned item independently for Members.

#### Delivery ownership

- Backlog number: 09
- Owner: Keith
- Milestone: C
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F6.2` — Loan behavior
- `F6.3` — Loan behavior
- `F6.4` — Loan behavior
- `F6.5` — Loan behavior
- `F9.1.1` — Authorization/integrity
- `N1` — Shared source of truth

#### Scope

- Implement own/all unresolved Loan queries, immutable DTOs and overdue calculation.
- Deliver Member Loan view and publish Exco query contract for 10.

#### Out of scope

- Exco screen (10), reporting commands (11), completed-history views, extensions and cancellation.

#### Components

- LoanQueryService and shared role-appropriate DTOs.
- Member Loan controller/FXML.

#### Interfaces provided

- LoanQueryService and shared role-appropriate DTOs.
- Member Loan controller/FXML.

#### Interfaces consumed

- Authorization, Loan/reference repositories, clock/startup timezone and 02 view registration.

#### Acceptance criteria

- [ ] Members see only their unresolved Loans/assigned IDs; Exco query returns all unresolved Loans.
- [ ] Rows include type, item ID, status, start time and end date; completed Loans are excluded.
- [ ] Overdue is true only for ON_LOAN strictly past the end date and never mutates state.
- [ ] Member UI refreshes on entry and handles empty/error results without exposing another Member data.

#### Test scenarios

- Multiple owners/statuses, assigned-ID privacy and unauthenticated/wrong-role access.
- Before/on/after date boundaries with fixed instants and timezones.
- Real partial approval creates independent Member rows and correct Exco query output.

#### Dependencies

##### Start prerequisites

- Completed #12; authorization/Loan DTO/view contracts.
- 08 completion is not required for fixture-based development.

##### Fixture or test-double work

- Allocated, return-pending, loss-pending and completed Loans for multiple Members.

##### Integration checks before closure

- 08 real allocations appear correctly; 10 uses the same production query implementation.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 08: [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30) — Implement Exco approval and item allocation.
- Backlog 10: [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32) — Implement the Exco active-Loan screen.

#### Predecessor issues

[#17](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/17), [#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-009`
- Backlog number: 09

<!-- plan-to-docs:parallel-role-implementation:SLICE-009 -->

### `SLICE-010` — Implement the Exco active-Loan screen

#### Context

Let Exco inspect all unresolved individual Loans using the shared query implementation.

#### Delivery ownership

- Backlog number: 10
- Owner: Darryl
- Milestone: C
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F6.3` — Loan behavior
- `F6.4` — Loan behavior
- `F6.5` — Loan behavior
- `F9.2` — Authorization/integrity
- `N2` — Separation of concerns

#### Scope

- Deliver Exco active-Loan view/controller with Member/item information and overdue indicators.

#### Out of scope

- Duplicated Loan-query logic, reporting/verification commands, active cancellation and end-date editing.

#### Components

- Exco active-Loan controller/FXML.

#### Interfaces provided

- Exco active-Loan controller/FXML.

#### Interfaces consumed

- 09 Exco Loan query/DTO contract and 02 view registration.

#### Acceptance criteria

- [ ] Exco sees all unresolved Loans with Member details, assigned IDs, status and dates.
- [ ] Overdue uses shared query output; pending returns/losses stay visible without overdue indication.
- [ ] No cancellation or end-date editing controls are exposed.
- [ ] Unauthorized access, empty/error states and refresh are handled consistently.

#### Test scenarios

- Render each status and overdue case; verify empty/error feedback.
- Attempt wrong-role navigation/query access.
- Allocate real items and confirm Exco and Member views agree.

#### Dependencies

##### Start prerequisites

- No new whole-issue completion prerequisite beyond the existing baseline.
- Agree 09 query/DTO and 02 view-registration interfaces.

##### Fixture or test-double work

- Query double with multiple Members, statuses and overdue/empty/error results.

##### Integration checks before closure

- Replace doubles with 09 and display real 08 allocations.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 08: [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30) — Implement Exco approval and item allocation.
- Backlog 09: [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) — Implement shared Loan queries and the Member Loan screen.

#### Predecessor issues

[#17](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/17)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-010`
- Backlog number: 10

<!-- plan-to-docs:parallel-role-implementation:SLICE-010 -->

### `SLICE-011` — Implement Member returns, loss reporting and managed evidence

#### Context

Deliver all three Member reporting paths and durable evidence without changing authoritative condition before Exco verification.

#### Delivery ownership

- Backlog number: 11
- Owner: Keith
- Milestone: D
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F7.1` — Member reports
- `F7.2` — Member reports
- `F9.1` — Authorization/integrity
- `F9.3` — Authorization/integrity
- `N1` — Shared source of truth
- `N2` — Separation of concerns

#### Scope

- Implement good return, damaged return and loss commands/forms for individual owned Loans.
- Validate/copy JPEG/PNG evidence and coordinate managed files with report transactions.
- Publish evidence-access contract and implement startup staging/finalized-image reconciliation.

#### Out of scope

- Exco verification (12), external-file persistence, report editing, disputes and recovery transitions.

#### Components

- MemberLoanService and Member report forms/controllers.
- ManagedDamageImageStore and startup recovery.

#### Interfaces provided

- MemberLoanService and Member report forms/controllers.
- ManagedDamageImageStore and startup recovery.

#### Interfaces consumed

- Authorization, Loan/item/report repositories, transaction outcomes, 09 Loan selection and 02 startup hook.

#### Acceptance criteria

- [ ] Only owners report ON_LOAN items; good return needs no report, damage needs image/description and loss needs description.
- [ ] Loan/item/report changes are atomic and affect no sibling Loan; authoritative condition stays unchanged.
- [ ] Forms validate input and present separate loss/return actions with repeat-action errors.
- [ ] Accept valid JPEG/PNG content from 1 byte through 5 MiB; copy to generated relative safe keys, never persist source paths.
- [ ] CONFIRMED_ROLLBACK permits compensation; COMMITTED retains evidence and reloads committed state; unknown outcomes preserve potentially referenced files.
- [ ] Recovery removes abandoned staging/unreferenced managed images while preserving committed references.

#### Test scenarios

- All three branches, wrong owner/status, duplicate submission and sibling independence.
- Absolute/relative source paths, missing/empty/oversized/malformed files and unsafe stored keys.
- Inject stage/finalize/repository/commit/cleanup failures; simulate interruption before commit and run recovery.

#### Dependencies

##### Start prerequisites

- Completed #12; stable authorization, Loan selection, evidence and startup-hook contracts.
- 12 verification implementation is not required to start.

##### Fixture or test-double work

- Owned ON_LOAN and pending fixtures, temporary images and injected storage/transaction failures.

##### Integration checks before closure

- 12 reads real reports/images; Darryl registers recovery before exposing reporting routes.
- Verify committed outcomes and later resolution through real Member/Exco views.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 09: [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) — Implement shared Loan queries and the Member Loan screen.
- Backlog 12: [#34](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/34) — Implement Exco report review and authoritative verification.

#### Predecessor issues

[#18](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/18)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-011`
- Backlog number: 11

<!-- plan-to-docs:parallel-role-implementation:SLICE-011 -->

### `SLICE-012` — Implement Exco report review and authoritative verification

#### Context

Let Exco review advisory reports/evidence and determine authoritative item outcomes atomically.

#### Delivery ownership

- Backlog number: 12
- Owner: Darryl
- Milestone: D
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F8.1` — Exco verification
- `F8.2` — Exco verification
- `F8.3` — Exco verification
- `F9.1` — Authorization/integrity
- `F9.3` — Authorization/integrity
- `N2` — Separation of concerns

#### Scope

- Implement pending-return/loss queries and good/damaged-available/damaged-unavailable/lost verification.
- Deliver pending queues, evidence display and verification controls with refresh.

#### Out of scope

- Member submissions/storage implementation (11), verification notes, disputes, reversal and lost-item recovery.

#### Components

- VerificationService and pending-report DTOs.
- Exco report queues, evidence view and verification controllers.

#### Interfaces provided

- VerificationService and pending-report DTOs.
- Exco report queues, evidence view and verification controllers.

#### Interfaces consumed

- 11 report/evidence-access contracts, shared report models, authorization and transactions.

#### Acceptance criteria

- [ ] Only Exco queries/verifies; UI shows Member/item details, advisory condition and applicable evidence.
- [ ] Good produces COMPLETED and GOOD/AVAILABLE; damaged honors Exco availability choice; loss produces COMPLETED and LOST/UNAVAILABLE.
- [ ] Exco may disagree with reported return condition.
- [ ] Wrong-branch/stale/repeated operations and confirmed rollback leave data unchanged.
- [ ] Successful verification refreshes pending queues and affected views.

#### Test scenarios

- Good reported as damaged and damaged reported as good; both damaged availability choices and confirmed loss.
- Wrong role/status/branch, missing evidence and repeated verification.
- Inject failure between Loan/item writes and complete real cross-role journeys.

#### Dependencies

##### Start prerequisites

- Completed #12 and stable report/evidence/authorization/view contracts.
- 11 completed forms are not a start blocker.

##### Fixture or test-double work

- Valid pending reports plus managed-image access doubles.

##### Integration checks before closure

- Review and resolve actual 11 submissions; 09/10 Loan and inventory views reflect outcomes.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 09: [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) — Implement shared Loan queries and the Member Loan screen.
- Backlog 10: [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32) — Implement the Exco active-Loan screen.
- Backlog 11: [#33](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/33) — Implement Member returns, loss reporting and managed evidence.

#### Predecessor issues

[#19](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/19)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-012`
- Backlog number: 12

<!-- plan-to-docs:parallel-role-implementation:SLICE-012 -->

### `SLICE-013` — Complete Member workflow acceptance

#### Context

Provide repeatable Member acceptance evidence across the integrated services and UI.

#### Delivery ownership

- Backlog number: 13
- Owner: Keith
- Milestone: E
- Existing GitHub labels: enhancement

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F1` — Authentication/accounts
- `F3` — Inventory/discovery
- `F4` — Request behavior
- `F5` — Request decisions
- `F6` — Loan behavior
- `F7` — Member reports
- `F8` — Exco verification
- `F9` — Authorization/integrity
- `N1` — Shared source of truth
- `N2` — Separation of concerns

#### Scope

- Prepare reusable fixtures/scenarios early and execute real Member cross-role journeys.
- Record outcomes and link defects covering privacy, lifecycle and storage failures.

#### Out of scope

- New product features, duplicate feature tests and packaged CI ownership (14).

#### Components

- Member acceptance scenarios/fixtures and result record.

#### Interfaces provided

- Member acceptance scenarios/fixtures and result record.

#### Interfaces consumed

- Integrated authentication, catalogue, requests, Loans, reports and Exco counterpart services/screens.

#### Acceptance criteria

- [ ] Exercise Exco account creation, Member login, request, approval, report and verification through real UI.
- [ ] Cover zero-stock warning, cancellation/rejection, partial approval and independent item handling.
- [ ] Verify cross-Member isolation, assigned-ID privacy, role switching and restart persistence.
- [ ] Exercise evidence/repeated-action/transaction failures and record results with linked defects.
- [ ] Unresolved acceptance failures prevent closure.

#### Test scenarios

- Execute Member journeys against one temporary shared database using real role switches.
- Repeat critical negative/rollback paths with deterministic failure injection.
- Restore persisted state after restart and compare visible outcomes.

#### Dependencies

##### Start prerequisites

- None for preparation.

##### Fixture or test-double work

- Scenario preparation and consistent temporary fixtures while milestones A-D are in progress.

##### Integration checks before closure

- Final execution requires real A-D flows (01-12); fixtures do not replace full workflow checks.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 03: [#25](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/25) — Implement Exco Member administration.
- Backlog 04: [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) — Implement Exco inventory and shared availability calculation.
- Backlog 05: [#27](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/27) — Implement the Member equipment catalogue.
- Backlog 06: [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28) — Implement Member request submission, tracking and cancellation.
- Backlog 07: [#29](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/29) — Implement the Exco pending-request queue and rejection.
- Backlog 08: [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30) — Implement Exco approval and item allocation.
- Backlog 09: [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) — Implement shared Loan queries and the Member Loan screen.
- Backlog 10: [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32) — Implement the Exco active-Loan screen.
- Backlog 11: [#33](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/33) — Implement Member returns, loss reporting and managed evidence.
- Backlog 12: [#34](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/34) — Implement Exco report review and authoritative verification.

#### Predecessor issues

[#13](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/13), [#14](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/14), [#15](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/15), [#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16), [#17](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/17), [#18](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/18), [#19](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/19)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-013`
- Backlog number: 13

<!-- plan-to-docs:parallel-role-implementation:SLICE-013 -->

### `SLICE-014` — Complete Exco acceptance and packaged delivery

#### Context

Finish Exco acceptance and prove the complete application can initialize and load its resources when packaged.

#### Delivery ownership

- Backlog number: 14
- Owner: Darryl
- Milestone: E
- Existing GitHub labels: enhancement, documentation

Owner and milestone are recorded here; GitHub assignees/milestone metadata are not changed by this migration.

#### Requirements

- `F1` — Authentication/accounts
- `F3` — Inventory/discovery
- `F4` — Request behavior
- `F5` — Request decisions
- `F6` — Loan behavior
- `F8` — Exco verification
- `F9` — Authorization/integrity
- `N1` — Shared source of truth
- `N2` — Separation of concerns

#### Scope

- Prepare Exco acceptance, implement packaged --verify-install and extend supported-platform CI.
- Run final checks and update Developer Guide with real startup, storage, recovery and verification commands.

#### Out of scope

- New product features and Member acceptance ownership (13).

#### Components

- Packaged verification entry point, CI checks, Exco acceptance record and Developer Guide.

#### Interfaces provided

- Packaged verification entry point, CI checks, Exco acceptance record and Developer Guide.

#### Interfaces consumed

- Application composition, Exco workflows, Gradle/Shadow packaging and shared fixtures.

#### Acceptance criteria

- [ ] Exercise Exco setup, accounts, inventory, rejection/allocation and every verification outcome through UI.
- [ ] Cover protected removal, unavailable/stale selection and exhaustion rejection.
- [ ] Required test/build/Javadoc/fat-JAR checks pass.
- [ ] Packaged verification loads SQLite and required resources on Windows/macOS/Linux with temporary data directories; include a limited display-backed JavaFX smoke check.
- [ ] Restart preserves state and Developer Guide documents implemented commands/recovery.
- [ ] Record defects/results; unresolved acceptance failures prevent closure.

#### Test scenarios

- Run ./gradlew test, ./gradlew build, ./gradlew javadoc and ./gradlew shadowJar.
- Run packaged --verify-install with empty/existing temporary data and resource/driver failure cases.
- Execute Exco journeys, role switches and limited toolkit/FXML smoke checks on supported environments.

#### Dependencies

##### Start prerequisites

- None for preparation; a runnable 02 application context is required for packaged execution.

##### Fixture or test-double work

- Packaging checks start with the initial runnable shell; expand as views/services land.

##### Integration checks before closure

- Final acceptance requires integrated 01-12 and cross-role result agreement with 13.
- 13 and 14 can execute concurrently; neither waits for the other issue to close.

Numbers 01–14 above refer to this replacement backlog, not GitHub issue numbers. Contracts and working producer increments can land before their issues close; do not treat integration partners as sequential whole-issue start blockers.

#### Related replacement issues

These links resolve the backlog numbers used above. They are contract/integration partners, not automatic whole-issue start blockers.

- Backlog 01: [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) — Implement authentication, sessions and Member login.
- Backlog 02: [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) — Build the JavaFX shell and Exco authentication flow.
- Backlog 03: [#25](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/25) — Implement Exco Member administration.
- Backlog 04: [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) — Implement Exco inventory and shared availability calculation.
- Backlog 05: [#27](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/27) — Implement the Member equipment catalogue.
- Backlog 06: [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28) — Implement Member request submission, tracking and cancellation.
- Backlog 07: [#29](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/29) — Implement the Exco pending-request queue and rejection.
- Backlog 08: [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30) — Implement Exco approval and item allocation.
- Backlog 09: [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) — Implement shared Loan queries and the Member Loan screen.
- Backlog 10: [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32) — Implement the Exco active-Loan screen.
- Backlog 11: [#33](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/33) — Implement Member returns, loss reporting and managed evidence.
- Backlog 12: [#34](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/34) — Implement Exco report review and authoritative verification.
- Backlog 13: [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35) — Complete Member workflow acceptance.

#### Predecessor issues

[#13](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/13), [#14](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/14), [#15](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/15), [#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16), [#17](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/17), [#18](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/18), [#19](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/19), [#20](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/20)

#### Traceability

- Design document: `docs/plans/parallel-role-implementation.md`
- Behavior slice: `SLICE-014`
- Backlog number: 14

<!-- plan-to-docs:parallel-role-implementation:SLICE-014 -->

## Traceability

| Slice ID | Requirements | Major feature / milestone | GitHub issue | State |
| --- | --- | --- | --- | --- |
| `SLICE-001` | F1.1, F1.2, F9.1.9, N1, N2 | 01 / A | [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23) | OPEN |
| `SLICE-002` | F1.1, F1.2.3, F9.1.9, N1.5, N2 | 02 / A | [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24) | OPEN |
| `SLICE-003` | F1.3, F9.1.9, F9.3.1, N2 | 03 / B | [#25](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/25) | OPEN |
| `SLICE-004` | F2.1, F2.2, F3.2, F9.2, F9.3, N1 | 04 / B | [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26) | OPEN |
| `SLICE-005` | F3.1, F3.3, F9.1.1, N1, N2 | 05 / B | [#27](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/27) | OPEN |
| `SLICE-006` | F4.1, F4.2, F4.3, F9.1, F9.3, N2 | 06 / C | [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28) | OPEN |
| `SLICE-007` | F4.4, F5.4, F9.1, F9.3, N2 | 07 / C | [#29](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/29) | OPEN |
| `SLICE-008` | F5.1, F5.2, F5.3, F6.1, F9.2, F9.3 | 08 / C | [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30) | OPEN |
| `SLICE-009` | F6.2, F6.3, F6.4, F6.5, F9.1.1, N1 | 09 / C | [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31) | OPEN |
| `SLICE-010` | F6.3, F6.4, F6.5, F9.2, N2 | 10 / C | [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32) | OPEN |
| `SLICE-011` | F7.1, F7.2, F9.1, F9.3, N1, N2 | 11 / D | [#33](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/33) | OPEN |
| `SLICE-012` | F8.1, F8.2, F8.3, F9.1, F9.3, N2 | 12 / D | [#34](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/34) | OPEN |
| `SLICE-013` | F1, F3, F4, F5, F6, F7, F8, F9, N1, N2 | 13 / E | [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35) | OPEN |
| `SLICE-014` | F1, F3, F4, F5, F6, F8, F9, N1, N2 | 14 / E | [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36) | OPEN |

## Superseded issue migration

Completed #12 (former SLICE-001) and closed core-domain issues were preserved. All 14
replacements were created and recorded before #13-#20 were closed as superseded/not planned.
Predecessor links in each new issue and successor links in each old issue preserve history.
No issue is deleted. This document's slice IDs are scoped by its document marker and must not
be confused with the old foundation's identically numbered slice IDs.

GitHub owners/milestones are recorded in bodies only. Existing `enhancement` applies to all
14 implementation/verification improvements; `documentation` additionally applies to 14.
Proposed role/layer labels are not created. No GitHub assignee, milestone, project, branch,
commit or push is created by this issue migration.

Migration state: Complete. Created #23-#36; closed #13-#20 as NOT_PLANNED.
Remote titles, complete bodies, markers, labels and closure states verified against the local
drafts. No duplicate replacements were skipped and no drafts remain uncreated.
Owner/milestone values are in issue bodies, not GitHub assignee/milestone fields.
The local documentation has not been committed or pushed by this migration.

- Predecessor [#13](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/13): closed as superseded/not planned; successors [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23), [#25](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/25), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#14](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/14): closed as superseded/not planned; successors [#26](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/26), [#27](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/27), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#15](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/15): closed as superseded/not planned; successors [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28), [#29](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/29), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16): closed as superseded/not planned; successors [#28](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/28), [#30](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/30), [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#17](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/17): closed as superseded/not planned; successors [#31](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/31), [#32](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/32), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#18](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/18): closed as superseded/not planned; successors [#33](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/33), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#19](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/19): closed as superseded/not planned; successors [#34](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/34), [#35](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/35), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).

- Predecessor [#20](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/20): closed as superseded/not planned; successors [#23](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/23), [#24](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/24), [#36](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/36).
