# Project Requirements

## 1. Source and interpretation

This document consolidates the implementation requirements defined in:

- Shared.md
- ExcoSpec.md
- MemberSpec.md

It replaces the previous ProjectRequirements.md in full. Requirements or constraints from the previous document are not retained unless they are stated in one of the three source specifications.

In this document:

- “shall” and “must” identify required behaviour;
- “should” preserves advisory wording used by a source specification;
- “is not required” identifies behaviour outside the required project scope and does not prohibit an implementation from providing it;
- matters listed as unspecified are not implementation requirements and must not be resolved by assumption.

## 2. Functional requirements

### F1: Authentication and account management

#### F1.1: Exco account

- F1.1.1: The system shall contain one pre-created Exco account.
- F1.1.2: On the pre-created Exco account's first login, the system shall require the Exco user to set or change the account password.
- F1.1.3: The system is not required to support creation of additional Exco accounts.
- F1.1.4: The system shall allow an Exco user to log in.

#### F1.2: Member accounts and authentication

- F1.2.1: Each Member account shall contain at least a unique Member ID, a name, and login credentials.
- F1.2.2: The system shall allow Exco to create a Member account and set that Member's password.
- F1.2.3: The system shall allow a Member to log in using credentials for an account created by Exco.
- F1.2.4: The system shall allow a Member to use the password set by Exco without requiring a password change on first login.
- F1.2.5: The system is not required to support Member self-registration.

#### F1.3: Exco management of Members

- F1.3.1: The system shall allow Exco to view Member accounts.
- F1.3.2: The system shall allow Exco to edit Member information supported by the application.
- F1.3.3: The system shall allow Exco to remove a Member only when the removal does not corrupt an unresolved Loan or LoanRequest that references that Member.
- F1.3.4: A Member-removal operation that would corrupt an unresolved Loan or LoanRequest shall not complete.

#### F1 account-model decisions

The following decisions clarify the account model used by the domain and later authentication
layers:

- A Member ID is an immutable, case-sensitive login identifier. Surrounding whitespace is
  trimmed, and blank identifiers are invalid.
- Exco may edit a Member's name and replace the Member's password, but may not edit the Member
  ID.
- Member names and other required account text are trimmed and must be nonblank.
- Passwords must contain at least eight characters and are stored only as salted
  PBKDF2-HMAC-SHA256 hashes. The hash format and hashing service are implementation concerns
  outside the core domain model.
- The singleton Exco account starts without a credential and requires local first-run password
  setup before normal authentication.

### F2: Domain model and states

#### F2.1: EquipmentType

- F2.1.1: An EquipmentType shall represent a kind of equipment that a Member can browse and request.
- F2.1.2: A Member shall interact with EquipmentTypes, rather than individual EquipmentItems, while browsing equipment and creating a LoanRequest.

#### F2.2: EquipmentItem

- F2.2.1: An EquipmentItem shall represent one physical item owned by the CCA.
- F2.2.2: Each EquipmentItem shall contain at least a unique Equipment ID, an EquipmentType, a condition, and an availability state.
- F2.2.3: An EquipmentItem condition shall be one of GOOD, DAMAGED, or LOST.
- F2.2.4: An EquipmentItem availability state shall be one of AVAILABLE, ON_LOAN, or UNAVAILABLE.
- F2.2.5: AVAILABLE shall mean that Exco has cleared the EquipmentItem for allocation to a new Loan.
- F2.2.6: ON_LOAN shall mean that the EquipmentItem is currently assigned to a Member.
- F2.2.7: UNAVAILABLE shall mean that the EquipmentItem must not be allocated to a new Loan.
- F2.2.8: An EquipmentItem condition shall become authoritative only through Exco verification.
- F2.2.9: A Member's damage or loss report shall not by itself determine the authoritative EquipmentItem condition.
- F2.2.10: An EquipmentItem whose authoritative condition is LOST shall always have availability UNAVAILABLE.
- F2.2.11: An EquipmentItem whose authoritative condition is DAMAGED may have availability AVAILABLE or UNAVAILABLE according to Exco's assessment.

#### F2.3: LoanRequest

- F2.3.1: A LoanRequest shall represent a Member's request for a quantity of one EquipmentType.
- F2.3.2: Each LoanRequest shall contain at least a unique Request ID, the requesting Member, the EquipmentType, requested quantity, requested start date, requested end date, optional details, an automatically recorded request creation date/time named requestedAt, request status, and approved quantity when approved.
- F2.3.3: A LoanRequest status shall be one of PENDING, APPROVED, REJECTED, or CANCELLED.
- F2.3.4: A PENDING LoanRequest shall reference an EquipmentType and shall not be tied to a specific EquipmentItem or Equipment ID.

#### F2.4: Loan

- F2.4.1: A Loan shall represent one specific EquipmentItem assigned to one Member.
- F2.4.2: Each Loan shall contain at least a unique Loan ID, the Member, the assigned EquipmentItem, loan start date/time, loan end date, and current Loan status.
- F2.4.3: A Loan status shall be one of ON_LOAN, RETURN_PENDING, LOST_PENDING, or COMPLETED.
- F2.4.4: The Loan model shall not have a separate APPROVED status.
- F2.4.5: The system shall create a Loan only after Exco approves a LoanRequest and assigns a specific EquipmentItem.
- F2.4.6: The system shall create exactly one individual Loan for each EquipmentItem assigned during approval.
- F2.4.7: Loans created from multiple EquipmentItems assigned to one LoanRequest shall be independently manageable.

### F3: Equipment discovery and inventory

#### F3.1: Member equipment view

- F3.1.1: The system shall allow a Member to view EquipmentTypes offered for loan.
- F3.1.2: The system shall not show individual Equipment IDs to a Member while the Member browses equipment or creates a LoanRequest.
- F3.1.3: For each EquipmentType, the system shall show the Member a current available quantity equal to the number of EquipmentItems of that type whose availability is AVAILABLE.
- F3.1.4: An EquipmentType shall remain visible and requestable when its current available quantity is zero.
- F3.1.5: Before a Member submits a request for an EquipmentType whose current available quantity is zero, the system shall warn the Member.
- F3.1.6: The system shall not reject or invalidate a LoanRequest solely because the current available quantity is zero.

#### F3.2: Exco inventory management

- F3.2.1: The system shall allow Exco to view individual EquipmentItems, including each item's Equipment ID, EquipmentType, condition, and availability.
- F3.2.2: The system shall allow Exco to add inventory as individual EquipmentItems with unique Equipment IDs.
- F3.2.3: The system shall allow Exco to make an EquipmentItem AVAILABLE when Exco determines that it is suitable for future allocation.
- F3.2.4: Only EquipmentItems whose availability is AVAILABLE shall contribute to Member-visible available quantity.
- F3.2.5: The system shall allow Exco to remove an EquipmentItem only when no unresolved Loan in ON_LOAN, RETURN_PENDING, or LOST_PENDING references that item.
- F3.2.6: The system shall not allow an EquipmentItem whose authoritative condition is LOST to be made AVAILABLE.
- F3.2.7: The system shall allow Exco to assess an EquipmentItem whose authoritative condition is DAMAGED as either AVAILABLE or UNAVAILABLE.

#### F3.3: Equipment ID visibility

- F3.3.1: Only Exco shall be able to view an EquipmentItem's ID before that item is allocated.
- F3.3.2: After an EquipmentItem is assigned, the system shall allow the assigned Member to view that item's Equipment ID.
- F3.3.3: A Member shall not be able to view the Equipment ID of an item that has not been assigned to that Member.

### F4: LoanRequest creation and viewing

#### F4.1: Request validation

- F4.1.1: A new LoanRequest shall be valid only when the Member exists.
- F4.1.2: A new LoanRequest shall be valid only when the EquipmentType exists.
- F4.1.3: A new LoanRequest shall be valid only when the requested quantity is a positive integer.
- F4.1.4: A new LoanRequest shall be valid only when the requested start date and requested end date are valid.
- F4.1.5: A new LoanRequest shall be valid only when the requested end date is not before the requested start date.
- F4.1.6: Current available quantity shall not be a validity condition for a LoanRequest.
- F4.1.7: A Member shall select an EquipmentType, not a specific EquipmentItem or Equipment ID, when creating a LoanRequest.
- F4.1.8: A Member shall provide a requested quantity, requested start date, and requested end date.
- F4.1.9: A Member may provide optional request details.
- F4.1.10: The system shall allow a Member to create a LoanRequest.

#### F4.2: Request submission

- F4.2.1: The system shall automatically record the date and time at which a LoanRequest is submitted as requestedAt.
- F4.2.2: A successfully submitted valid LoanRequest shall begin with status PENDING.
- F4.2.3: Creating a LoanRequest shall not reserve or assign any EquipmentItem.
- F4.2.4: Creating a LoanRequest shall not change any EquipmentItem availability state.
- F4.2.5: The requested start date shall be informational only.
- F4.2.6: The requested start date shall not reserve equipment or prevent Exco from applying judgment when deciding whether to approve the request.
- F4.2.7: Requested quantity shall not guarantee approved quantity.

#### F4.3: Member request view and cancellation

- F4.3.1: The system shall allow a Member to view that Member's own LoanRequests.
- F4.3.2: For each LoanRequest shown to the Member, the system shall display at least the EquipmentType, requested quantity, requested start date, requested end date, request status, and approved quantity when applicable.
- F4.3.3: The system shall display a manually or automatically rejected request to its requesting Member with status REJECTED.
- F4.3.4: The system shall allow only the requesting Member to cancel that Member's PENDING LoanRequest.
- F4.3.5: Cancelling a request shall perform the transition PENDING to CANCELLED.
- F4.3.6: The system shall not allow a Member to cancel a LoanRequest whose status is APPROVED, REJECTED, or CANCELLED.
- F4.3.7: A CANCELLED LoanRequest shall not later be approved.

#### F4.4: Exco pending-request view

- F4.4.1: The system shall allow Exco to view LoanRequests whose status is PENDING.
- F4.4.2: Pending requests shown to Exco should be ordered from earliest to latest requestedAt.
- F4.4.3: The ordering of pending requests shall not force Exco to approve them in first-come-first-served order.
- F4.4.4: For each pending LoanRequest, the system shall show Exco at least the Member, EquipmentType, requested quantity, current available quantity, requested start date, requested end date, requestedAt, and optional details.

### F5: LoanRequest approval and rejection

#### F5.1: Approval eligibility and selection

- F5.1.1: Only Exco shall be able to approve a LoanRequest.
- F5.1.2: Exco shall be able to approve only a LoanRequest whose status is PENDING.
- F5.1.3: During approval, Exco shall select one or more specific EquipmentItems of the requested EquipmentType.
- F5.1.4: Every selected EquipmentItem shall have availability AVAILABLE.
- F5.1.5: EquipmentItems whose availability is ON_LOAN or UNAVAILABLE shall not be selectable or allocatable.
- F5.1.6: The approved quantity shall be at least one.
- F5.1.7: The approved quantity shall not exceed the requested quantity.
- F5.1.8: The approved quantity shall not exceed the number of currently AVAILABLE EquipmentItems of the requested EquipmentType.
- F5.1.9: Exco may approve fewer EquipmentItems than the Member requested.
- F5.1.10: If no EquipmentItem of the requested EquipmentType is AVAILABLE, approval shall not complete and the system shall notify Exco.

#### F5.2: Approval result

- F5.2.1: When approval is confirmed, the LoanRequest shall transition from PENDING to APPROVED.
- F5.2.2: The system shall record an approved quantity equal to the number of selected EquipmentItems.
- F5.2.3: Each selected EquipmentItem shall transition from AVAILABLE to ON_LOAN.
- F5.2.4: The system shall create one ON_LOAN Loan for each selected EquipmentItem.
- F5.2.5: Each created Loan shall reference the requesting Member and one selected EquipmentItem.
- F5.2.6: Each assigned Equipment ID shall become visible to the requesting Member.
- F5.2.7: If the approved quantity is less than the requested quantity, the unapproved quantity shall not remain pending.
- F5.2.8: A Member who wants additional EquipmentItems after a partial approval shall submit a new LoanRequest.

#### F5.3: Automatic rejection when stock reaches zero

- F5.3.1: If an approval causes the available quantity of its EquipmentType to become zero, the system shall transition every other LoanRequest for that EquipmentType that is PENDING at that moment to REJECTED.
- F5.3.2: The automatic rejection shall not affect LoanRequests for other EquipmentTypes.
- F5.3.3: A LoanRequest automatically rejected under F5.3.1 shall not be reopened automatically if equipment later becomes AVAILABLE.
- F5.3.4: The system shall continue to permit new LoanRequests while current available quantity is zero, subject to the warning in F3.1.5.

#### F5.4: Manual rejection

- F5.4.1: Only Exco shall be able to reject a LoanRequest.
- F5.4.2: Exco shall be able to reject only a LoanRequest whose status is PENDING.
- F5.4.3: Rejecting a request shall perform the transition PENDING to REJECTED.
- F5.4.4: A REJECTED LoanRequest shall not later be approved.

### F6: Active Loans and overdue display

#### F6.1: Loan creation and dates

- F6.1.1: Approval of a LoanRequest shall cause each resulting Loan to begin immediately with status ON_LOAN.
- F6.1.2: A resulting Loan's end date shall be based on the requested end date of its source LoanRequest.
- F6.1.3: The system shall not support extension of a Loan after approval.
- F6.1.4: The system shall not allow a Loan's end date to be modified after approval.

#### F6.2: Member Loan view

- F6.2.1: The system shall allow a Member to view the Member's active Loans.
- F6.2.2: The system shall display each assigned EquipmentItem as an individual Loan.
- F6.2.3: For each displayed Loan, the system shall show at least its EquipmentType, Equipment ID, status, start date/time, and end date.
- F6.2.4: Returning or reporting one assigned EquipmentItem shall not automatically affect any other assigned EquipmentItem or Loan.

#### F6.3: Exco Loan view

- F6.3.1: The system shall allow Exco to view active individual Loans and their assigned Equipment IDs.

#### F6.4: Overdue Loans

- F6.4.1: When the current date is past the end date of a Loan whose status is ON_LOAN, the system shall visibly mark that Loan as overdue.
- F6.4.2: An overdue indication shall not change the Loan's ON_LOAN status.
- F6.4.3: The system shall not automatically apply a fine, penalty, forced return, cancellation, extension, or overdue-follow-up notification.
- F6.4.4: Any overdue follow-up is outside the application and is handled by Exco.
- F6.4.5: The overdue indication shall be visible in both Member and Exco Loan views.

#### F6.5: Active-Loan cancellation

- F6.5.1: Neither Member nor Exco shall be able to cancel a Loan whose status is ON_LOAN, RETURN_PENDING, or LOST_PENDING.
- F6.5.2: Once a Loan is ON_LOAN, it shall be resolved through the return or lost-item workflow.

### F7: Member return and loss reporting

#### F7.1: Return an individual item

- F7.1.1: The system shall allow a Member to submit a return only for an individual EquipmentItem currently ON_LOAN to that Member.
- F7.1.2: Submitting a return shall transition the corresponding Loan from ON_LOAN to RETURN_PENDING.
- F7.1.3: Submitting a return shall transition the corresponding EquipmentItem availability from ON_LOAN to UNAVAILABLE.
- F7.1.4: The EquipmentItem shall remain UNAVAILABLE until Exco verifies the return.
- F7.1.5: The return workflow shall allow the Member to report that the item appears to be in good condition or damaged.
- F7.1.6: A good-condition return shall not require a damage report.
- F7.1.7: A damaged return report shall require an image and a description.
- F7.1.8: A Member's reported condition shall remain advisory until Exco verification.
- F7.1.9: The system shall not allow another normal-return submission for a Loan in RETURN_PENDING, LOST_PENDING, or COMPLETED.

#### F7.2: Report an individual item as lost

- F7.2.1: Report Lost shall be an action separate from Return.
- F7.2.2: The system shall allow a Member to report an individual EquipmentItem whose Loan is currently ON_LOAN as lost.
- F7.2.3: A lost-item report shall require a description.
- F7.2.4: Submitting a lost-item report shall transition the corresponding Loan from ON_LOAN to LOST_PENDING.
- F7.2.5: Submitting a lost-item report shall transition the corresponding EquipmentItem availability from ON_LOAN to UNAVAILABLE.
- F7.2.6: Submitting a lost-item report shall not set the authoritative EquipmentItem condition to LOST.
- F7.2.7: The system is not required to support disputes or recovery of an item while its loss report is pending.

### F8: Exco verification

#### F8.1: Review pending returns

- F8.1.1: The system shall allow Exco to view Loans whose status is RETURN_PENDING.
- F8.1.2: For each RETURN_PENDING Loan, the system shall show Exco the Member, EquipmentType, Equipment ID, Member-reported condition, and any submitted damage image and description.

#### F8.2: Verify a returned item

- F8.2.1: Only Exco shall be able to verify a RETURN_PENDING Loan.
- F8.2.2: When Exco verifies a return as good, the system shall transition the Loan from RETURN_PENDING to COMPLETED, set the EquipmentItem condition to GOOD, and set its availability to AVAILABLE.
- F8.2.3: When Exco verifies a return as damaged, the system shall transition the Loan from RETURN_PENDING to COMPLETED and set the EquipmentItem condition to DAMAGED.
- F8.2.4: When verifying a damaged return, Exco shall choose whether the EquipmentItem availability becomes AVAILABLE or UNAVAILABLE.
- F8.2.5: Exco verification shall determine the final EquipmentItem condition and availability; the final outcome need not match the Member's report.

#### F8.3: Review and confirm pending loss reports

- F8.3.1: The system shall allow Exco to view Loans whose status is LOST_PENDING.
- F8.3.2: For each LOST_PENDING Loan, the system shall show Exco the Member, EquipmentType, Equipment ID, and loss description.
- F8.3.3: Only Exco shall be able to confirm a LOST_PENDING Loan as lost.
- F8.3.4: When Exco confirms a lost item, the system shall transition the Loan from LOST_PENDING to COMPLETED, set the EquipmentItem condition to LOST, and set its availability to UNAVAILABLE.
- F8.3.5: The system is not required to support dispute handling or recovery of an item before loss confirmation.

### F9: Authorization and domain integrity

#### F9.1: Member restrictions

- F9.1.1: A Member shall not be able to view Equipment IDs for EquipmentItems not assigned to that Member.
- F9.1.2: A Member shall not be able to select an Equipment ID when creating a LoanRequest.
- F9.1.3: A Member shall not be able to approve or reject a LoanRequest.
- F9.1.4: A Member shall not be able to choose which EquipmentItems Exco assigns.
- F9.1.5: A Member shall not be able to verify or finalize a return, damage report, or loss report.
- F9.1.6: A Member shall not be able to make an EquipmentItem AVAILABLE.
- F9.1.7: A Member shall not be able to cancel an active Loan.
- F9.1.8: A Member shall not be able to change a Loan end date.
- F9.1.9: A Member shall not be able to use Exco-only Member-management or inventory-management functions.

#### F9.2: Exco restrictions

- F9.2.1: Exco shall not be able to approve or reject a LoanRequest that is not PENDING.
- F9.2.2: Exco shall not be able to assign an EquipmentItem that is not AVAILABLE.
- F9.2.3: Exco shall not be able to assign an EquipmentItem whose EquipmentType differs from the requested EquipmentType.
- F9.2.4: Exco shall not be able to assign more EquipmentItems than the Member requested.
- F9.2.5: Exco shall not be able to approve a LoanRequest with zero assigned EquipmentItems.
- F9.2.6: Exco shall not be able to allocate one EquipmentItem to multiple unresolved Loans.
- F9.2.7: Exco shall not be able to cancel a Loan in ON_LOAN, RETURN_PENDING, or LOST_PENDING.
- F9.2.8: Exco shall not be able to modify a Loan end date after approval.
- F9.2.9: Exco shall not be able to remove an EquipmentItem referenced by a Loan in ON_LOAN, RETURN_PENDING, or LOST_PENDING.
- F9.2.10: Exco shall not be able to mark an EquipmentItem whose authoritative condition is LOST as AVAILABLE.

#### F9.3: Global invariants

- F9.3.1: Every Member ID shall be unique.
- F9.3.2: Every Equipment ID shall be unique.
- F9.3.3: Every Request ID shall be unique.
- F9.3.4: Every Loan ID shall be unique.
- F9.3.5: A PENDING LoanRequest shall reference an EquipmentType rather than a specific EquipmentItem.
- F9.3.6: Only an AVAILABLE EquipmentItem shall be allocated.
- F9.3.7: Every allocated EquipmentItem shall create exactly one individual Loan.
- F9.3.8: An EquipmentItem shall not belong to more than one unresolved Loan at the same time.
- F9.3.9: An ON_LOAN EquipmentItem shall not be allocated to another Member.
- F9.3.10: An EquipmentItem associated with a RETURN_PENDING or LOST_PENDING Loan, or whose availability is otherwise UNAVAILABLE, shall not be allocated.
- F9.3.11: A REJECTED or CANCELLED LoanRequest shall not later be approved.
- F9.3.12: Only Exco shall approve or reject PENDING LoanRequests.
- F9.3.13: Only the requesting Member shall cancel that Member's PENDING LoanRequest.
- F9.3.14: Only Exco shall verify returns and lost-item reports.
- F9.3.15: Active Loans shall not be cancelled.
- F9.3.16: An EquipmentItem referenced by an unresolved Loan shall not be removed.
- F9.3.17: Shared state shall remain consistent between Member and Exco features.

## 3. Architecture and shared-data requirements

### N1: Shared source of truth

- N1.1: Members, EquipmentTypes, EquipmentItems, LoanRequests, Loans, damage reports, and loss reports shall be shared between Member and Exco features.
- N1.2: The system shall maintain one shared source of truth for this shared data.
- N1.3: Member and Exco features shall not maintain independent copies of the same business data.
- N1.4: An action performed through one role shall be reflected in the other role's view of shared state.
- N1.5: Role-specific Member and Exco user interfaces should remain separate.

### N2: Separation of concerns

- N2.1: Shared business rules shall not be duplicated between Member and Exco user-interface code.
- N2.2: Shared business rules include request validation, available-quantity calculation, request state transitions, approval and allocation, automatic rejection when stock is exhausted, Loan creation, equipment availability transitions, overdue detection, return processing, and damage/loss verification.
- N2.3: The intended dependency direction is UI to Controller to Service to Domain/Repository.
- N2.4: JavaFX user-interface code should not directly manipulate persistent storage.

## 4. Explicit non-goals

The source specifications do not require:

- equipment pre-booking or reservation;
- Member self-registration;
- loan-extension requests;
- Exco modification of a Loan end date after approval;
- automatic fines or overdue penalties;
- automatic chasing or notification of overdue Members;
- cancellation of an active Loan;
- long-term or historical Loan tracking;
- historical-record retention after completion;
- automatic reopening of rejected requests when stock later becomes available;
- in-application handling of disputed or recovered lost-item reports;
- creation of additional Exco accounts.

## 5. Unspecified points requiring clarification

The source specifications do not define the following matters. They are intentionally left unspecified rather than resolved by assumptions:

- password reset, logout, session behaviour, and the remaining credential-validation details;
- the initial authentication or bootstrap mechanism for the pre-created Exco account before first-login password setup;
- which LoanRequest statuses are considered unresolved for Member removal and whether referenced-member removal must be blocked or may preserve valid record snapshots;
- EquipmentType creation, editing, removal, and selection of which types are offered for loan;
- the initial condition and availability assigned to a newly added EquipmentItem;
- the EquipmentItem states or workflows from which Exco may manually make an item AVAILABLE, beyond the stated LOST restriction;
- date format, timezone, whether past requested dates are valid, and other date-validation bounds;
- the precise date/time boundary and timezone used to determine when an ON_LOAN Loan is overdue;
- tie-breaking when pending requests have identical requestedAt values;
- whether “active Loans” means only ON_LOAN Loans or all unresolved Loans;
- persistence technology and detailed repository implementation;
- approval concurrency, transaction boundaries, and ID-generation mechanisms;
- notification wording or presentation when approval is impossible because no item is AVAILABLE;
- rejection reasons, cancellation reasons, verification notes, and audit metadata;
- optional request-details format, validation, and size limits;
- damage-image format, validation, storage, and size limits;
- description format, validation, and size limits for damage and loss reports;
- whether completed Loans or assigned Equipment IDs remain visible to Members;
- a workflow for reversing or rejecting a RETURN_PENDING report;
- recovery transitions from LOST or COMPLETED;
- state transitions out of COMPLETED;
- the complete permitted condition/availability combination matrix beyond the transitions and restrictions stated above;
- interface layout, search, filtering, pagination, accessibility, performance targets, and other non-functional behaviour not stated in the source specifications.

## 6. Source traceability

- Shared.md sections 2–12 are represented by F1–F9 and N1.
- Shared.md section 13 is represented by N2.
- Shared.md section 14 is represented by F9.3 and the related lifecycle requirements.
- Shared.md section 15 is represented by the explicit non-goals.
- ExcoSpec.md requirements EXCO-01 through EXCO-33 are represented by F1, F3, F4.4, F5, F6.3–F6.5, F8, and F9.2.
- MemberSpec.md requirements MEM-01 through MEM-27 are represented by F1, F3.1, F3.3, F4, F6.2, F6.4–F6.5, F7, and F9.1.
