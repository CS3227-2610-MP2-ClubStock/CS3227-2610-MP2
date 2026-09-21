# Project name: ClubStock

ClubStock is a single-machine equipment management application with two roles: Member and
Exco. The Java desktop application targets Java 25 and JavaFX 25, supports Windows, macOS,
and Linux, and is packaged as a fat JAR. Shared business data has one source of truth for
both roles; local persistence is an implementation concern.

## Authentication and accounts

- The system contains one pre-created Exco account. On its first login, Exco must set the
  account password before normal authentication is available. Additional Exco accounts are
  not required.
- Exco creates Member accounts. Each Member has a unique, immutable, case-sensitive Member
  ID, a name, and login credentials. Surrounding whitespace is trimmed for required account
  text, and Member IDs must not be blank.
- Exco may edit a Member's name or replace the Member's password, but may not change the
  Member ID. Member self-registration is not required, and Members do not need to change the
  password set by Exco on first login. Passwords have at least eight characters and are stored
  only as salted PBKDF2-HMAC-SHA256 hashes.

## Member features

- Members browse EquipmentTypes and see each type's current available quantity. Individual
  Equipment IDs are hidden until an item is assigned to that Member.
- Members create LoanRequests for an EquipmentType, requested quantity, start date, end date,
  and optional details. The end date cannot be before the start date. A request records its
  submission time, starts as `PENDING`, and does not reserve or assign equipment.
- A type remains visible and requestable when its available quantity is zero; the application
  warns the Member before submission but does not reject the request for that reason.
- Members view their own requests and may cancel only their own `PENDING` requests. After
  approval, Members view their individually assigned Loans and may return one item as good,
  report damage with an image and description, or report the item as lost with a description.
  Each Loan follows only one of these return or loss branches.
- Members can see an overdue indicator for an active Loan after its end date. Overdue display
  does not change the Loan's status.

## Exco features

- Exco can view, create, and edit Member accounts, and may remove a Member only when doing so
  does not corrupt an unresolved LoanRequest or Loan.
- Exco manages physical EquipmentItems individually. Each item has a unique Equipment ID, an
  EquipmentType, a condition (`GOOD`, `DAMAGED`, or `LOST`), and an availability state
  (`AVAILABLE`, `ON_LOAN`, or `UNAVAILABLE`). Only `AVAILABLE` items contribute to the
  Member-visible quantity and can be allocated.
- Exco views pending LoanRequests, may reject them, or may approve a request by selecting one
  or more available items of the requested type. Partial approval is allowed. Approval creates
  one Loan per selected item and changes those items to `ON_LOAN`; a request cannot be approved
  with no available selection. If approval exhausts a type's available stock, other pending
  requests for that type are rejected automatically.
- Exco verifies returned and lost items. Exco's verification determines the authoritative item
  condition: a good return makes the item available, a damaged item may be made available or
  unavailable, and a lost item remains unavailable. Member damage and loss reports are advisory
  until verification.

## Shared domain rules

Requests, inventory, accounts, Loans, and reports are shared between the Member and Exco
features. Role-specific interfaces must use the same domain and persistence state so that an
action performed by one role is reflected in the other role's view.
