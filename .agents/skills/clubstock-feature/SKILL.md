---
name: clubstock-feature
description: Implement or change ClubStock Member and Exco business workflows involving requests, item allocation, loans, inventory, or return verification. Use when shared domain behavior changes, not for purely visual styling.
---

# ClubStock Feature Implementation

Implement a focused feature through the existing shared domain and service layers so both roles observe the same result.

## Find the behavior and integration points

Read [AGENTS.md](../../../AGENTS.md), the [project overview](../../../docs/ProjectDescription.md), [requirements](../../../docs/ProjectRequirements.md), [shared specification](../../../docs/Shared.md), and the affected [Exco](../../../docs/ExcoSpec.md) or [Member](../../../docs/MemberSpec.md) specification. Read both role specifications for a workflow that passes between them.

Inspect the current models, services, repositories, controllers, and tests. Do not assume these layers already exist in a starter application. Reuse established interfaces and persistence choices; introduce only the pieces the requested feature needs.

Map the request to requirement IDs and describe the actor, preconditions, state changes, and cross-role result. Check requirements section 5 for undefined product behavior. Flag document conflicts before implementing the affected behavior and clarify decisions that materially block it. Continue independent work. Visual or internal code-organization choices do not require inventing product policy.

## Keep the domain distinctions

Use the shared specification as the maintained source for states and transitions:

- EquipmentType is the requestable category; EquipmentItem is a physical item with its own ID, condition, and availability.
- LoanRequest records requested quantity and dates; approval can assign fewer items without leaving the remainder pending.
- Loan represents one assigned item. Multiple items approved together produce independently managed Loans.
- A Member's report is advisory. Authoritative condition changes through Exco verification.
- Member and Exco features share services and repositories rather than maintaining separate stores of the same entities.

Follow the intended direction: UI to controller to service to domain/repository. Enforce caller permissions, ownership, and transition preconditions at the shared operation boundary, including calls that bypass the UI.

## Implement consistent transitions

For the affected operation, identify every record and view that must change together. Re-read mutable preconditions before applying the change, using the project's existing consistency mechanism. Choose an implementation that preserves the documented invariants without claiming that a particular database, transaction model, or ID scheme is specified.

Pay particular attention to:

- Approval: unique selected IDs, matching type, current availability, quantity bounds, one Loan per selected item, and matching approved quantity.
- Stock exhaustion: reject other requests pending for that type at the moment approval exhausts availability. This is not a permanent ban on later requests at zero stock.
- Return or loss submission: update only the selected Loan and item's availability, retaining authoritative condition until verification.
- Verification: complete the matching pending Loan and set the final condition and availability together.
- Removal and repeated actions: protect unresolved references, prevent duplicate allocation or resolution, and leave state unchanged when validation fails.

Keep reusable rules in one shared place. Do not create helper copies in both role controllers. Avoid extensions, reservations, recovery transitions, or account policies outside the user's scope and agreed behavior.

## Connect and verify

Update only the necessary callers and refresh their view of shared state after success. For a shared API change, inspect both role call sites even when only one screen was requested.

Use focused tests for substantive rules: the successful transition, a meaningful invalid attempt with no partial changes, caller/ownership boundaries, and the opposite role's observable result when affected. Prefer service tests without starting JavaFX for business logic. Extend the current test stack; check the build before assuming one exists.

Report implemented behavior, relevant requirement IDs, affected integration points, and verification actually performed. Keep unresolved specification questions separate from confirmed defects.
