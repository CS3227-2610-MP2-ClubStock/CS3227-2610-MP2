# ClubStock High-Level Project Outline

## Summary

Build the project from the inside out: define the core domain first, establish
the shared backend around it, and only then create the JavaFX application
structure. After those unassigned foundations, Keith owns Member-specific
features and Darryl owns Exco-specific features.

`ProjectRequirements.md`, `ExcoSpec.md`, and `MemberSpec.md` are authoritative
where `ProjectDescription.md` differs.

## Ordered Project Phases

| Order | Phase | Main outcome | Assignee |
| ---: | --- | --- | --- |
| 0 | Requirements decisions | Resolve unspecified choices that block later work, especially authentication identifiers, EquipmentType provisioning, editable Member fields, date rules, and damage-image constraints. | Unassigned |
| 1 | Core domain model | Introduce accounts and identities, `EquipmentType`, `EquipmentItem`, `LoanRequest`, `Loan`, damage reports, and loss reports, together with their states, relationships, invariants, and lifecycle rules. | Unassigned |
| 2 | Shared backend foundation | Establish the shared source of truth, persistence, repositories, shared business operations, authorization boundaries, and cross-role state consistency. | Unassigned |
| 3 | JavaFX application structure | Create the application shell, role-specific navigation, controller boundaries, shared styling, startup flow, and distributable application structure. | Unassigned |
| 4 | Authentication and Member accounts | Add Exco first-login password setup, Exco Member-account management, and Member login using an Exco-created account. | Darryl: Exco setup and Member management; Keith: Member login |
| 5 | Equipment inventory | Let Exco view and manage individual items, including condition, availability, release, and protected removal. | Darryl |
| 6 | Member equipment discovery | Let Members browse EquipmentTypes and calculated available quantities without seeing unassigned Equipment IDs, including the zero-stock warning. | Keith |
| 7 | Loan request submission and review | Add Member request creation, history, and pending cancellation, followed by the Exco pending-request queue and manual rejection. | Keith: submission, own-request view, cancellation; Darryl: pending queue and rejection |
| 8 | Approval and allocation | Let Exco approve pending requests by assigning available items, creating one Loan per item, and applying stock-exhaustion rejection. Show approved quantities and assigned IDs to the Member. | Darryl: approval and allocation; Keith: Member-facing results |
| 9 | Active loans and overdue visibility | Display allocated items as independent Loans and visibly mark overdue `ON_LOAN` records without changing their status. | Keith: Member view; Darryl: Exco view |
| 10 | Member return and loss reporting | Let Members return individual items as apparently good or damaged, or report them lost, while leaving authoritative assessment to Exco. | Keith |
| 11 | Exco verification | Let Exco review pending reports, determine authoritative condition and availability, and complete the affected Loans. | Darryl |
| 12 | Cross-role integration and acceptance | Validate authorization, protected removals, shared-state consistency, packaging, and the complete request-to-resolution workflow. | Keith and Darryl |

## Foundation Sequence

1. Model accounts and unique identities.
2. Model EquipmentTypes and individual EquipmentItems.
3. Add equipment condition and availability states.
4. Model LoanRequests and their lifecycle.
5. Model individual Loans and their lifecycle.
6. Add return, damage, and loss reporting concepts.
7. Establish shared storage and business operations around those models.
8. Add application startup, authentication routing, and separate Member and
   Exco JavaFX areas.
9. Build role-specific features in workflow order.

## Acceptance Scenarios

- Exco completes first-login setup, creates a Member, and the Member logs in.
- Exco adds inventory; Members see only EquipmentTypes and accurate available
  quantities.
- A Member submits, views, and cancels a pending request, including a warned
  but valid zero-stock request.
- Exco rejects or partially approves a request and the Member sees the correct
  outcome.
- Approval creates one independently managed Loan per allocated item.
- Exhausting available stock rejects other currently pending requests for that
  type.
- Both roles see overdue Loans without an automatic status change.
- Member return, damage, and loss reports reach Exco verification and produce
  the required final states.
- Invalid, unauthorized, or repeated operations do not partially alter shared
  state.

## Assumptions and Boundaries

- The requested specifications take precedence over conflicting overview text.
- Core domain, backend foundation, and JavaFX structure have no individual
  assignee.
- "JavaFX application structure" means the UI shell and role navigation;
  feature screens follow in their respective phases.
- Unspecified product policies are settled in Phase 0 instead of being assumed
  during implementation.
- Reservations, self-registration, extensions, active-loan cancellation,
  fines, automated overdue follow-up, additional Exco accounts, and long-term
  history remain outside scope.
