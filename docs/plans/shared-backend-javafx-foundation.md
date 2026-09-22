<!-- plan-to-docs-document: shared-backend-javafx-foundation -->
# Shared Backend and JavaFX Foundation Engineering Design

| Field | Value |
| --- | --- |
| Status | Approved |
| Source plan | `docs/ProposedProjectPlan.md`, phases 2–3, with the confirmed decisions recorded below |
| Document ID | `shared-backend-javafx-foundation` |
| Target repository | `CS3227-2610-MP2-ClubStock/CS3227-2610-MP2` |

## Summary and Goals

This design establishes ClubStock's shared persistence and application-service foundation,
then replaces the JavaFX demonstration window with a working role-first authentication flow
and separate Member and Exco application shells. It is intended for the engineers who will
later add the role-specific feature screens.

The design assumes the approved core-domain design in
`docs/plans/core-domain-model.md` and its account, equipment, request, Loan, and report
classes are available. It implements the repository-wide and multi-entity rules that the
core-domain design deliberately deferred.

The implementation succeeds when:

- all shared business data survives an application restart in one SQLite database;
- each multi-record workflow either commits completely or leaves both memory and storage
  unchanged;
- Member and Exco operations use one source of truth and enforce role and ownership rules at
  the service boundary;
- the singleton Exco account supports first-run setup and subsequent login, Members can log
  in with Exco-created credentials, and logout clears the active session;
- damage evidence is copied into application-managed storage and cannot leave a committed
  report pointing to a missing file;
- the JavaFX shell loads FXML through injected controllers and cannot route one role into the
  other role's area; and
- tests exercise SQLite restoration, authorization, workflow atomicity, FXML loading, and
  the packaged application on the supported build matrix.

This design does not add the Member or Exco workflow screens. It provides their backend
operations, query models, authentication screens, role shells, and navigation extension
points. Reservations, Loan extensions, active-Loan cancellation, additional Exco accounts,
recovery of lost items, return-report reversal, audit trails, and long-term history screens
remain outside scope.

## Requirements and Repository Grounding

### Stated requirements

The authoritative behavior comes from `docs/ProjectRequirements.md`, `docs/Shared.md`,
`docs/MemberSpec.md`, and `docs/ExcoSpec.md`. This design particularly implements:

- `N1` and `N2`: one shared source of truth with UI-to-controller-to-service-to-domain or
  repository dependency direction;
- `F1`: Exco first-run setup, Member accounts, authentication, and protected removal;
- `F3`: shared catalogue quantities and individual inventory management;
- `F4` and `F5`: request submission, queries, cancellation, rejection, approval, allocation,
  and exhaustion rejection;
- `F6`: individual unresolved Loan queries and non-mutating overdue calculation;
- `F7` and `F8`: Member return/loss submission and authoritative Exco verification; and
- `F9`: authorization and cross-record invariants.

`docs/ProjectRequirements.md` states that its unspecified points must not be resolved by
assumption. The material points needed by this design were therefore confirmed before this
document was created and are listed under Confirmed decisions.

### Confirmed decisions

- Persistence uses SQLite rather than a JSON snapshot.
- Phase 2 implements all shared workflows, not only repository infrastructure.
- Working authentication is pulled forward from phase 4 so the phase-3 JavaFX shell is
  operational. Later phase-4 work adds account-management screens rather than another
  authentication implementation.
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

### Existing implementation

The repository currently contains:

- a Java 25 and JavaFX 25.0.3 Gradle application with a Shadow fat-JAR task;
- pure domain classes under `clubstock.domain` for accounts, equipment, LoanRequests, Loans,
  and reports;
- deterministic `Clock`-based request and Loan factories;
- JUnit Jupiter tests for the domain model; and
- a three-platform GitHub Actions build matrix.

The domain objects intentionally expose creation and lifecycle commands but not arbitrary
setters. Persistence therefore requires validated restoration APIs; repositories must not use
reflection or replay artificial business events to recreate stored state.

### Documentation discrepancies

- `docs/ProposedProjectPlan.md` still describes several phase-0 questions as unresolved even
  though `docs/ProjectRequirements.md`, the core-domain design, and the confirmed decisions
  above now resolve the ones used here.
- `docs/DeveloperGuide.md` says application sources and a launcher have not been added, but
  `Main` and `Launcher` now exist. The JavaFX-shell slice updates this guide.
- `docs/ProjectRequirements.md` contains duplicated `F4.2` and `N1.3` headings. They are
  editorial duplicates, not competing requirements.
- No behavioral conflict among the authoritative role and shared specifications affects this
  design.

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
migrations, creates repository and image-store adapters, creates services, and supplies
controllers through a controller factory. Startup either produces a fully usable context or
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

## Major Feature 1: SQLite Persistence and Transaction Boundary

**Behavior:** Persist every shared entity in one local database and execute repository-wide
invariants through explicit transactions.

**Mapped requirements:** `N1`, `N2`, `F9.3`, plus the persistence needs of `F1`–`F8`.

### Domain restoration APIs

- **Responsibility:** Recreate a previously validated entity without pretending that it is a
  newly submitted request, a newly started Loan, or a new item.
- **Interface:** Add a documented static `restore(...)` factory to each mutable aggregate.
  Add read access for persistence-relevant state that is currently internal, including
  EquipmentItem verification-pending state. Add guarded `deactivate` and `retire` lifecycle
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
  `CONFIRMED_ROLLBACK` from `COMMIT_OUTCOME_UNKNOWN` so resource-owning workflows can perform
  safe compensation.
- **Collaborators:** Every command service and the SQLite database adapter.
- **State and data:** Read-only queries use read operations; writes acquire their transaction
  before re-reading preconditions so stale UI state cannot authorize a transition.
- **Failure behavior:** Domain, authorization, constraint, or pre-commit I/O failures roll back.
  A commit error is reported as `CONFIRMED_ROLLBACK` only when non-durability is established;
  otherwise it is `COMMIT_OUTCOME_UNKNOWN`. Services discard mutated aggregate instances after
  either failure and never infer rollback solely from an exception returned by `commit()`.

### Interactions and data flow

At startup, the database directory is created, SQLite loads, migrations run, and the singleton
Exco row is inserted only when absent. A service command starts a write unit of work, reloads
all records used by its preconditions, applies domain operations, persists every affected
record, and commits once. Query services map restored domain data to immutable DTOs before the
unit of work closes.

### Acceptance criteria

- Fresh startup creates schema version 1 and exactly one unconfigured Exco account.
- Repeated startup is idempotent and retains data.
- Every valid domain lifecycle state round-trips through SQLite without timestamp changes.
- Invalid stored state stops loading with a data-integrity error.
- Unique IDs, folded type names, references, and one-report-per-Loan constraints are enforced.
- An injected failure in a multi-record command leaves every table unchanged.

## Major Feature 2: Authentication, Sessions, and Account Administration

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

## Major Feature 3: Catalogue, Inventory, and Request Decisions

**Behavior:** Provide all shared backend operations needed to manage inventory, browse safe
catalogue summaries, submit and decide requests, and atomically allocate items.

**Mapped requirements:** `F2.1`, `F2.2`, `F3`, `F4`, `F5`, and the related `F9` rules.

### `EquipmentCatalogService`

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

### `LoanRequestService`

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

## Major Feature 4: Unresolved Loans, Reports, and Verification

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
  image referenced by a committed report.

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

## Major Feature 5: JavaFX Authentication and Role Shell

**Behavior:** Replace the demonstration window with a packaged FXML application that performs
real authentication and routes users into isolated role shells.

**Mapped requirements:** `F1.1.2`, `F1.1.4`, `F1.2.3`, `F9.1.9`, `N1.5`, `N2.3`, and
`N2.4`.

### `Launcher`, `ClubStockApplication`, and `ApplicationContext`

- **Responsibility:** Start JavaFX without classpath-launcher issues and construct the complete
  backend once.
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

## Cross-Cutting Verification and Delivery

- Use JUnit Jupiter and temporary directories/databases; tests must never read or modify the
  user's real data directory.
- Test services against real SQLite adapters for transaction and constraint behavior. Small
  pure application tests may use in-memory fakes where SQL behavior is not under test.
- Inject `Clock`, `ZoneId`, ID generation, transaction-failure hooks, and image-store failure
  hooks to make boundary cases deterministic.
- Keep JavaFX controller logic testable without a visible Stage. Add a limited toolkit/FXML
  smoke test rather than coupling all service tests to JavaFX.
- Run `./gradlew test`, `./gradlew build`, `./gradlew javadoc`, and
  `./gradlew shadowJar` for completion.
- Extend the current Windows/macOS/Linux CI job to build the fat JAR and run its
  `--verify-install` mode against a temporary data directory. A Linux display-backed smoke
  check may additionally launch and close the role-selection screen.
- Update `docs/DeveloperGuide.md` in the JavaFX-shell slice with actual source entry points,
  data layout, first-run behavior, SQLite packaging, verification mode, and recovery guidance.

## Delivery Issues

### `SLICE-001` — Establish SQLite persistence and transactional repositories

#### Context

The existing domain model has no persistence or repository-wide transaction boundary.
ClubStock needs one durable source of truth before any role workflow can safely coordinate
Members, inventory, requests, Loans, and reports.

#### Requirements

- `N1` — Persist one shared source of truth for both roles.
- `N2.1`–`N2.4` — Keep storage behind services and repositories.
- `F9.3` — Enforce cross-record identity and lifecycle invariants.

#### Scope

- Add validated restoration and soft-removal state to the affected domain aggregates.
- Add SQLite JDBC, version-1 migrations, connection configuration, transaction management,
  repository ports/adapters, row mappers, UUID generation, and application-level errors.
- Persist and restore every existing domain aggregate and report.
- Add integration tests for schema, restoration, constraints, restart, and rollback.

#### Out of scope

- Authentication policy and role sessions.
- User-facing JavaFX screens.
- Workflow-specific service commands beyond repository/transaction test fixtures.
- Backup UI, external database servers, or encryption of the whole database.

#### Components

- **Domain restoration APIs:** Recreate valid persisted states without event replay.
- **SchemaMigrator/SqliteDatabase:** Initialize versioned storage safely.
- **Repository ports/adapters:** Store and query typed aggregates without leaking JDBC.
- **TransactionManager/UnitOfWork:** Commit or roll back one complete use case and distinguish a
  confirmed rollback from an ambiguous commit outcome for safe external-resource cleanup.
- **Application errors and ID generator:** Standardize failure mapping and internal IDs.

#### Acceptance criteria

- [ ] Fresh and repeated startup produce one valid version-1 database and singleton Exco row.
- [ ] Every valid entity state round-trips without timestamp or identity changes.
- [ ] Invalid rows and future schema versions fail explicitly without data replacement.
- [ ] Unique IDs, type names, references, and report cardinality are constrained.
- [ ] A forced mid-operation failure rolls back all database changes.
- [ ] A failed transaction reports `CONFIRMED_ROLLBACK` only when non-durability is established;
  otherwise a commit error reports `COMMIT_OUTCOME_UNKNOWN`.
- [ ] Tests use temporary paths and pass on the existing three-platform build matrix.

#### Test scenarios

- Initialize an empty directory twice and compare schema/account state.
- Round-trip each request, Loan, item, account, and report lifecycle state.
- Attempt duplicate identities, duplicate folded type names, broken foreign keys, and duplicate
  reports.
- Inject an exception after several writes and verify the pre-transaction snapshot remains.
- Simulate confirmed rollback and indeterminate commit failures and verify their transaction
  outcomes remain distinguishable to callers.
- Open malformed, read-only, and unsupported-version databases and verify safe failure.

#### Dependencies

- Existing approved core-domain design and implementation.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-001`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-001 -->

### `SLICE-002` — Implement authentication, sessions, and Member account administration

#### Context

The application needs secure credentials and a trusted role/identity boundary before any
service or shell can authorize Member and Exco behavior.

#### Requirements

- `F1.1` — Support the singleton Exco account, first-run setup, and login.
- `F1.2` — Support Exco-created Member credentials and Member login.
- `F1.3` — View, create, edit, and safely remove Members.
- `F9.1.9`, `F9.3.1` — Restrict Exco functions and preserve unique Member identity.

#### Scope

- Add PBKDF2 hashing/verification with the agreed format and password validation.
- Add role-first authentication operations, one in-memory session, and logout.
- Add Exco-only Member account queries and create/edit/password-replacement/deactivation
  commands.
- Enforce unresolved-reference checks and inactive-account login rejection.

#### Out of scope

- Authentication screens, remembered login, idle timeout, Exco password change/recovery, and
  Member self-registration.
- Member and inventory feature screens.
- Audit logs and account-deletion history views.

#### Components

- **Pbkdf2PasswordHasher:** Creates and verifies versioned salted hashes.
- **AuthenticationService/SessionManager:** Establishes and clears the current principal.
- **MemberAccountService:** Performs authorized Member administration transactionally.
- **Account query DTOs:** Expose safe Member summaries without password hashes.

#### Acceptance criteria

- [ ] Passwords follow the confirmed validation and PBKDF2 policy and are never persisted in
  plaintext.
- [ ] Exco setup succeeds once; normal Exco login works afterward.
- [ ] Active Members can log in and inactive/unknown Members receive the same generic failure.
- [ ] Only Exco can manage Members; Member ID never changes.
- [ ] Member removal is blocked by pending requests or unresolved Loans and otherwise soft
  deactivates the account.
- [ ] Logout invalidates subsequent authenticated operations.

#### Test scenarios

- Hash the same password twice, verify both, and confirm distinct salts/encodings.
- Test Unicode, boundary-length, all-whitespace, mismatched-confirmation, and wrong-password
  inputs.
- Complete Exco setup, repeat setup, log in/out, and attempt use after logout.
- Create/edit/deactivate Members as Exco and attempt every command as a Member.
- Attempt deactivation with each blocking and nonblocking request/Loan status.

#### Dependencies

- `SLICE-001` — Requires account repositories and transactions.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-002`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-002 -->

### `SLICE-003` — Manage EquipmentTypes and individual inventory

#### Context

Exco needs to provision requestable categories and physical items while Members need an
Equipment-ID-free view of offered types and current availability.

#### Requirements

- `F2.1`, `F2.2` — Preserve the type/item distinction and item states.
- `F3` — Support Member discovery, Exco inventory management, and ID visibility.
- `F9.2.2`, `F9.2.3`, `F9.2.9`, `F9.2.10` — Prevent invalid allocation/removal/release.
- `F9.3.2`, `F9.3.6`, `F9.3.9`, `F9.3.10`, `F9.3.16` — Preserve inventory integrity.

#### Scope

- Add Exco operations for type creation, rename, offer/unoffer, and protected deletion.
- Add Exco operations for item creation, release, retirement, and detailed inventory queries.
- Add Member-safe offered-type summaries with calculated available quantity.
- Implement uniqueness, reference, authorization, and soft-retirement behavior.

#### Out of scope

- Inventory JavaFX screens.
- Automatic physical Equipment ID generation.
- Reservation of future inventory or bulk import.
- Authoritative return/loss verification, which belongs to `SLICE-008`.

#### Components

- **EquipmentCatalogService:** Owns type/item commands and role-filtered queries.
- **Catalogue DTO:** Contains type identity/name and available count only.
- **Inventory DTO:** Contains Exco-visible IDs, type, condition, availability, and retirement.
- **Equipment repositories:** Supply counts and unresolved-reference checks transactionally.

#### Acceptance criteria

- [ ] Exco can manage types and manually identified items under the agreed initial states.
- [ ] Fold-equivalent type names and duplicate Equipment IDs are rejected.
- [ ] Members see only offered types and counts of non-retired `AVAILABLE` items.
- [ ] Members never receive unassigned Equipment IDs.
- [ ] Lost/retired items cannot be released and unresolved items cannot be retired.
- [ ] Referenced types must be unoffered and remain undeletable until all references are gone.

#### Test scenarios

- Create, rename, offer, unoffer, and delete referenced/unreferenced types.
- Add, release, allocate-test, and retire items in every relevant condition/availability state.
- Compare Member and Exco query fields for the same inventory.
- Recalculate counts after release, retirement, allocation, and type filtering.
- Attempt all commands with the wrong role and verify no data changes.

#### Dependencies

- `SLICE-001` — Requires equipment repositories and transactions.
- `SLICE-002` — Requires Exco/Member authorization.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-003`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-003 -->

### `SLICE-004` — Submit, query, cancel, and reject LoanRequests

#### Context

Members need to request offered types without reserving items, while Exco needs a deterministic
pending queue and both roles need authorized state transitions.

#### Requirements

- `F4` — Validate, submit, view, order, and cancel requests.
- `F5.4` — Manually reject pending requests.
- `F9.1.2`, `F9.1.3`, `F9.3.5`, `F9.3.11`, `F9.3.12`, `F9.3.13` — Enforce request role,
  ownership, and terminal-state rules.

#### Scope

- Add Member submission preview with a zero-stock warning flag and confirmed submission.
- Add Member own-request query containing the opaque Request ID, EquipmentType, requested
  quantity, requested start and end dates, status, and approved quantity when applicable, plus
  owner-only pending cancellation by Request ID.
- Add Exco pending queue ordered by `requestedAt` then Request ID, containing the opaque Request
  ID, Member, EquipmentType, requested quantity, current availability, requested dates,
  `requestedAt`, and optional details.
- Add Exco-only pending rejection.

#### Out of scope

- Request screens.
- Item selection, approval, Loan creation, and automatic exhaustion rejection.
- Rejection/cancellation reasons and request reopening.

#### Components

- **LoanRequestService:** Coordinates all non-approval request use cases.
- **Submission preview DTO:** Returns normalized input and whether zero-stock confirmation is
  required.
- **Member request DTO:** Exposes the opaque Request ID, EquipmentType ID and display name,
  requested quantity, requested start and end dates, request status, and approved quantity
  when applicable.
- **Pending request DTO:** Exposes the opaque Request ID, Member ID and display name,
  EquipmentType ID and display name, requested quantity, current available quantity, requested
  start and end dates, `requestedAt`, and optional details without reserving inventory.

#### Acceptance criteria

- [ ] Valid submission creates one pending request with generated identity/time and no item
  state changes.
- [ ] Zero stock requires warning confirmation but remains valid.
- [ ] Members see only their own requests, with the opaque Request ID, EquipmentType, requested
  quantity, requested start and end dates, status, and approved quantity when applicable, and
  can cancel only their own pending request identified by that Request ID.
- [ ] Exco sees every pending request in deterministic oldest-first order; each row contains the
  opaque Request ID and every field required by `F4.4.4`, and may be rejected by Request ID.
- [ ] Non-pending requests reject cancellation and rejection without mutation.
- [ ] Unoffered/missing types and inactive Members cannot create requests.

#### Test scenarios

- Preview and submit positive, zero-stock, invalid-quantity, reversed-date, and blank-details
  requests.
- Submit two requests at the same clock instant and verify Request-ID tie-breaking.
- Query as each Member; verify every returned DTO contains the opaque Request ID and required
  EquipmentType, requested quantity, requested dates, status, and conditional approved
  quantity, and ensure cross-Member records are absent.
- Submit duplicate-looking pending requests, cancel one by its Request ID, and verify the other
  remains pending and unchanged.
- Query duplicate-looking pending requests as Exco; verify that each DTO contains a distinct
  Request ID plus Member, EquipmentType, quantity, current availability, requested dates,
  `requestedAt`, and optional details, then reject one by its Request ID and verify the other
  remains pending and unchanged.
- Cancel/reject in every request state and with both roles.
- Verify that submission never changes item availability.

#### Dependencies

- `SLICE-002` — Requires authenticated Member and Exco principals.
- `SLICE-003` — Requires offered types and availability queries.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-004`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-004 -->

### `SLICE-005` — Approve requests with atomic allocation and exhaustion rejection

#### Context

Approval changes a request, multiple items, multiple new Loans, and sometimes competing
requests. These changes must be one indivisible operation.

#### Requirements

- `F5.1`–`F5.3` — Validate selection, approve, allocate, create Loans, and reject on exhaustion.
- `F6.1` — Start each resulting Loan immediately with the request end date.
- `F9.2.1`–`F9.2.6`, `F9.3.6`–`F9.3.12` — Enforce allocation and uniqueness invariants.

#### Scope

- Add an Exco-only approval command for a pending request and explicit Equipment-ID selection.
- Re-read every precondition inside one write transaction.
- Create one Loan per selected item and close partial approvals.
- Reject other pending same-type requests when committed availability becomes zero.
- Add rollback and stale-selection integration tests.

#### Out of scope

- Approval UI.
- Automatic selection, reservations, waitlists, partial remainder requests, or reopening.
- Approval/rejection reasons.

#### Components

- **ApprovalService:** Owns the complete approval transaction.
- **Loan ID generation:** Produces one unique ID per selected item.
- **Approval result DTO:** Returns approved quantity, created Loan IDs, and automatically
  rejected Request IDs.
- **Transactional repositories:** Recheck request/item state and persist all changes once.

#### Acceptance criteria

- [ ] Only Exco can approve a currently pending request.
- [ ] Selection is nonempty, unique, same-type, non-retired, available, and within requested
  quantity.
- [ ] The request approved quantity equals the selection size.
- [ ] Every selected item becomes `ON_LOAN` and produces exactly one `ON_LOAN` Loan.
- [ ] Partial approval leaves no pending remainder.
- [ ] Reaching zero availability rejects only other pending requests of that type.
- [ ] Any validation, constraint, or injected persistence failure rolls back every effect.

#### Test scenarios

- Approve full and partial quantities and inspect all resulting records.
- Attempt zero, duplicate, excessive, wrong-type, unavailable, retired, and stale selections.
- Race two transactions for the same item and verify only one can commit.
- Exhaust one type and verify same-type versus other-type pending requests.
- Inject failures after request approval, item allocation, Loan insertion, and auto-rejection.

#### Dependencies

- `SLICE-003` — Requires managed inventory.
- `SLICE-004` — Requires pending requests and rejection behavior.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-005`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-005 -->

### `SLICE-006` — Query unresolved Loans and calculate overdue visibility

#### Context

Members and Exco need role-appropriate views of independently managed Loans before return and
verification actions can target them safely.

#### Requirements

- `F6.2`, `F6.3` — Expose individual active Loans to the appropriate role.
- `F6.4` — Calculate overdue without changing Loan status.
- `F6.5` — Do not expose active-Loan cancellation.
- `F9.1.1`, `F9.3.8`–`F9.3.10`, `F9.3.15` — Preserve visibility and active lifecycle rules.

#### Scope

- Add Member own-unresolved-Loans and Exco all-unresolved-Loans queries.
- Join type names and Equipment IDs into immutable role-filtered DTOs.
- Calculate overdue with the injected clock and startup system timezone.
- Exclude completed Loans from default active queries.

#### Out of scope

- Loan feature screens, completed-history views, extensions, cancellation, and follow-up
  notifications.
- Return/loss commands and verification queues.

#### Components

- **LoanQueryService:** Applies role/ownership filtering and overdue calculation.
- **Loan view DTOs:** Carry type, assigned Equipment ID, status, startedAt, end date, and
  overdue flag.
- **Repository join queries:** Resolve references without exposing unrelated IDs.

#### Acceptance criteria

- [ ] Members see only their `ON_LOAN`, `RETURN_PENDING`, and `LOST_PENDING` Loans.
- [ ] Exco sees all unresolved individual Loans and assigned IDs.
- [ ] Overdue is true only for `ON_LOAN` strictly after the end date in the startup timezone.
- [ ] Querying overdue never mutates or persists a status change.
- [ ] Completed Loans and unassigned Equipment IDs are absent from Member active views.

#### Test scenarios

- Query multiple Members, statuses, types, and items as each role.
- Check before/on/after end-date boundaries with fixed instants and multiple zones.
- Verify pending return/loss records remain visible but not overdue.
- Verify completed records are stored yet omitted.
- Attempt unauthenticated and cross-role queries.

#### Dependencies

- `SLICE-005` — Requires transactionally created Loans and allocations.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-006`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-006 -->

### `SLICE-007` — Submit returns, damage evidence, and loss reports atomically

#### Context

A Member action must move one Loan and item into a matching pending state and, for damage or
loss, preserve required evidence without affecting sibling Loans.

#### Requirements

- `F7` — Support distinct good-return, damaged-return, and lost-item submissions.
- `F9.1.5`–`F9.1.8`, `F9.3.8`, `F9.3.10`, `F9.3.15` — Enforce ownership, evidence, and
  active-Loan restrictions.

#### Scope

- Add managed JPEG/PNG inspection, absolute-or-relative source selection, staging, final
  storage, cleanup, and reference resolution.
- Add Member commands for good return, damaged return, and report lost.
- Persist Loan/item transitions and reports in coordinated transactions.
- Ensure confirmed rollback cleans new managed files, ambiguous commit outcomes retain finalized
  evidence unless a committed-reference check proves it unreferenced, and startup removes both
  abandoned staging files and finalized managed images that have no committed damage-report
  reference.

#### Out of scope

- Return/loss screens and Exco verification.
- Video or other image formats, persisted references to files outside managed storage, report
  edits, disputes, or recovery.
- Additional descriptions, notes, or audit metadata beyond the required reports.

#### Components

- **ManagedDamageImageStore:** Owns safe evidence validation and storage.
- **MemberLoanService:** Authorizes and executes the three submission branches.
- **Damage/Loss repositories:** Enforce one applicable report per Loan.
- **Image/transaction coordination:** Deletes finalized evidence only after confirmed rollback
  or confirmed absence of a committed reference, retains evidence after an ambiguous commit
  outcome, and relies on startup reconciliation for safe eventual cleanup.

#### Acceptance criteria

- [ ] Only the owning Member can act on an `ON_LOAN` Loan.
- [ ] Good return creates no damage report and holds the item for verification.
- [ ] Damaged return requires decoded JPEG/PNG evidence within 1 byte–5 MiB and a description.
- [ ] Lost submission requires a description and does not set authoritative condition `LOST`.
- [ ] Repeat, cross-branch, and sibling-Loan mutations are rejected.
- [ ] Confirmed rollback and image failures leave no partial state or committed missing-file
  reference.
- [ ] If commit durability is ambiguous, cleanup retains the finalized image unless an
  independent query confirms that no committed damage report references it.
- [ ] Startup recovery removes staged and finalized files without committed references while
  retaining every finalized image referenced by a committed damage report.

#### Test scenarios

- Submit every valid branch and inspect Loan, item, and report state.
- Attempt actions as another Member and after every non-`ON_LOAN` status.
- Test empty, oversized, mislabeled, malformed, PNG, and JPEG source files; accept valid
  absolute and relative source paths, and reject absolute or traversing persisted storage keys.
- Inject staging, finalization, repository, and confirmed-rollback failures and inspect normal
  cleanup.
- Simulate `commit()` reporting an error after durably committing the report; verify cleanup
  preserves the finalized evidence and the committed reference remains resolvable.
- Simulate an ambiguous commit outcome with no committed report and an unavailable reference
  check; verify cleanup retains the finalized file until startup reconciliation removes it.
- Simulate interruption after finalization but before commit, then verify startup reconciliation
  deletes the unreferenced finalized file and preserves every committed referenced image.
- Submit for one of several Loans from a request and verify siblings remain unchanged.

#### Dependencies

- `SLICE-005` — Requires allocated Loans.
- `SLICE-006` — Requires authorized Loan lookup/query behavior.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-007`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-007 -->

### `SLICE-008` — Verify returns and loss reports authoritatively

#### Context

Member reports remain advisory until Exco applies a final condition and availability while
completing the affected Loan.

#### Requirements

- `F8` — Query and verify pending returns and losses.
- `F9.1.5`, `F9.1.6`, `F9.3.14` — Reserve authoritative verification for Exco.
- `F2.2.8`–`F2.2.11` — Preserve authoritative condition and availability combinations.

#### Scope

- Add Exco pending-return and pending-loss queries with all required evidence.
- Add verify-good, verify-damaged-available, verify-damaged-unavailable, and confirm-lost
  commands.
- Complete the Loan and item outcome in one transaction.
- Permit Exco's result to differ from the Member's advisory report.

#### Out of scope

- Verification screens, verification notes, dispute handling, return rejection, lost-item
  recovery, and transitions out of `COMPLETED`.

#### Components

- **VerificationService:** Owns Exco queries and authoritative commands.
- **Pending verification DTOs:** Carry Member, type, Equipment ID, reported condition, and
  applicable evidence.
- **Transactional Loan/item update:** Completes matching branches without partial outcomes.

#### Acceptance criteria

- [ ] Only Exco can query or complete pending verification work.
- [ ] Good verification completes the Loan and produces `GOOD`/`AVAILABLE`.
- [ ] Damaged verification completes the Loan and honors Exco's available/unavailable choice.
- [ ] Loss confirmation completes the Loan and produces `LOST`/`UNAVAILABLE`.
- [ ] Exco can choose an outcome different from the reported return condition.
- [ ] Wrong-branch, stale, repeated, and failed operations leave state unchanged.

#### Test scenarios

- Query good, damaged, and loss reports and verify required evidence fields.
- Verify reported good as damaged and reported damaged as good.
- Verify both damaged availability choices and confirmed lost.
- Attempt each command as a Member and against every wrong Loan status.
- Inject failure between Loan and item updates and confirm rollback.

#### Dependencies

- `SLICE-007` — Requires pending workflows and stored reports.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-008`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-008 -->

### `SLICE-009` — Build the JavaFX authentication and role shell

#### Context

The repository still launches a demonstration label. A real application shell must initialize
the shared backend, authenticate users, and create separate extension points for later Member
and Exco screens.

#### Requirements

- `F1.1.2`, `F1.1.4`, `F1.2.3` — Expose first-run setup and role authentication.
- `F9.1.9` — Keep Exco-only areas unavailable to Members.
- `N1.5` — Maintain separate role-specific interfaces.
- `N2.3`, `N2.4` — Preserve controller/service boundaries and storage isolation.

#### Scope

- Replace the demo application with named launcher/application/context classes.
- Add role-choice, Exco setup/login, Member login, Member home, and Exco home FXML views.
- Add central navigation/controller injection, shared CSS, safe error presentation, and logout.
- Add packaged verification mode and fat-JAR resource/SQLite smoke checks.
- Correct the stale JavaFX section in `docs/DeveloperGuide.md`.

#### Out of scope

- Member catalogue/request/Loan screens and Exco account/inventory/request/verification
  screens.
- Remembered login, idle timeout, password recovery/change, custom themes, or advanced
  accessibility certification.

#### Components

- **Launcher/ClubStockApplication/ApplicationContext:** Start and compose the application.
- **Navigator/controller factory:** Load authorized FXML routes with injected services.
- **Authentication controllers:** Handle role choice, setup, login, and safe validation.
- **Role shells:** Supply separate home/content hosts and logout.
- **CSS/resources and verification mode:** Establish packaging and UI conventions.

#### Acceptance criteria

- [ ] Fresh Exco flow performs setup; later Exco flow performs login.
- [ ] Active Members log in with Member ID/password and reach only the Member shell.
- [ ] Role routes cannot be opened without the matching principal.
- [ ] Logout clears the session and blocks reuse through navigation history.
- [ ] Controllers contain no JDBC or repository access.
- [ ] FXML/CSS and SQLite load from the fat JAR on the three-platform CI matrix.
- [ ] The developer guide describes the actual launcher, data directory, and verification
  workflow.

#### Test scenarios

- Exercise fresh setup, repeat startup, correct/incorrect Exco login, and Member login states.
- Attempt direct and back-navigation into both shells with no session and the wrong role.
- Verify password fields clear and generic login errors reveal no account-existence detail.
- Load every FXML through the controller factory in a temporary application context.
- Run packaged `--verify-install` with an empty and existing temporary data directory.
- Launch and close the role-choice screen in a display-backed smoke environment.

#### Dependencies

- `SLICE-001` — Requires a startup-ready persisted application context.
- `SLICE-002` — Requires authentication and session services.
- `SLICE-003`–`SLICE-008` — The final composition root constructs the completed shared
  backend that later feature controllers will consume.

#### Traceability

- Design document: `docs/plans/shared-backend-javafx-foundation.md`
- Behavior slice: `SLICE-009`

<!-- plan-to-docs:shared-backend-javafx-foundation:SLICE-009 -->

## Traceability

| Slice ID | Requirements | Major feature | GitHub issue | State |
| --- | --- | --- | --- | --- |
| `SLICE-001` | `N1`, `N2`, `F9.3` | SQLite persistence and transactions | [#12](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/12) | Open |
| `SLICE-002` | `F1`, `F9.1.9`, `F9.3.1` | Authentication and accounts | [#13](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/13) | Open |
| `SLICE-003` | `F2.1`, `F2.2`, `F3`, relevant `F9` | Catalogue and inventory | [#14](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/14) | Open |
| `SLICE-004` | `F4`, `F5.4`, relevant `F9` | Request submission and decisions | [#15](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/15) | Open |
| `SLICE-005` | `F5.1`–`F5.3`, `F6.1`, relevant `F9` | Approval and allocation | [#16](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/16) | Open |
| `SLICE-006` | `F6.2`–`F6.5`, `F9.1.1` | Active Loan visibility | [#17](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/17) | Open |
| `SLICE-007` | `F7`, relevant `F9` | Return and loss submission | [#18](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/18) | Open |
| `SLICE-008` | `F8`, `F9.3.14` | Exco verification | [#19](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/19) | Open |
| `SLICE-009` | Authentication requirements, `N1.5`, `N2.3`, `N2.4` | JavaFX shell | [#20](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/20) | Open |

## Assumptions and Open Questions

- The current branch's core-domain work is the implementation baseline even if every commit
  has not yet been merged into `master`.
- SQLite is accessed by one application process. Database transactions still protect stale or
  repeated UI actions; multi-process coordination is not a supported operating mode.
- Completed records remain persisted to preserve references but no completed-history UI is
  required.
- Soft-removed Member and Equipment IDs remain permanently reserved.
- Date pickers use the runtime locale for display; persistence and service DTOs retain
  `LocalDate`/ISO semantics.
- The 600,000 PBKDF2 work factor is encoded with each hash so a future migration can verify old
  hashes and upgrade them after successful authentication.
- GitHub label discovery on 2026-09-22 confirmed that the existing `enhancement` label means
  "New feature or request" and clearly applies to all nine slices.
- Duplicate discovery on 2026-09-22 inspected every open and closed issue body in the target
  repository and found no pre-existing `shared-backend-javafx-foundation` traceability
  marker. After explicit confirmation, all nine slices were created as issues #12–#20 and
  linked in the traceability table.
