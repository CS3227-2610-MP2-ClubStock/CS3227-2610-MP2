# ClubStock Project Implementation Overview

## Active plan

The [Parallel Member and Exco Implementation Roadmap](plans/parallel-role-implementation.md)
is the active delivery plan for all remaining services, JavaFX screens and final acceptance.
Keith owns Member features and Darryl owns Exco features, including business logic and UI.
Keith owns shared authentication/session services; Darryl owns the JavaFX shell and composition.

The core domain and SLICE-001 SQLite persistence foundation are implemented on the current
baseline. Preserve them. The former shared-backend foundation delivery sequence has been
replaced; the entire backend no longer has to be completed before role screens can begin.
Confirmed technical/product decisions remain in the
[Shared Application Design Reference](plans/shared-application-design.md).

[ProjectRequirements.md](ProjectRequirements.md), [Shared.md](Shared.md),
[MemberSpec.md](MemberSpec.md) and [ExcoSpec.md](ExcoSpec.md) define authoritative behavior.
Flag conflicts before implementing affected behavior. Previously confirmed decisions remain
in force; genuinely unresolved product choices still require clarification.

## Original phase coverage

Phase numbers are retained for requirements/design traceability. They identify feature areas,
not mandatory sequential completion gates. Milestones refer to the active roadmap.

| Phase | Area | Delivery and ownership |
| ---: | --- | --- |
| 0 | Requirements decisions | Retain confirmed decisions; clarify newly encountered gaps before affected implementation |
| 1 | Core domain model | Retain implemented model and [core design](plans/core-domain-model.md) |
| 2 | Shared backend foundation | Retain SLICE-001; deliver remaining services within each owner's features in A–D |
| 3 | JavaFX application structure | A: Darryl owns shell, navigation, controller injection and styling; extend wiring incrementally |
| 4 | Authentication and Member accounts | A: Keith owns shared auth and Member login, Darryl Exco setup/login; B: Darryl Member administration |
| 5 | Equipment inventory | B: Darryl owns inventory services/screens and shared availability policy |
| 6 | Member equipment discovery | B: Keith owns catalogue service/screen; C includes zero-stock submission warning |
| 7 | Loan request submission and review | C: Keith owns submission/own requests/cancellation; Darryl queue/rejection |
| 8 | Approval and allocation | C: Darryl owns approval/allocation; Keith Member results |
| 9 | Active Loans and overdue visibility | C: Keith owns shared Loan queries and Member view; Darryl Exco view |
| 10 | Member return and loss reporting | D: Keith owns reporting services/screens and managed evidence |
| 11 | Exco verification | D: Darryl owns verification services/screens |
| 12 | Cross-role integration and acceptance | Joint checks throughout A–D; E completes acceptance and packaging |

## Integration milestones

| Milestone | Observable result |
| --- | --- |
| A — Usable startup | Role selection, Exco setup/login, Member login and guarded shells/logout |
| B — Accounts and discovery | Exco-created Member can log in and see correct offered inventory counts |
| C — Requests and Loans | Requests cross roles through rejection/approval into individual Loans |
| D — Reporting and verification | Member reports reach Exco and resolve the correct item and Loan |
| E — Final acceptance | Complete workflows, restart persistence and packaged operation pass checks |

Both tracks may overlap milestones. Agree interfaces first and use valid temporary fixtures
while the opposite role's producer workflow is unfinished. Integrate real services at each
checkpoint. Services enforce shared rules through the same domain and SQLite transactions;
UI controllers do not own persistence or duplicate business rules.

## Acceptance and boundaries

Acceptance covers setup/account creation, inventory and catalogue privacy, warned zero-stock
requests, pending cancellation/rejection, partial allocation, stock-exhaustion rejection,
independent Loans, overdue indicators, all report/verification outcomes, protected removals,
authorization and atomic failures. The roadmap specifies the full scenarios and checks.

Reservations, Member self-registration, extensions, active-Loan cancellation, fines,
automated overdue follow-up, additional Exco accounts, lost-item recovery, return-report
reversal, audit trails and long-term history screens remain outside scope.
