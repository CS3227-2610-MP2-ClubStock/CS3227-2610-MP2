# Member Role Specification

## 1. Purpose

This document defines the requirements for the **Member** role.

All shared domain rules in `Shared.md` apply.

## 2. Authentication

### MEM-01 — Login
A Member shall be able to log in using credentials for an account created by Exco.

### MEM-02 — No Self-Registration
The system is not required to support Member self-registration.

### MEM-03 — Exco-Set Password
A Member may use the password set by Exco normally and is not required to change it on first login.

## 3. Browse Equipment

### MEM-04 — View Equipment Types
A Member shall be able to view EquipmentTypes offered for loan without seeing individual Equipment IDs.

### MEM-05 — View Available Quantity
For each EquipmentType, the Member shall see the current quantity of EquipmentItems whose availability is `AVAILABLE`.

### MEM-06 — Zero-Stock Requests
An EquipmentType with available quantity `0` remains visible and requestable. The system must show a warning before submission, but zero stock alone must not prevent the request.

## 4. Create Loan Request

### MEM-07 — Select EquipmentType
A Member shall request an EquipmentType, not a specific EquipmentItem.

### MEM-08 — Select Quantity
A Member shall enter a positive requested quantity. The requested quantity does not guarantee the approved quantity.

### MEM-09 — Requested Dates
A Member shall provide a requested start date and requested end date. The end date must not be before the start date. The start date is informational only and does not reserve equipment.

### MEM-10 — Optional Details
A Member may provide optional request details.

### MEM-11 — Automatic Timestamp
The system shall automatically record the request submission date/time.

### MEM-12 — Initial Status
A successfully submitted request begins as `PENDING` and does not reserve or change any EquipmentItem.

## 5. View and Cancel Requests

### MEM-13 — View Own Requests
A Member shall be able to view their own requests, including EquipmentType, requested quantity, requested dates, request status, and approved quantity when applicable.

### MEM-14 — Cancel Pending Request
A Member may cancel only their own `PENDING` request:

`PENDING -> CANCELLED`

### MEM-15 — Partial Approval
If Exco approves fewer items than requested, the Member shall see the approved quantity. The unapproved quantity does not remain pending.

### MEM-16 — Rejected Request Visibility
If a request is rejected manually or automatically, the Member shall see it as `REJECTED`.

## 6. View Assigned Loans

### MEM-17 — View Assigned IDs
After approval, the Member shall be able to see each specific Equipment ID assigned to them.

### MEM-18 — Individual Loan Display
Each assigned EquipmentItem shall be displayed as an individual Loan with at least EquipmentType, Equipment ID, status, start date/time, and end date.

### MEM-19 — Independent Handling
Returning or reporting one assigned EquipmentItem must not automatically affect the Member's other assigned items.

## 7. Overdue Loans

### MEM-20 — Overdue Indicator
An `ON_LOAN` Loan past its end date shall be visibly marked overdue. It remains `ON_LOAN`; no automatic fine, extension, or cancellation is applied.

## 8. Return Equipment

### MEM-21 — Return Individual Item
A Member may submit a return for an individual EquipmentItem currently `ON_LOAN` to them.

- Loan: `ON_LOAN -> RETURN_PENDING`
- Equipment availability: `ON_LOAN -> UNAVAILABLE`

### MEM-22 — Good Return
For an apparently good-condition return, no damage report is required. Exco still performs final verification.

### MEM-23 — Damaged Return
For an apparently damaged return, the Member must provide an image and description. The report does not directly set the authoritative Equipment condition.

### MEM-24 — No Duplicate Return
A Loan already in `RETURN_PENDING`, `LOST_PENDING`, or `COMPLETED` must not allow another normal return submission.

## 9. Report Lost Equipment

### MEM-25 — Separate Lost Action
`Report Lost` shall be separate from the normal `Return` action.

### MEM-26 — Submit Lost Report
A Member may report an individual `ON_LOAN` EquipmentItem as lost and must provide a description.

- Loan: `ON_LOAN -> LOST_PENDING`
- Equipment availability: `ON_LOAN -> UNAVAILABLE`

### MEM-27 — Exco Verification Required
Submitting a lost report must not immediately set the authoritative Equipment condition to `LOST`.

## 10. Member Restrictions

A Member must not be able to:

- view unassigned Equipment IDs
- select an Equipment ID when creating a request
- approve or reject LoanRequests
- choose which EquipmentItems are assigned
- verify their own return, damage, or loss reports
- make equipment `AVAILABLE`
- cancel an active Loan
- change a Loan end date
- use Exco-only Member or inventory management functions
