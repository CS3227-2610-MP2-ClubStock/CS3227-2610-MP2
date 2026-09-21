# Exco Role Specification

## 1. Purpose

This document defines the requirements for the **Exco** role.

All shared domain rules in `Shared.md` apply.

## 2. Authentication

### EXCO-01 — Pre-Created Account
The system shall contain one pre-created Exco account.

### EXCO-02 — First-Login Password Setup
On first login, the Exco user shall be required to set or change the Exco account password.

The system is not required to support creation of additional Exco accounts.

## 3. Member Account Management

### EXCO-03 — View Members
Exco shall be able to view Member accounts.

### EXCO-04 — Create Member Account
Exco shall be able to create a Member account containing at least a unique Member ID, Member name, and password set by Exco.

### EXCO-05 — Edit Member
Exco shall be able to edit Member information supported by the application.

### EXCO-06 — Remove Member
Exco shall be able to remove a Member only when doing so does not corrupt an unresolved Loan or LoanRequest.

## 4. Equipment Inventory

### EXCO-07 — View Individual Equipment
Exco shall be able to view individual EquipmentItems, including Equipment ID, EquipmentType, condition, and availability.

### EXCO-08 — Add Individual Equipment
Exco shall add inventory as individual EquipmentItems with unique Equipment IDs.

### EXCO-09 — Release Equipment
Exco shall be able to make an item `AVAILABLE` when it is suitable for future allocation. Only `AVAILABLE` items contribute to Member-visible available quantity.

### EXCO-10 — Remove Equipment
Exco shall be able to remove an EquipmentItem only when it is not referenced by an unresolved `ON_LOAN`, `RETURN_PENDING`, or `LOST_PENDING` Loan.

### EXCO-11 — Lost Equipment
An item whose authoritative condition is `LOST` must remain `UNAVAILABLE`.

### EXCO-12 — Damaged Equipment
An item whose authoritative condition is `DAMAGED` may be `AVAILABLE` or `UNAVAILABLE`, based on Exco's assessment.

## 5. View Loan Requests

### EXCO-13 — View Pending Requests
Exco shall be able to view pending LoanRequests.

### EXCO-14 — Oldest-First Ordering
Pending requests should be displayed from earliest to latest request creation time. This ordering is informational and does not force first-come-first-served approval.

### EXCO-15 — Request Information
For each request, Exco shall be able to see at least Member, EquipmentType, requested quantity, current available quantity, requested start/end dates, request creation time, and optional details.

The requested start date is informational only and does not reserve stock.

## 6. Approve Loan Request

### EXCO-16 — Approve Pending Only
Only a `PENDING` request may be approved.

### EXCO-17 — Assign Specific IDs
Exco shall select specific `AVAILABLE` EquipmentItems of the requested EquipmentType.

`ON_LOAN` and `UNAVAILABLE` items must not be selectable.

### EXCO-18 — Decide Approved Quantity
Exco may approve fewer items than requested.

The approved quantity must be at least 1, must not exceed the requested quantity, and must not exceed currently available stock.

### EXCO-19 — No-Stock Approval
If there are no `AVAILABLE` items of the requested EquipmentType, the request cannot be approved and Exco must be notified.

### EXCO-20 — Approval Result
When approval is confirmed:

1. LoanRequest becomes `APPROVED`.
2. Approved quantity is recorded.
3. Each selected EquipmentItem changes `AVAILABLE -> ON_LOAN`.
4. One individual `ON_LOAN` Loan is created for each selected item.
5. Assigned Equipment IDs become visible to the Member.

There is no separate `APPROVED` Loan state.

### EXCO-21 — Unfulfilled Quantity
If fewer items are approved than requested, the remaining quantity does not stay pending. The Member must make a new request if they still want more.

### EXCO-22 — Automatic Rejection When Stock Reaches Zero
If an approval causes the available quantity of that EquipmentType to become `0`, the system shall automatically reject every other currently `PENDING` request for that EquipmentType.

Those requests are not reopened automatically later.

Requests submitted afterwards while availability remains `0` are still allowed, with the Member zero-stock warning defined in `Shared.md`.

## 7. Reject Loan Request

### EXCO-23 — Reject Pending Request
Exco may reject only a `PENDING` request:

`PENDING -> REJECTED`

## 8. Active Loans

### EXCO-24 — View Active Loans
Exco shall be able to view active individual Loans and assigned Equipment IDs.

### EXCO-25 — View Overdue Loans
An `ON_LOAN` Loan past its end date shall be visibly marked overdue. It remains `ON_LOAN`. Exco handles follow-up offline.

### EXCO-26 — No Loan Extension
The application does not support extension or editing of a Loan end date after approval.

### EXCO-27 — No Active-Loan Cancellation
Exco must not be able to cancel an `ON_LOAN`, `RETURN_PENDING`, or `LOST_PENDING` Loan.

## 9. Verify Returned Equipment

### EXCO-28 — View Pending Returns
Exco shall be able to view `RETURN_PENDING` Loans with Member, EquipmentType, Equipment ID, Member-reported condition, and any damage image/description.

### EXCO-29 — Verify Good Return
After checking the item, Exco may verify it as good:

- Loan: `RETURN_PENDING -> COMPLETED`
- Equipment condition: `GOOD`
- Equipment availability: `AVAILABLE`

### EXCO-30 — Verify Damaged Return
After checking the item, Exco may verify it as damaged:

- Loan: `RETURN_PENDING -> COMPLETED`
- Equipment condition: `DAMAGED`
- Equipment availability: `AVAILABLE` or `UNAVAILABLE`, chosen by Exco

### EXCO-31 — Exco Is Authoritative
The Member's reported condition is advisory. Exco verification determines final Equipment condition and availability.

## 10. Verify Lost Equipment

### EXCO-32 — View Pending Lost Reports
Exco shall be able to view `LOST_PENDING` Loans with Member, EquipmentType, Equipment ID, and loss description.

### EXCO-33 — Confirm Lost Item
Only Exco may confirm a pending lost-item report.

After confirmation:

- Loan: `LOST_PENDING -> COMPLETED`
- Equipment condition: `LOST`
- Equipment availability: `UNAVAILABLE`

Dispute handling or recovery before confirmation is outside the current project scope.

## 11. Exco Restrictions

Exco must not:

- approve or reject a request that is not `PENDING`
- assign an item that is not `AVAILABLE`
- assign an item of a different EquipmentType
- assign more items than requested
- approve with zero assigned items
- allocate the same EquipmentItem to multiple unresolved Loans
- cancel an active Loan
- modify a Loan end date after approval
- remove an EquipmentItem referenced by an unresolved Loan
- mark a `LOST` item as `AVAILABLE`
