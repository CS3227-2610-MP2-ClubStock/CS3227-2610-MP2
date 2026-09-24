# SLICE-005 — Member equipment catalogue (#27)

- Owner: Keith
- Milestone: B
- Status: implementation plan; no catalogue code or demo account has been created by this document.

## Integration with Darryl's #26 inventory work

This is the contract to settle with Darryl before the catalogue service is wired into the
application. The [parallel role roadmap](parallel-role-implementation.md) assigns the shared
`AvailabilityPolicy` implementation to Darryl in SLICE-004 / #26 and the Member catalogue to
Keith in SLICE-005 / #27. A complete #26 UI is not a start prerequisite for Keith's service or
screen work, but #27 cannot claim integrated stock counts until it uses the real #26 policy.

| #26 provides | #27 consumes or verifies |
| --- | --- |
| Persisted type offering, renaming and deletion, plus item add, release and retirement | Load offered types and read their current names from the shared repositories; do not maintain a Member-side copy. |
| One `AvailabilityPolicy` usable inside an existing `UnitOfWork` | Request the count once per offered type within the catalogue's single read transaction; the policy must not start another transaction. |
| Eligibility rule: count non-retired items of the type whose availability is `AVAILABLE` | Display the returned count, including zero. Do not add a `GOOD`-condition filter: an Exco-cleared `DAMAGED` item can be available. |
| Exco inventory operations that commit changes to the shared SQLite database | Log out, use Exco to change inventory, log back in as the fixture Member and reopen the catalogue to observe the committed result. |

Proposed Java contract for the shared boundary:

```java
package clubstock.application.inventory;

import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentTypeId;

public interface AvailabilityPolicy {
    int countAvailable(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId);
}
```

Use the existing `EquipmentItemRepository.findByType(...)` and `EquipmentItem` state as the
behavioral reference. The interface can land with Keith's service so it compiles against a
test stub; Darryl supplies the production implementation in #26. Confirm the package and
signature with Darryl before either branch publishes the contract, to avoid two competing
policy APIs. The production implementation is the only source of the counting rule for Exco
inventory, Member catalogue, later request previews, and allocation. It should accept the
caller's unit of work and return zero when a type has no eligible items. No schema change is
needed for this slice; the existing `(equipment_type_id, availability, is_retired)` index
supports a later repository count optimization if #26 needs it.

The integration handoff is: agree the contract; Keith publishes the Member query and UI
against a policy stub; Darryl publishes the real policy and inventory operations; Keith
injects the real policy in `ApplicationContext`; then both sides perform the role-switch
acceptance scenario on one database. The interface itself may be added by either branch once,
but the counting implementation belongs to #26.

## Intent and boundaries

SLICE-005 covers `F3.1`, `F3.3`, `F9.1.1`, `N1`, and `N2` in
[ProjectRequirements](../ProjectRequirements.md), plus `MEM-04` and `MEM-05` in
[MemberSpec](../MemberSpec.md). A signed-in Member sees only offered EquipmentTypes, each
type's current available quantity, and a clear zero-stock state. The catalogue never returns
or renders an individual Equipment ID. A type with zero available items stays in the list.

The Member home screen becomes the catalogue landing page, as agreed for this plan. Entering
the page after login loads a fresh snapshot; an explicit Refresh action loads another snapshot
while the page remains open. The type ID remains in the service DTO for the later request
flow, but the current UI displays only name and quantity. Keep a stable name-then-type-ID
order so refreshes do not rearrange equal-looking entries unexpectedly.

The following work belongs to other slices: Exco account administration (#25), Exco
inventory screens and the policy implementation (#26), and request creation plus the
submission-time zero-stock warning (#28). The catalogue must not disable a zero-stock type
as unrequestable or add a premature request form. No production Member self-registration,
automatic Member seed, new database migration, or second business-data store is proposed.

## Increment 1 — Temporary Member account for development

**Result:** The existing Member login can be exercised immediately, before Exco's #25
account-creation UI exists. This is disposable development data, not application bootstrap.

1. Add a test-source `CatalogDemoSeeder` with a `main` method. It creates an isolated database
   through `ApplicationContext.create(...)` under `build/clubstock-demo`, then uses
   `transactionManager().write(...)` and `MemberRepository.insert(...)` to add one active
   Member. Construct the account with `Member.create(new MemberId("demo-member"),
   "Demo Member", hash)` and `Pbkdf2PasswordHasher`; never insert a literal or test-double
   hash into SQLite. The demonstration password is `demo-password` (at least eight
   characters), and its plaintext is never stored or printed by the app.
2. Make the seeder repeatable: if `demo-member` is already present, leave that row alone;
   reject any unexpected fixture setup failure rather than replacing the database. Restrict
   the task to its fixed `build/clubstock-demo` directory. `build/` is already ignored by
   Git, so cleanup is `./gradlew clean` or removal of that one directory.
3. Add `seedCatalogDemo` as a Gradle `JavaExec` task using the test runtime classpath and
   depending on `testClasses`. Add `runCatalogDemo` using the main runtime classpath,
   depending on `seedCatalogDemo`, and setting `clubstock.dataDir` to the same directory.
   Keep the ordinary `run` task and production data-directory resolution unchanged.
4. Run `./gradlew runCatalogDemo` and sign in as `demo-member` / `demo-password`. At this
   increment the existing Member home placeholder is sufficient to prove that the account
   works. Avoid putting the fixture in `${user.home}/.clubstock` or in tracked files.

After #25 exists, the normal acceptance path uses an account created by Exco. The fixture
remains useful for repeatable development and does not count as proof of Exco account
creation.

## Increment 2 — Member-safe catalogue query

**Result:** A Member-authorized service returns a complete, immutable catalogue snapshot
without leaking physical-item details.

1. Add `MemberCatalogService` under `clubstock.application.catalog` with a no-argument
   `listOfferedTypes()` query. Inject `TransactionManager`, `SessionManager`, and the shared
   `AvailabilityPolicy`. Require a Member session at the service boundary, including calls
   that bypass the UI. In one `TransactionManager.read(...)` callback, verify that the
   principal's Member record still exists and is active, call
   `EquipmentTypeRepository.findAll()`, filter `isOffered()`, count each type through the
   policy using the same `UnitOfWork`, and return `List.copyOf(...)`.
2. Add an immutable `CatalogType` record with exactly `EquipmentTypeId equipmentTypeId`,
   `String name`, and `int availableQuantity`. Reject null identity/name and negative counts
   in its constructor. Do not expose `EquipmentItem`, `EquipmentId`, condition, or individual
   availability. Map the type's current name to a string before leaving the read transaction.
3. Sort by `EquipmentTypeName.comparisonKey()` and then type ID. Include offered types even
   when the policy returns zero. Return an empty list when none are offered. Preserve the
   existing application error convention for unauthorized or failed reads; never return a
   partial list after a failure.
4. Extend the demo seeder with valid domain-created inventory: one offered type with two
   released items, one offered type with zero available items, and one unoffered type. Use
   `EquipmentType.create(...).offer()`, `EquipmentItem.create(...).release()` where
   applicable, and the existing repositories inside one write transaction. Stable fixture
   IDs and existence checks make repeat runs idempotent. Do not add a second count
   implementation to the Member service or controller.

The service can be developed with a stub `AvailabilityPolicy` before #26 merges. A test
stub only establishes the service's filtering, privacy, authorization, and transaction use;
the real policy is required for count acceptance.

## Increment 3 — Catalogue in the Member home

**Result:** A successful Member login opens a usable catalogue with safe loading states.

1. Replace the placeholder content in `member-home.fxml` with a catalogue heading, a
   Refresh button, a scrollable list or table of type names and available quantities, an
   empty-state label, and an error label. The row for zero stock must say `0 available` and
   have a visible text status; color alone is insufficient. Do not render a physical
   Equipment ID or an item selection control. Retain the shell header and logout action.
2. Inject `MemberCatalogService` into `MemberHomeController` through `UiComposition`. Load
   the service snapshot on `initialize()` so every new route entry sees current data. Make
   Refresh call the same loader. Replace existing rows only after a successful query; on
   failure clear any old rows, show a safe message, and keep Refresh available. Show
   `No equipment is currently offered` for a successful empty list. Keep JavaFX layout and
   state changes in the controller; all role and count rules remain in the service/policy.
3. Expose the service from `ApplicationContext`, passing its existing database transaction
   manager and session manager. Register the updated controller in `UiComposition` and pass
   the context-owned service from `ClubStockApplication`. Preserve the existing `MEMBER_HOME`
   route guard, member login destination, and logout behavior. Use the shared CSS for
   zero-stock and error readability.

If #26 has not yet landed, keep the query and screen changes staged against the agreed
contract and use a stub for controller-level checks. Finish production composition only
after the real policy is available; no fixture-only availability provider should be wired
into the normal application.

## Increment 4 — Real #26 integration and acceptance

**Result:** The catalogue reads the same committed stock state that Exco manages.

1. Connect Darryl's production `AvailabilityPolicy` to `ApplicationContext`. Remove any
   temporary production wiring used during development, while retaining test stubs for
   isolated service checks. Inspect both Member and Exco call sites of the shared policy to
   confirm they count the same eligible items and neither starts a nested transaction.
2. On one temporary SQLite database, create or offer types and add/release/retire items
   through #26. Switch to Member by logging out and in. Confirm each offered type's count,
   confirm unoffered types are absent, and reopen or refresh the Member screen after further
   Exco changes. The Member result must follow committed changes without restarting the
   application or copying inventory into Member-owned storage.
3. With #25 available, repeat the login portion using an Exco-created Member account.
   Check that a signed-out caller and an Exco caller cannot invoke the catalogue service,
   even if they bypass route navigation.

## Expected file changes

| Increment | Files |
| --- | --- |
| 1 | New `src/test/java/clubstock/fixture/CatalogDemoSeeder.java`; `build.gradle` for development-only tasks. |
| 2 | New `src/main/java/clubstock/application/catalog/MemberCatalogService.java` and `CatalogType.java`; the agreed policy interface under `clubstock.application.inventory` if #26 has not already added it; new `src/test/java/clubstock/application/catalog/MemberCatalogServiceTest.java`; extend the fixture seeder. |
| 3 | `src/main/java/clubstock/ui/controller/MemberHomeController.java`, `src/main/resources/clubstock/ui/view/member-home.fxml`, `src/main/resources/clubstock/ui/clubstock.css`, `src/main/java/clubstock/ApplicationContext.java`, `src/main/java/clubstock/ui/UiComposition.java`, and `src/main/java/clubstock/ClubStockApplication.java`; update `src/test/java/clubstock/ui/FxmlResourceTest.java` and add focused controller state checks where practical. |
| 4 | Composition changes needed to construct Darryl's real policy; integration checks using the existing SQLite test infrastructure. No migration or domain-state changes are expected. |

## Focused verification

- Use temporary SQLite fixtures for offered/unoffered and positive/zero-stock cases. Include
  `AVAILABLE`, `UNAVAILABLE`, and retired items, plus an available damaged item, so the
  shared eligibility rule is checked without accidentally limiting counts to `GOOD`.
- Assert `CatalogType` exposes exactly type identity, name, and count; inspect the rendered
  FXML/controller path for accidental physical IDs. Assert wrong-role, signed-out, missing,
  and inactive Member calls fail without returning data.
- Check successful empty rendering, service-error rendering, zero-stock text, initial load,
  Refresh, and re-entry. Use the existing `FxmlResourceTest` for route/controller resource
  structure and a JavaFX smoke check for actual screen loading if the local runtime supports
  it.
- Run the focused catalogue tests, then the project Gradle test/build checks when #26 is
  integrated. Record which checks actually ran. Close #27 only after the real #26 policy and
  role-switch scenario pass; the isolated demo fixture alone is not joint acceptance.

## References

- [SLICE-005 and milestone B](parallel-role-implementation.md)
- [Shared application design](shared-application-design.md)
- [Project requirements](../ProjectRequirements.md)
- [Shared domain rules](../Shared.md)
- [Member specification](../MemberSpec.md)
- [Exco specification](../ExcoSpec.md)
