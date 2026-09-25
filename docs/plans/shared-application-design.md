# Shared Application Design Reference

## Purpose and status

This reference preserves the confirmed behavior and technical decisions from the former
shared-backend foundation design. It accompanies the active
[parallel implementation roadmap](parallel-role-implementation.md); it is not a delivery
sequence or an additional backlog. The roadmap defines ownership and integration milestones.
The complete delivery scope includes both roles' services and feature screens.

The core domain and SLICE-001 persistence are implemented on the baseline branch. Later
sections describe planned behavior, not completed features. Existing code is authoritative
for the implemented persistence API, including all three transaction outcomes. Preserve the
schema and data; extend repositories only when a feature needs it.

[Project requirements](../ProjectRequirements.md), [shared rules](../Shared.md),
[Member specification](../MemberSpec.md), and [Exco specification](../ExcoSpec.md)
remain authoritative. Flag behavioral conflicts before implementing affected work.
The decisions below resolve previously unspecified points; remaining unspecified product
behavior still needs clarification before implementation.

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
controllers through a controller factory. Damage-image storage reconciles committed report
references before the context is returned and before routes are composed. Each feature is wired
incrementally; startup does not wait for unimplemented future features. Startup either produces
a fully usable context or shows a fatal startup error; it never continues with a partially
initialized backend.

The default data directory is `${user.home}/.clubstock`. The system property
`clubstock.dataDir` overrides it for tests and controlled deployments. The directory contains:

```text
clubstock.db
damage-evidence/
  .staging/
```

After database initialization and before application routes are composed, startup reads every
committed damage-report image reference in one consistent database read. It then removes
abandoned staged files and unreferenced generated images while preserving referenced images,
including legacy image keys. A failed reference scan or unsafe managed-directory layout stops
startup before any reporting route becomes available.

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
  its contents, finalize the copy under a generated relative key, discard staged or newly
  finalized files, reconcile storage with committed report references, and resolve a stored
  reference for Exco view.
- **Collaborators:** Member Loan workflow, `DamageImageReference`, transaction manager, and
  startup cleanup.
- **State and data:** Accept decoded JPEG or PNG content from 1 byte through 5 MiB. A selected
  source path identifies an external file only for validation and copying and is never
  persisted. Generated names, not original filenames, become storage keys. Database rows store
  relative, non-traversing storage keys only. New files are staged under
  `damage-evidence/.staging/` and finalized under generated keys in `damage-evidence/`.
- **Failure behavior:** Missing, oversized, unsupported, or malformed source files are rejected;
  an absolute source path from a desktop file picker is valid. Absolute or traversing persisted
  storage keys and symlink escapes are rejected. A finalized file is deleted only after rollback
  is confirmed or a committed-reference check confirms that no damage report references it. An
  ambiguous commit outcome retains the finalized file for startup reconciliation. A failure
  never removes an image referenced by a committed report. `COMMITTED` cleanup failures retain
  evidence and must not be presented as a rolled-back submission; reload the committed Loan
  state. Startup preserves referenced legacy images and stops before route composition if the
  reference scan or reconciliation fails.

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
