# Shared Domain Specification

## 1. Purpose

This document defines the domain concepts, state rules, and cross-role behaviour shared by the Member and Exco sides of the CCA Equipment Loan Tracker.

Role-specific requirements are defined separately in:

- `MemberSpec.md`
- `ExcoSpec.md`

Both role implementations must follow this shared specification.

---

## 2. Core Concepts

The system distinguishes between an **EquipmentType**, an individual **EquipmentItem**, a **LoanRequest**, and an individual **Loan**.

### 2.1 EquipmentType

An `EquipmentType` represents the kind of equipment a Member can request.

Examples:

- Hockey Stick
- Helmet
- Goalkeeper Pads

Members interact only with EquipmentTypes when browsing and requesting equipment.

Members must not be shown individual Equipment IDs while browsing or creating requests.

### 2.2 EquipmentItem

An `EquipmentItem` represents one physical item owned by the CCA.

Each EquipmentItem has at least:

- a unique Equipment ID
- an EquipmentType
- a condition
- an availability status

Example:

- EquipmentType: Hockey Stick
- Equipment ID: `#234`

Only Exco users may view individual Equipment IDs before allocation.

Once an EquipmentItem has been assigned to a Member, that Member may see the Equipment ID for that assigned item.

### 2.3 LoanRequest

A `LoanRequest` represents a Member asking for a quantity of an EquipmentType.

A LoanRequest is not tied to any specific Equipment ID while it is pending.

Each LoanRequest contains at least:

- a unique Request ID
- Member
- EquipmentType
- requested quantity
- requested start date
- requested end date
- optional details
- request creation date/time (`requestedAt`)
- request status
- approved quantity, if approved

The requested start date is informational only. It does not reserve equipment and does not prevent Exco from using their own judgement when approving requests.

The system must automatically record the date and time at which the request was submitted.

Pending requests shown to Exco should be ordered from earliest to latest `requestedAt`.

### 2.4 Loan

A `Loan` represents one specific EquipmentItem assigned to one Member.

A Loan is created only after Exco approves a LoanRequest and assigns a specific EquipmentItem.

If Exco assigns multiple EquipmentItems for one request, one individual Loan is created for each assigned EquipmentItem.

Example:

A Member requests:

- EquipmentType: Hockey Stick
- Quantity: 3

Exco assigns:

- Hockey Stick `#234`
- Hockey Stick `#241`

The request is approved with an approved quantity of 2, and two separate Loans are created.

Each Loan contains at least:

- a unique Loan ID
- Member
- assigned EquipmentItem
- start date/time of the loan
- end date
- current Loan status

The Member can manage the Loans independently after allocation.

---

## 3. User Roles

### 3.1 Member

A Member may:

- log in using an account created by Exco
- browse EquipmentTypes and current available quantities
- create LoanRequests
- view their own LoanRequests
- cancel their own pending LoanRequests
- view Equipment IDs assigned to them after approval
- view their active Loans
- return individual EquipmentItems
- report individual EquipmentItems as damaged during return
- report individual EquipmentItems as lost

### 3.2 Exco

An Exco user may:

- log in
- manage Member accounts
- manage equipment inventory
- view individual Equipment IDs and their states
- view pending LoanRequests
- approve or reject pending LoanRequests
- choose the quantity to approve
- assign specific EquipmentItems to approved Members
- verify returned EquipmentItems
- verify damage reports
- verify lost-item reports
- view overdue Loans

Role-specific UI should remain separate.

---

## 4. Member Accounts

Each Member must contain at least:

- a unique Member ID
- name
- login credentials

Member accounts are created by Exco.

When creating a Member account, Exco sets the Member's password.

Members are not required to change this password on first login.

The system is not required to support Member self-registration.

An active Member referenced by an unresolved Loan or LoanRequest must not be removed in a way that corrupts that active record.

Historical-record retention after completion is out of scope.

---

## 5. Equipment State

### 5.1 Equipment Condition

An EquipmentItem has one of the following conditions:

- `GOOD`
- `DAMAGED`
- `LOST`

The condition is authoritative only after Exco verification.

A Member may report damage or loss, but the Member's report does not by itself determine the final Equipment condition.

### 5.2 Equipment Availability

An EquipmentItem has one of the following availability states:

- `AVAILABLE`
- `ON_LOAN`
- `UNAVAILABLE`

Meaning:

- `AVAILABLE`: Exco has cleared the item for allocation to a new Loan.
- `ON_LOAN`: the item is currently assigned to a Member.
- `UNAVAILABLE`: the item must not be allocated to a new Loan.

Only EquipmentItems with availability `AVAILABLE` may be assigned during approval.

An EquipmentItem with condition `LOST` must always be `UNAVAILABLE`.

An EquipmentItem with condition `DAMAGED` may be either `AVAILABLE` or `UNAVAILABLE`, depending on Exco's assessment.

### 5.3 Member-visible Available Quantity

Members do not see Equipment IDs.

For each EquipmentType, Members see a current available quantity calculated from individual EquipmentItems of that type whose availability is `AVAILABLE`.

An EquipmentType remains visible and requestable even when its current available quantity is `0`.

When the available quantity is `0`, the Member must be warned before submitting the request, but the request must not be denied solely because the current quantity is zero.

---

## 6. LoanRequest Status

A LoanRequest has one of the following statuses:

- `PENDING`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

### 6.1 Creating a Request

A valid new request begins as `PENDING`.

Creating a request does not reserve any EquipmentItem and does not change any EquipmentItem availability state.

### 6.2 Member Cancellation

A Member may cancel only their own `PENDING` request:

`PENDING -> CANCELLED`

A cancelled request cannot later be approved.

### 6.3 Exco Rejection

Exco may reject only a `PENDING` request:

`PENDING -> REJECTED`

A rejected request cannot later be approved.

### 6.4 Exco Approval

Exco may approve only a `PENDING` request.

During approval:

1. Exco selects one or more `AVAILABLE` EquipmentItems of the requested EquipmentType.
2. The number assigned must not exceed the Member's requested quantity or the number of currently `AVAILABLE` items.
3. Exco may approve fewer items than the requested quantity.
4. At least one EquipmentItem must be assigned for the request to be approved.
5. The request becomes `APPROVED`.
6. The approved quantity is recorded.
7. One individual Loan is created for each assigned EquipmentItem.
8. Each assigned EquipmentItem immediately changes `AVAILABLE -> ON_LOAN`.

Any unapproved portion of the requested quantity is not kept pending.

If the Member still wants additional items, a new request must be submitted.

### 6.5 Automatic Rejection When Stock Is Exhausted

When an Exco approval causes the available quantity of an EquipmentType to become `0`, every other request for that EquipmentType that is `PENDING` at that moment is automatically changed to `REJECTED`.

Those rejected requests are not automatically reopened if equipment later becomes available.

A new request may still be submitted later while the displayed available quantity is `0`; it is allowed, with the required zero-stock warning.

---

## 7. Loan Status and Lifecycle

Each assigned EquipmentItem has its own Loan.

A Loan has one of the following statuses:

- `ON_LOAN`
- `RETURN_PENDING`
- `LOST_PENDING`
- `COMPLETED`

There is no separate `APPROVED` Loan state.

Approval of a LoanRequest immediately creates one or more `ON_LOAN` Loans.

### 7.1 Approval

For each allocated EquipmentItem:

- a Loan is created with status `ON_LOAN`
- the EquipmentItem availability becomes `ON_LOAN`
- the Member can now see that EquipmentItem's ID on their dashboard

### 7.2 Individual Returns

Each Loan is independent.

If several EquipmentItems were allocated from one LoanRequest, the Member may return them separately at different times.

### 7.3 Returning an Item

When a Member submits a return for an individual EquipmentItem:

Loan:

`ON_LOAN -> RETURN_PENDING`

Equipment availability:

`ON_LOAN -> UNAVAILABLE`

The item remains `UNAVAILABLE` until Exco verifies the return.

The Member reports whether the returned item appears to be:

- in good condition, or
- damaged

A damaged return report must contain:

- image
- description

The Member report is not the authoritative final condition.

### 7.4 Reporting an Item as Lost

`Report Lost` is a separate action from `Return`.

When a Member reports an individual EquipmentItem as lost:

Loan:

`ON_LOAN -> LOST_PENDING`

Equipment availability:

`ON_LOAN -> UNAVAILABLE`

The loss report must contain:

- description

The Member's report does not itself change the Equipment condition to `LOST`.

The app is only required to support Exco confirmation of the loss report. Disputes or recovery of an item while a loss report is pending are outside the current project scope.

### 7.5 Exco Verification of a Returned Item

Only Exco may verify a `RETURN_PENDING` Loan.

For a good return:

- Loan: `RETURN_PENDING -> COMPLETED`
- Equipment condition: `GOOD`
- Equipment availability: `AVAILABLE`

For a damaged return:

- Loan: `RETURN_PENDING -> COMPLETED`
- Equipment condition: `DAMAGED`
- Equipment availability: `AVAILABLE` or `UNAVAILABLE`, chosen by Exco based on assessment

### 7.6 Exco Verification of a Lost Item

Only Exco may confirm a `LOST_PENDING` Loan as lost.

After confirmation:

- Loan: `LOST_PENDING -> COMPLETED`
- Equipment condition: `LOST`
- Equipment availability: `UNAVAILABLE`

---

## 8. Dates and Overdue Loans

A LoanRequest stores:

- requested start date
- requested end date
- automatic request creation date/time

The requested start date is a guide for Exco and has no reservation effect.

When Exco approves a request, the Loan begins immediately.

The Loan's end date is based on the request's requested end date.

The app does not support loan extensions or end-date modification after approval.

If the current date is past an `ON_LOAN` Loan's end date, the Loan is displayed as **overdue**.

Being overdue does not automatically change the Loan status.

There is no automatic fine, penalty, forced return, or automatic cancellation.

Exco handles follow-up with overdue Members outside the app.

---

## 9. Active Loan Cancellation

An `ON_LOAN`, `RETURN_PENDING`, or `LOST_PENDING` Loan cannot be cancelled by Exco or Member.

Once a Loan is `ON_LOAN`, it must be resolved through the return or lost-item workflow.

---

## 10. Equipment Removal

Exco manages EquipmentItems by their individual Equipment IDs.

An EquipmentItem must not be removed while it is referenced by an unresolved Loan whose status is:

- `ON_LOAN`
- `RETURN_PENDING`
- `LOST_PENDING`

Historical loan tracking is outside the current project scope.

---

## 11. LoanRequest Validation

A new LoanRequest is valid only when:

- the Member exists
- the EquipmentType exists
- requested quantity is a positive integer
- requested start date is valid
- requested end date is valid
- requested end date is not before the requested start date

Current available quantity is not a validity requirement.

A request for an EquipmentType with `0` currently available items is valid, but the user must be warned.

---

## 12. Shared Data

The following are shared between Member and Exco features:

- Members
- EquipmentTypes
- EquipmentItems
- LoanRequests
- Loans
- damage reports
- loss reports

There must be one shared source of truth.

Member and Exco features must not maintain independent copies of the same business data.

Actions performed by one role must be reflected in the other role's view of shared state.

---

## 13. Shared Architecture Rules

Shared business rules must not be duplicated between Member and Exco UI code.

Examples include:

- request validation
- available-quantity calculation
- request state transitions
- approval and allocation rules
- automatic rejection when stock is exhausted
- Loan creation
- equipment availability changes
- overdue detection
- return processing
- damage/loss verification rules

The intended dependency direction is:

UI  
↓  
Controller  
↓  
Service  
↓  
Domain / Repository

JavaFX UI code should not directly manipulate persistent storage.

---

## 14. Invariants

1. Every Member ID is unique.
2. Every Equipment ID is unique.
3. Every Request ID is unique.
4. Every Loan ID is unique.
5. A pending LoanRequest references an EquipmentType, not a specific EquipmentItem.
6. A Member cannot see Equipment IDs before items are assigned to them.
7. Only an `AVAILABLE` EquipmentItem may be allocated.
8. Exco cannot allocate more items than the requested quantity.
9. Each allocated EquipmentItem creates exactly one individual Loan.
10. An EquipmentItem must not belong to more than one unresolved Loan at the same time.
11. An `ON_LOAN` EquipmentItem must not be allocated to another Member.
12. A `RETURN_PENDING`, `LOST_PENDING`, or otherwise `UNAVAILABLE` EquipmentItem must not be allocated.
13. A `LOST` EquipmentItem must be `UNAVAILABLE`.
14. A rejected or cancelled request cannot later be approved.
15. Only Exco may approve or reject pending requests.
16. Only the requesting Member may cancel their pending request.
17. Only Exco may verify returns and lost-item reports.
18. Active Loans cannot be cancelled.
19. Equipment referenced by an unresolved Loan cannot be removed.
20. Shared state must remain consistent between Member and Exco features.

---

## 15. Explicit Non-Goals

The current project does not require:

- equipment pre-booking or reservation
- Member self-registration
- loan extension requests
- Exco modification of Loan end dates after approval
- automatic fines or overdue penalties
- automatic chasing/notification of overdue Members
- cancellation of an active Loan
- long-term loan-history tracking
- reopening rejected requests when stock becomes available later
- resolving disputed or recovered lost-item reports inside the app
