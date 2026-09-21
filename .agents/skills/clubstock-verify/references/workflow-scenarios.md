# Workflow scenarios

Use only cases affected by the task. These are suggested fixtures and test sequences, not additional requirements. Confirm expectations against the current [requirements](../../../../docs/ProjectRequirements.md) and [shared specification](../../../../docs/Shared.md). Resolve affected document conflicts before asserting an expected outcome.

Create fixtures through the existing test setup or disposable storage. Choose dates relative to an explicit test clock or the project's established date policy; do not invent past-date or timezone requirements.

## Requests and allocation

Use two Members, two EquipmentTypes, and multiple items of one type. Explicitly seed item condition and availability rather than assuming new-item defaults. Seed at least one unavailable item and several pending requests to distinguish eligible allocations and automatic rejection.

| Scenario | Action and expected observation | Requirement |
| --- | --- | --- |
| Request without reservation | Submit a valid request. It starts PENDING with requestedAt recorded; item availability and available count stay unchanged. | F4.2 |
| Zero stock | In a fixture with zero AVAILABLE items, show the warning before submission. A valid request can still become PENDING. The overview conflicts with this rule; clarify affected behavior first. | F3.1.4-F3.1.6 |
| Input validity | Exercise non-positive quantity, nonexistent Member/type, and end before start. Reject invalid input without adding a request. Do not invent additional date bounds. | F4.1 |
| Partial approval | Request three items and assign two eligible distinct items while one other remains AVAILABLE. Record approved quantity two and create exactly two ON_LOAN Loans. No unfulfilled portion remains pending. | F5.1-F5.2 |
| Exhausting approval | Approve the final available item. Other requests already PENDING for that type become REJECTED; other types' requests are unchanged. A later zero-stock request remains allowed and is not automatically rejected merely on creation. | F5.3 |
| Availability restored | Return and verify an item after stock exhaustion. Previously rejected requests remain REJECTED. | F5.3.3 |
| Invalid allocation | Attempt an unavailable item, wrong type, duplicate ID, too many items, empty selection, or a second approval of the same request. Reject with no duplicate Loans or partial mutation. | F5.1, F9.2, F9.3.8 |
| Cancellation and ownership | Only the requesting Member can cancel a PENDING request. The cancelled request cannot later be approved; active Loans cannot be cancelled. | F4.3, F6.5 |
| ID visibility | Before allocation, Member browsing exposes types and counts only. After allocation, the assigned Member can see the assigned IDs; another Member cannot see those IDs. | F3.3, F9.1.1 |

## Individual returns and loss

Start each branch with a fresh fixture containing two ON_LOAN items assigned through the same request. Return or report only one so the other proves that handling is independent.

| Scenario | Action and expected observation | Requirement |
| --- | --- | --- |
| Good return submitted | Submit a normal return. The selected Loan becomes RETURN_PENDING, its item becomes UNAVAILABLE, and authoritative condition is unchanged. The sibling Loan stays ON_LOAN. | F6.2.4, F7.1 |
| Damaged return validation | Omit the image or description. Reject without changing the Loan or item. With both supplied, store the advisory report and enter RETURN_PENDING. | F7.1.2-F7.1.8 |
| Good verification | Exco verifies a RETURN_PENDING item as good. Complete that Loan, set condition GOOD and availability AVAILABLE, and refresh the Member-visible quantity. | F8.2.2, N1.4 |
| Damaged verification | Test Exco choosing AVAILABLE and UNAVAILABLE in separate fixtures. Both complete the Loan and set DAMAGED; only AVAILABLE contributes to stock. | F8.2.3-F8.2.4, F3.2.4 |
| Report versus assessment | Exco verifies a reported-damaged return as good, or a reported-good return as damaged. The verified result determines final condition. | F8.2.5 |
| Loss submitted | Use the separate Report Lost action. Require a description, move the Loan to LOST_PENDING and item to UNAVAILABLE, and retain authoritative condition until confirmation. | F7.2 |
| Loss confirmed | Exco confirms the pending loss. Complete the Loan, set LOST and UNAVAILABLE. An attempt to make that item AVAILABLE must fail. | F8.3.4, F3.2.6 |
| Repeat or unauthorized action | Try another return after a pending/completed report, another Member's Loan, Member self-verification, or repeated Exco confirmation. Reject without changing records or quantities. | F7.1.1, F7.1.9, F8.2.1, F8.3.3 |

## Removal, overdue, and shared views

- Attempt item removal separately while a Loan is ON_LOAN, RETURN_PENDING, and LOST_PENDING; removal must fail in each case (F3.2.5). Member removal must preserve unresolved references; clarify the unresolved-request definition and deletion policy if the task depends on them (F1.3, requirements section 5).
- Under the project's established date policy, display an ON_LOAN Loan whose end date is clearly in the past. Both roles see overdue, while status remains ON_LOAN and no fine, extension, or cancellation occurs (F6.4). Test exact date boundaries only after their policy is defined.
- Perform the relevant action as one role, then navigate to the other role's view of the same data. Check that the changed quantity, request, Loan, or pending verification is reflected without independent data copies (N1).
- In desktop checks, inspect empty states, selection-dependent actions, validation messages, repeated clicks while an operation is pending, resizing, and keyboard access. These are usability checks, not newly mandated product requirements.
