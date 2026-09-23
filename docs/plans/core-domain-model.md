<!-- plan-to-docs-document: core-domain-model -->
# Core Domain Model Engineering Design

| Field | Value |
| --- | --- |
| Status | Approved |
| Source plan | Latest finalized conversation plan: Item 1 — Core Domain Model |
| Document ID | `core-domain-model` |
| Target repository | `CS3227-2610-MP2-ClubStock/CS3227-2610-MP2` |

## Status in the active roadmap

The core domain is implemented and retained as the baseline for the
[parallel implementation roadmap](parallel-role-implementation.md). Repository-state
statements and delivery issues below describe the original domain-design stage; they
are historical context, not a request to recreate implemented classes or tests.

## Summary and Goals

This design implements phase 1 of `docs/ProposedProjectPlan.md`: a framework-independent
domain model for ClubStock's accounts, equipment, requests, individual loans, and member
reports. It is written for the engineers implementing the shared foundations before the
Member and Exco user interfaces diverge.

The implementation succeeds when:

- every required entity and state can be represented without JavaFX or persistence;
- entity-local invariants and lifecycle transitions reject invalid changes before mutating
  state;
- relationships use typed identities rather than copied entity data;
- timestamps and overdue checks are deterministic in tests;
- tests mirror the production package hierarchy and run through Gradle on JDK 25; and
- unresolved cross-record rules are clearly reserved for the shared backend phase.

This phase does not implement repositories, persistence, authentication services,
authorization, JavaFX screens, or atomic workflows spanning multiple entities. It also does
not move the existing default-package `Main` and `Launcher`; application packaging belongs to
the JavaFX application-structure phase.

## Requirements and Repository Grounding

### Stated requirements

`docs/ProjectRequirements.md`, `docs/Shared.md`, `docs/MemberSpec.md`, and
`docs/ExcoSpec.md` define the authoritative model and lifecycle. In particular:

- a Member account has a unique Member ID, name, and credentials;
- EquipmentType is requestable while EquipmentItem represents a physical item;
- EquipmentItem condition and availability are separate state dimensions;
- a pending LoanRequest names an EquipmentType and never an EquipmentItem;
- approval produces one independently managed Loan per assigned EquipmentItem;
- member damage and loss submissions are advisory until Exco verification; and
- completed, rejected, and cancelled records do not transition back into active states.

Where `docs/ProjectDescription.md` conflicts, the authoritative specifications take
precedence. This applies to Exco-created Member accounts, zero-stock requests remaining
valid after a warning, and inventory being represented as individual EquipmentItems rather
than only aggregate quantities.

### Confirmed product decisions from phase 0

- Member ID is an immutable, case-sensitive login identifier.
- Exco may edit a Member's name and replace their password, but cannot edit the Member ID.
- Passwords require at least eight characters and are stored only as salted
  PBKDF2-HMAC-SHA256 hashes.
- The singleton Exco account starts without a credential and therefore requires local
  first-run password setup.
- EquipmentTypes have stable IDs, case-insensitively unique trimmed names, and an offered
  flag. New types start unoffered.
- A referenced EquipmentType is unoffered; it is hard-deleted only when no item, request, or
  loan references it.
- New EquipmentItems start `GOOD` and `UNAVAILABLE` and require explicit Exco release.
- Requested dates use `LocalDate`. Past starts are valid, but an end date cannot precede its
  start date.
- Request and Loan creation times use `Instant`. An `ON_LOAN` Loan is overdue only when the
  supplied current local date is strictly after its end date.
- Member removal is blocked by a `PENDING` LoanRequest or a Loan in `ON_LOAN`,
  `RETURN_PENDING`, or `LOST_PENDING`.
- Damage evidence is JPEG or PNG, no larger than 5 MiB, copied to application-managed
  storage. The domain stores a relative opaque storage reference and metadata.
- Required text is trimmed and nonblank. Optional request details normalize blank input to
  absence. No additional maximum text lengths are imposed.

### Repository constraints and implementation inferences

- The project uses Gradle, Java 25, JavaFX 25.0.3, and the existing multi-platform build.
- The repository currently has only `Main` and `Launcher` production sources and no test
  sources. JUnit Jupiter must therefore be configured before domain tests can run.
- **Implementation inference:** use `clubstock.domain` as the package root because the
  SE-EDU Java standard requires named, lowercase packages rooted in the project name.
- **Implementation inference:** use standard `IllegalArgumentException` for invalid values
  and `IllegalStateException` for invalid lifecycle transitions instead of introducing an
  exception hierarchy before services need one.
- **Implementation inference:** typed identifiers receive their string values from callers.
  Generation and repository-wide uniqueness are backend responsibilities.

## Architecture and Constraints

### Package structure

```text
src/main/java/clubstock/domain/
├── account/
│   ├── ExcoAccount.java
│   ├── Member.java
│   ├── MemberId.java
│   └── PasswordHash.java
├── equipment/
│   ├── EquipmentAvailability.java
│   ├── EquipmentCondition.java
│   ├── EquipmentId.java
│   ├── EquipmentItem.java
│   ├── EquipmentType.java
│   ├── EquipmentTypeId.java
│   └── EquipmentTypeName.java
├── request/
│   ├── LoanRequest.java
│   ├── LoanRequestId.java
│   └── LoanRequestStatus.java
├── loan/
│   ├── Loan.java
│   ├── LoanId.java
│   ├── LoanStatus.java
│   └── ReportedReturnCondition.java
└── report/
    ├── DamageImageFormat.java
    ├── DamageImageReference.java
    ├── DamageReport.java
    └── LossReport.java
```

Tests use the identical path beneath `src/test/java` and append `Test` to the tested
production class name. Behavior-free enums are exercised through their owning entities and
do not receive tests that merely repeat their constants.

### Common modeling rules

- Identifier records trim surrounding whitespace, reject null or blank values, preserve
  case, and remain distinct Java types.
- Mutable entity classes are final, keep identity fields final, expose no general setters,
  and compare by typed identity.
- Relationships store typed IDs. Repositories and services resolve collaborating entities,
  avoiding cycles and stale embedded copies.
- Creation factories establish valid initial states. Lifecycle commands check all local
  preconditions before changing fields.
- `Clock` is supplied to factories that capture the current instant. `LocalDate` is supplied
  to overdue checks so the domain does not choose a timezone.
- Every public class and public nontrivial method follows the project's SE-EDU Javadoc and
  Java formatting requirements.

## Major Feature 1: Account Identities and Credential State

**Behavior:** Represent Member and singleton Exco accounts without retaining plaintext
passwords, while preserving immutable account identity and first-run Exco state.

**Mapped requirements:** `F1.1.1`, `F1.1.2`, `F1.2.1`, `F1.2.2`, `F1.2.4`, `F1.3.2`,
`F9.3.1`.

### `MemberId`

- **Responsibility:** Provides the unique, case-sensitive Member and login identity.
- **Interface:** Constructs from a string and exposes the normalized stored value.
- **Collaborators:** `Member`, `LoanRequest`, `Loan`, and later account repositories.
- **State and data:** Trimmed nonblank value; case remains unchanged.
- **Failure behavior:** Rejects null or blank values with `IllegalArgumentException`.

### `PasswordHash`

- **Responsibility:** Prevents account entities from accepting or exposing plaintext
  credentials.
- **Interface:** Wraps one nonblank encoded hash string.
- **Collaborators:** `Member`, `ExcoAccount`, and the later password hashing service.
- **State and data:** Opaque encoded hash; it does not perform PBKDF2 itself.
- **Failure behavior:** Rejects null or blank encodings.

### `Member`

- **Responsibility:** Owns Member identity, display name, and current credential hash.
- **Interface:** Creation factory, identity/name/hash accessors, `updateName`, and
  `replacePasswordHash`.
- **Collaborators:** Typed identifiers now; account repository and authentication service
  later.
- **State and data:** Immutable `MemberId`; mutable validated name and `PasswordHash`.
- **Failure behavior:** Invalid edits throw before changing the previous value.

### `ExcoAccount`

- **Responsibility:** Represents the one logical Exco account and whether first-run password
  setup is required.
- **Interface:** `createForFirstRun`, `requiresPasswordSetup`, optional hash access, and
  `completeInitialPasswordSetup`.
- **Collaborators:** Startup/authentication services in later phases.
- **State and data:** Initially no hash; successful setup stores one hash and clears the
  setup-required state.
- **Failure behavior:** Rejects null hashes and repeated initial setup without overwriting the
  established credential.

### Interactions and data flow

Exco-created Member input is validated and converted to `MemberId` and `PasswordHash` before
constructing `Member`. On first application startup, the later authentication service loads
the singleton `ExcoAccount`; absence of its hash selects password setup rather than normal
login. Item 1 only models these states and does not hash input or perform authentication.

### Acceptance criteria

- A valid Member has a stable typed ID, trimmed name, and opaque password hash.
- Member name and hash can be replaced independently without changing identity.
- Case variants of a Member ID remain distinct values.
- A first-run Exco account clearly reports that setup is required.
- Initial Exco setup cannot be applied twice.

## Major Feature 2: Equipment Catalogue and Item Lifecycle

**Behavior:** Represent requestable equipment categories separately from physical inventory,
including authoritative condition and allocation availability.

**Mapped requirements:** `F2.1`, `F2.2`, `F3.1.1`-`F3.1.4`, `F3.2.1`-`F3.2.7`,
`F9.2.2`, `F9.2.3`, `F9.2.9`, `F9.2.10`, `F9.3.2`, `F9.3.6`, `F9.3.9`, `F9.3.10`,
`F9.3.16`.

### `EquipmentTypeId` and `EquipmentId`

- **Responsibility:** Distinguish category identity from physical-item identity.
- **Interface:** Construct from normalized nonblank string values.
- **Collaborators:** Equipment entities, requests, loans, and later repositories.
- **Failure behavior:** Reject invalid identifier values.

### `EquipmentTypeName`

- **Responsibility:** Preserve display capitalization while producing a stable
  case-insensitive comparison key.
- **Interface:** Exposes the trimmed display value and a locale-independent Unicode
  case-folded comparison key.
- **Collaborators:** `EquipmentType` and the later repository uniqueness check.
- **Failure behavior:** Rejects null or blank names.

### `EquipmentType`

- **Responsibility:** Represents an equipment category that may be published for Member
  browsing and requests.
- **Interface:** Creation, rename, offer, and unoffer operations.
- **Collaborators:** `EquipmentTypeName`, EquipmentItems, LoanRequests, and repositories.
- **State and data:** Immutable ID, mutable name, and offered flag; new instances start
  unoffered.
- **Failure behavior:** Invalid rename attempts leave the current name intact. Cross-record
  duplicate names are rejected later at the service/repository boundary.

### `EquipmentItem`

- **Responsibility:** Owns one physical item's authoritative condition and availability.
- **Interface:** Creation plus guarded release, allocation, verification hold, good return,
  damaged return, and confirmed-loss transitions.
- **Collaborators:** EquipmentType by ID now; Loan and inventory services later.
- **State and data:** New items are `GOOD`/`UNAVAILABLE`. `LOST` always implies
  `UNAVAILABLE`; `DAMAGED` may be available or unavailable according to Exco assessment.
- **Failure behavior:** Rejects allocation unless `AVAILABLE`, verification hold unless
  `ON_LOAN`, release when `LOST`, and invalid condition/availability combinations without
  partial mutation.

### Interactions and data flow

An item is created against an EquipmentType ID, explicitly released, and then allocated by a
later approval service. Return or loss submission moves only availability from `ON_LOAN` to
`UNAVAILABLE`; the authoritative condition remains unchanged. Exco verification then applies
the final condition and availability together through an item transition. Repository checks
later ensure that referenced types/items cannot be hard-deleted.

### Acceptance criteria

- EquipmentType and EquipmentItem identities cannot be mixed.
- A new type is unoffered and a new item is `GOOD`/`UNAVAILABLE`.
- Allocation is possible only after explicit release.
- Holding an allocated item for verification retains its authoritative condition.
- Good and both damaged verification outcomes produce the documented states.
- Confirmed lost items cannot be released.

## Major Feature 3: LoanRequest Lifecycle

**Behavior:** Represent a Member's request for a quantity of one EquipmentType, including
validated dates, automatic submission time, terminal state transitions, and approved
quantity.

**Mapped requirements:** `F2.3`, `F4.1`, `F4.2`, `F4.3.4`-`F4.3.7`, `F5.1.2`,
`F5.1.6`-`F5.1.9`, `F5.2.1`, `F5.2.2`, `F5.2.7`, `F5.4`, `F9.3.3`, `F9.3.5`,
`F9.3.11`, `F9.3.13`.

### `LoanRequestId` and `LoanRequestStatus`

- **Responsibility:** Provide request identity and its closed set of lifecycle states.
- **Interface:** Typed ID plus `PENDING`, `APPROVED`, `REJECTED`, and `CANCELLED` states.
- **Collaborators:** `LoanRequest`, `Loan`, and later repositories/services.

### `LoanRequest`

- **Responsibility:** Own request input, lifecycle, creation time, and optional approval
  result.
- **Interface:** `submit(..., Clock)`, `approve(int)`, `reject`, and
  `cancelBy(MemberId)` plus read-only accessors.
- **Collaborators:** Member and EquipmentType through IDs; approval and cancellation services
  later.
- **State and data:** Positive requested quantity, requested start/end `LocalDate`, normalized
  optional details, automatic `requestedAt`, status, and optional approved quantity. No
  EquipmentItem ID is stored.
- **Failure behavior:** Rejects nonpositive quantities, end-before-start dates, approval
  outside `1..requestedQuantity`, non-owner cancellation, and transitions from non-pending
  states before mutation.

### Interactions and data flow

Submission receives validated typed references and a `Clock`, captures `requestedAt`, and
starts `PENDING` without affecting equipment. Later services establish that referenced
records exist and orchestrate approval with item allocation and Loan creation. Entity methods
prevent rejected or cancelled requests from later becoming approved and prevent partial
approval quantities from remaining pending.

### Acceptance criteria

- Valid requests start `PENDING` at the supplied clock instant.
- Past starts and same-day ranges are accepted; end-before-start is rejected.
- Blank optional details become absent.
- Requests never contain Equipment IDs or reserve equipment.
- Approval records exactly one valid approved quantity and becomes terminal.
- Only the requesting Member ID can cancel a pending request.
- Invalid or repeated transitions preserve every prior field value.

## Major Feature 4: Individual Loans and Member Reports

**Behavior:** Represent one assigned item per Loan, independent return/loss paths, advisory
member evidence, Exco completion paths, and non-mutating overdue detection.

**Mapped requirements:** `F2.4`, `F6.1`, `F6.2.2`-`F6.2.4`, `F6.4`, `F6.5`, `F7`, `F8`,
`F9.3.4`, `F9.3.7`, `F9.3.8`, `F9.3.15`.

### `LoanId`, `LoanStatus`, and `ReportedReturnCondition`

- **Responsibility:** Identify an individual allocation and constrain its loan/report states.
- **Interface:** Typed Loan ID; `ON_LOAN`, `RETURN_PENDING`, `LOST_PENDING`, `COMPLETED`;
  and advisory `GOOD`/`DAMAGED` return condition.
- **Collaborators:** `Loan`, report entities, and later Loan repositories.

### `Loan`

- **Responsibility:** Own the lifecycle and immutable dates of one Member/EquipmentItem
  assignment.
- **Interface:** `start(..., Clock)`, separate return and loss submission methods, separate
  return and loss completion methods, `isOverdue(LocalDate)`, and read-only accessors.
- **Collaborators:** Source request, Member, and EquipmentItem through typed IDs; reports and
  workflow services later.
- **State and data:** Immutable IDs, immediate start `Instant`, immutable end `LocalDate`,
  current status, and optional reported return condition.
- **Failure behavior:** Rejects repeat submissions, completion through the wrong pending
  branch, cancellation, or end-date modification. Overdue checks never mutate status.

### `DamageImageReference`

- **Responsibility:** Carries validated metadata for an application-managed evidence file.
- **Interface:** Relative storage key, `JPEG`/`PNG` format, byte size, and 5 MiB maximum.
- **Collaborators:** `DamageReport` and the later image storage service.
- **Failure behavior:** Rejects blank, absolute, or traversal-containing keys, unsupported
  metadata, and sizes outside `1..5 MiB`.

### `DamageReport` and `LossReport`

- **Responsibility:** Preserve immutable advisory evidence keyed one-to-one by Loan ID.
- **Interface:** Damage report accepts Loan ID, image reference, and description; loss report
  accepts Loan ID and description.
- **Collaborators:** Loan workflow services and later report repositories.
- **State and data:** Trimmed nonblank descriptions. A good return has no DamageReport; its
  advisory condition is stored on Loan.
- **Failure behavior:** Rejects missing required evidence or descriptions. Repository
  uniqueness later prevents multiple reports of one kind for a Loan.

### Interactions and data flow

Approval services later create one Loan per allocated EquipmentItem and copy the request end
date. Return submission records `GOOD` or `DAMAGED`; damaged submission additionally stores a
DamageReport. Loss submission stores a LossReport through a separate path. The Loan and item
must transition together at the service boundary in phase 2. Exco verification completes the
matching pending branch and applies the authoritative item outcome independently of the
Member's advisory report.

### Acceptance criteria

- Each Loan refers to exactly one source request, Member, and EquipmentItem.
- Loan start uses the supplied clock and status begins `ON_LOAN`.
- Return and loss are separate, one-way lifecycle branches.
- One Loan's transitions do not affect another Loan instance.
- Overdue is true only for `ON_LOAN` after the end date and never changes status.
- Damage and loss reports reject incomplete evidence.
- A reported condition does not alter EquipmentItem condition in item 1.

## Cross-Cutting Verification

Update `build.gradle` with:

- JUnit BOM `5.14.3`;
- `org.junit.jupiter:junit-jupiter`;
- `org.junit.platform:junit-platform-launcher`; and
- `useJUnitPlatform()` on the Gradle test task.

Use only JUnit Jupiter assertions; the pure domain model does not need Mockito or JavaFX test
infrastructure. Test methods follow the SE-EDU pattern
`featureUnderTest_testScenario_expectedBehavior`.

Create these test files:

```text
src/test/java/clubstock/domain/account/
├── ExcoAccountTest.java
├── MemberIdTest.java
├── MemberTest.java
└── PasswordHashTest.java
src/test/java/clubstock/domain/equipment/
├── EquipmentIdTest.java
├── EquipmentItemTest.java
├── EquipmentTypeIdTest.java
├── EquipmentTypeNameTest.java
└── EquipmentTypeTest.java
src/test/java/clubstock/domain/request/
├── LoanRequestIdTest.java
└── LoanRequestTest.java
src/test/java/clubstock/domain/loan/
├── LoanIdTest.java
└── LoanTest.java
src/test/java/clubstock/domain/report/
├── DamageImageReferenceTest.java
├── DamageReportTest.java
└── LossReportTest.java
```

Verification commands are `./gradlew test`, `./gradlew build`, and `./gradlew javadoc`.
The test task must execute Jupiter tests rather than succeed with `NO-SOURCE`. No JavaFX or
packaged-JAR smoke test is required because this phase does not alter UI or resources.

## Deferred Shared-Backend Rules

The following rules require repository or multi-entity knowledge and are deliberately left
for phase 2 rather than incompletely enforced by one entity:

- global identifier and EquipmentType-name uniqueness;
- existence of referenced Members and EquipmentTypes;
- approval selection validation, item allocation, Loan creation, and stock-exhaustion
  rejection as one operation;
- one unresolved Loan per EquipmentItem;
- one damage or loss report per Loan;
- role authorization and ownership at service entry points;
- protected Member, EquipmentType, and EquipmentItem removal;
- application-managed image copying and content inspection; and
- shared persistence and transaction boundaries.

## Delivery Issues

### `SLICE-001` — Establish account identities and credential setup state

#### Context

ClubStock needs stable Member identity and an explicit first-run Exco credential state before
authentication, requests, or loans can safely reference accounts.

#### Requirements

- `F1.1.1`, `F1.1.2` — Represent the singleton Exco account and initial password setup.
- `F1.2.1`, `F1.2.2`, `F1.2.4` — Represent Exco-created Member accounts and credentials.
- `F1.3.2` — Support the agreed editable Member fields.
- `F9.3.1` — Preserve unique Member identity for later repository enforcement.

#### Scope

- Record the account-related phase 0 decisions in `docs/ProjectRequirements.md`.
- Configure JUnit Jupiter and the Gradle test runner.
- Add `MemberId`, `PasswordHash`, `Member`, and `ExcoAccount` under
  `clubstock.domain.account`.
- Add mirrored tests for normalization, validation, identity, permitted edits, and Exco
  first-run setup.

#### Out of scope

- Password hashing or verification implementation.
- Login UI, sessions, logout, reset, or repository-wide Member uniqueness.
- Member removal checks against requests or loans.

#### Components

- **Gradle test configuration:** Makes the first real domain tests discoverable in local and
  CI builds.
- **MemberId:** Supplies immutable, case-sensitive Member/login identity.
- **PasswordHash:** Carries an opaque encoded hash without accepting plaintext.
- **Member:** Owns validated name and replaceable credential under immutable identity.
- **ExcoAccount:** Represents missing-initial-credential and completed-setup states.

#### Acceptance criteria

- [ ] JUnit Jupiter tests execute through `./gradlew test`.
- [ ] Typed Member IDs reject null/blank values and preserve case.
- [ ] Member name and password hash edits cannot change Member ID.
- [ ] Invalid edits leave the existing account unchanged.
- [ ] A first-run Exco account requires setup, accepts it once, and rejects repetition.
- [ ] Public code follows the SE-EDU Java standard and passes Javadoc generation.

#### Test scenarios

- Construct IDs with valid, padded, blank, null, and case-variant input.
- Create a Member, update each editable field, and attempt invalid replacements.
- Create a first-run Exco account, complete setup, and repeat the setup call.

#### Dependencies

- None.

#### Traceability

- Design document: `docs/plans/core-domain-model.md`
- Behavior slice: `SLICE-001`

<!-- plan-to-docs:core-domain-model:SLICE-001 -->

### `SLICE-002` — Model equipment catalogue and physical-item lifecycle

#### Context

Members request categories while Exco manages physical inventory. The domain must preserve
that distinction and prevent invalid local condition/availability transitions.

#### Requirements

- `F2.1`, `F2.2` — Represent EquipmentTypes and individual EquipmentItems.
- `F3.2.1`-`F3.2.7` — Model inventory states, release, and lost/damaged restrictions.
- `F9.2.2`, `F9.2.3`, `F9.2.10` — Prevent invalid allocation and release states.
- `F9.3.2`, `F9.3.6`, `F9.3.9`, `F9.3.10` — Preserve equipment identity and allocation
  invariants.

#### Scope

- Record EquipmentType and new-item phase 0 decisions in `docs/ProjectRequirements.md`.
- Add equipment IDs, name value, state enums, EquipmentType, and EquipmentItem under
  `clubstock.domain.equipment`.
- Implement guarded per-item lifecycle methods and mirrored tests.

#### Out of scope

- Equipment repositories, available-quantity calculation, approval orchestration, and UI.
- Global type-name/Equipment-ID uniqueness.
- Protected hard deletion and checks for unresolved Loans.

#### Components

- **EquipmentTypeId/EquipmentId:** Keep category and physical identities distinct.
- **EquipmentTypeName:** Supplies display text and case-insensitive comparison key.
- **EquipmentType:** Owns rename and offered/unoffered state.
- **EquipmentItem:** Owns condition and availability with guarded lifecycle changes.

#### Acceptance criteria

- [ ] New EquipmentTypes are unoffered and new EquipmentItems are `GOOD`/`UNAVAILABLE`.
- [ ] EquipmentType and EquipmentItem IDs are different Java types.
- [ ] Type names are trimmed and expose a case-insensitive comparison key.
- [ ] Only an available item can be allocated.
- [ ] Pending return/loss handling makes an allocated item unavailable without changing its
  condition.
- [ ] Verified good, damaged-available, damaged-unavailable, and lost outcomes are
  representable.
- [ ] A lost item cannot become available.

#### Test scenarios

- Exercise the normal create, release, allocate, hold, and verification sequence.
- Verify both Exco choices for a damaged item.
- Attempt allocation before release, repeated allocation, invalid verification, and lost-item
  release; confirm no partial mutation.

#### Dependencies

- `SLICE-001` — Uses its JUnit/Gradle test foundation.

#### Traceability

- Design document: `docs/plans/core-domain-model.md`
- Behavior slice: `SLICE-002`

<!-- plan-to-docs:core-domain-model:SLICE-002 -->

### `SLICE-003` — Implement the LoanRequest lifecycle

#### Context

A LoanRequest must capture a Member's request for a quantity of one EquipmentType without
reserving or identifying physical items, and must permit only the documented terminal
transitions.

#### Requirements

- `F2.3` — Define LoanRequest identity, fields, and states.
- `F4.1`, `F4.2` — Validate and timestamp submission without reserving inventory.
- `F4.3.4`-`F4.3.7` — Enforce owner-only pending cancellation.
- `F5.1.2`, `F5.1.6`-`F5.1.9`, `F5.2.1`, `F5.2.2`, `F5.4` — Constrain approval and rejection.
- `F9.3.3`, `F9.3.5`, `F9.3.11`, `F9.3.13` — Preserve request identity and terminal states.

#### Scope

- Record requested-date and optional-text decisions in `docs/ProjectRequirements.md`.
- Add `LoanRequestId`, `LoanRequestStatus`, and `LoanRequest` under
  `clubstock.domain.request`.
- Implement deterministic submission and guarded approve, reject, and cancel transitions.
- Add mirrored tests for validation, timestamps, ownership, and state immutability.

#### Out of scope

- Member/EquipmentType existence checks.
- Available-stock validation, item selection, Loan creation, authorization, and automatic
  rejection of competing requests.
- Repository persistence and Exco queue ordering.

#### Components

- **LoanRequestId/LoanRequestStatus:** Supply identity and the closed lifecycle state set.
- **LoanRequest:** Owns request data, submission time, status, and approved quantity.

#### Acceptance criteria

- [ ] Submission captures `requestedAt` from a supplied `Clock` and starts `PENDING`.
- [ ] Quantity is positive and end date is not before start date.
- [ ] Past starts and equal start/end dates are accepted.
- [ ] Blank optional details normalize to absence.
- [ ] A pending request references only Member and EquipmentType IDs.
- [ ] Approval accepts only `1..requestedQuantity` and records the exact approved quantity.
- [ ] Only the requesting Member ID can cancel a pending request.
- [ ] Approved, rejected, and cancelled requests reject further transitions without mutation.

#### Test scenarios

- Submit valid future, past, and same-day requests against a fixed clock.
- Reject zero/negative quantity and reversed dates.
- Approve full and partial quantities; reject zero, excessive, and repeated approval.
- Cancel as owner and non-owner; try cancellation after every terminal state.

#### Dependencies

- `SLICE-001` — Requires `MemberId` and the test foundation.
- `SLICE-002` — Requires `EquipmentTypeId`.

#### Traceability

- Design document: `docs/plans/core-domain-model.md`
- Behavior slice: `SLICE-003`

<!-- plan-to-docs:core-domain-model:SLICE-003 -->

### `SLICE-004` — Model individual Loan return, loss, and report states

#### Context

Each approved EquipmentItem needs its own Loan so Members can return or report items
independently and Exco can later apply an authoritative outcome.

#### Requirements

- `F2.4`, `F6.1`, `F6.2.2`-`F6.2.4` — Represent one immediate Loan per assigned item.
- `F6.4`, `F6.5` — Detect overdue Loans without changing status and prohibit cancellation.
- `F7` — Model separate return/damage/loss submission paths.
- `F8` — Model Exco completion paths and authoritative outcomes.
- `F9.3.4`, `F9.3.7`, `F9.3.8`, `F9.3.15` — Preserve identity and independent Loan lifecycle.

#### Scope

- Record overdue and damage-image phase 0 decisions in `docs/ProjectRequirements.md`.
- Add Loan identity/state/report-condition types and `Loan` under `clubstock.domain.loan`.
- Add damage image metadata, DamageReport, and LossReport under
  `clubstock.domain.report`.
- Implement deterministic Loan start, separate submission/completion branches, and overdue
  checks with mirrored tests.

#### Out of scope

- Approval orchestration and creating multiple Loans atomically.
- File selection, MIME sniffing, managed image copying, persistence, and report uniqueness.
- Member/Exco authorization and multi-entity Loan/EquipmentItem transactions.
- UI presentation and completed-record retention policy.

#### Components

- **LoanId/LoanStatus/ReportedReturnCondition:** Define individual Loan identity and states.
- **Loan:** Owns immutable assignment/dates, lifecycle, and advisory return condition.
- **DamageImageReference:** Validates managed evidence metadata and size policy.
- **DamageReport/LossReport:** Preserve required advisory evidence keyed by Loan ID.

#### Acceptance criteria

- [ ] A Loan starts immediately from the supplied `Clock` with status `ON_LOAN`.
- [ ] Source request, Member, EquipmentItem, and end date cannot be changed.
- [ ] Return and loss follow separate one-way pending branches.
- [ ] Completion is allowed only from the corresponding pending state.
- [ ] Overdue is true only when an `ON_LOAN` Loan is past its end date and does not mutate it.
- [ ] Damage evidence accepts only managed JPEG/PNG references from 1 byte through 5 MiB.
- [ ] Damage and loss descriptions are required and trimmed.
- [ ] Invalid or repeated operations leave Loan and report data unchanged.

#### Test scenarios

- Start multiple Loans from one request and transition one while verifying the other remains
  unchanged.
- Exercise good return, damaged return, and lost paths through completion.
- Attempt wrong-branch and repeated transitions.
- Test before, on, and after the end-date boundary across active and pending statuses.
- Reject blank/absolute/traversal image keys, unsupported sizes, and missing descriptions.

#### Dependencies

- `SLICE-001` — Requires `MemberId` and the test foundation.
- `SLICE-002` — Requires `EquipmentId` and equipment state concepts.
- `SLICE-003` — Requires `LoanRequestId`.

#### Traceability

- Design document: `docs/plans/core-domain-model.md`
- Behavior slice: `SLICE-004`

<!-- plan-to-docs:core-domain-model:SLICE-004 -->

## Traceability

| Slice ID | Requirements | Major feature | GitHub issue | State |
| --- | --- | --- | --- | --- |
| `SLICE-001` | `F1.1`, `F1.2`, `F1.3.2`, `F9.3.1` | Account identities and credential state | [#3](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/3) | Open |
| `SLICE-002` | `F2.1`, `F2.2`, `F3.2`, `F9.2`, `F9.3` | Equipment catalogue and item lifecycle | [#4](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/4) | Open |
| `SLICE-003` | `F2.3`, `F4`, `F5`, `F9.3` | LoanRequest lifecycle | [#5](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/5) | Open |
| `SLICE-004` | `F2.4`, `F6`, `F7`, `F8`, `F9.3` | Individual Loans and reports | [#6](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/issues/6) | Open |

## Assumptions and Open Questions

- The exact PBKDF2 work factor, salt length, and encoded format are selected with the
  authentication implementation; the core domain intentionally treats the result as opaque.
- The backend will select and consistently supply the application timezone when converting
  `Instant` to the `LocalDate` passed to overdue checks.
- ID generation and all global uniqueness constraints are backend concerns; this model only
  validates identifier shape.
- Report records are immutable entities keyed by Loan ID. Database/repository constraints
  will enforce one report of each applicable kind per Loan.
- `docs/ProjectRequirements.md` is updated as each behavior slice lands so implemented policy
  is no longer listed as unspecified.
- GitHub duplicate discovery checked all open and closed issues before the creation preview;
  the repository contained no existing issues and therefore no matching traceability markers.
- The existing `enhancement` label clearly applies to all four proposed behavior slices. No
  other label is applied because the remaining labels are narrower or ambiguous.
