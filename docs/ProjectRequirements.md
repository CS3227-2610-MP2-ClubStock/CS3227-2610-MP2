# Functional requirements
## F1: Application initialization and Exco account setup
### F1.1: Detect first launch
- F1.1.1: The system shall determine whether the Exco account has been initialized whenever the application starts.
- F1.1.2: The system shall display the Exco password setup screen when no initialized Exco account exists.
- F1.1.3: The system shall prevent access to all other application functions until the initial Exco password has been set.
- F1.1.4: The system shall persist the completion of initial setup across subsequent application launches.
### F1.2: Initialize the Exco account
- F1.2.1: The system shall allow an initial Exco password to be entered and confirmed.
- F1.2.2: The system shall reject the setup submission when the password and confirmation do not match.
- F1.2.3: The system shall create exactly one Exco account after valid setup information is submitted.
- F1.2.4: The system shall prevent the creation of any additional Exco account.
- F1.2.5: The system shall redirect the current user to the login screen after successful initialization.
## F2: Authentication and session management
### F2.1: Select a login role
- F2.1.1: The system shall provide Member and Exco login modes on the login screen.
- F2.1.2: The system shall allow the current user to toggle between Member and Exco login modes.
- F2.1.3: The system shall clearly indicate which login mode is currently selected.
- F2.1.4: The system shall request a username and password in Member login mode.
- F2.1.5: The system shall request the Exco password in Exco login mode.
### F2.2: Authenticate members
- F2.2.1: The system shall authenticate a member only when the supplied username and password match an active Member account.
- F2.2.2: The system shall treat Member usernames as case-sensitive during authentication.
- F2.2.3: The system shall display a generic authentication failure message when Member credentials are invalid.
- F2.2.4: The system shall grant authenticated members access only to Member functions.
### F2.3: Authenticate the Exco
- F2.3.1: The system shall authenticate the Exco using the password established during initial setup.
- F2.3.2: The system shall display a generic authentication failure message when the Exco password is invalid.
- F2.3.3: The system shall grant an authenticated Exco access only to Exco functions.
### F2.4: Manage authenticated sessions
- F2.4.1: The system shall establish an authenticated session following a successful login.
- F2.4.2: The system shall identify the role and account associated with the current session.
- F2.4.3: The system shall allow the current user to log out.
- F2.4.4: The system shall terminate the current session on logout and return to the login screen.
- F2.4.5: The system shall prevent a logged-out user from accessing authenticated functions.
## F3: Member account registration
### F3.1: Create a Member account
- F3.1.1: The system shall allow a prospective member to register an account using a username and password.
- F3.1.2: The system shall require the password to be entered twice during self-registration.
- F3.1.3: The system shall reject registration when the password and confirmation do not match.
- F3.1.4: The system shall reject registration when an existing Member account has the exact same case-sensitive username.
- F3.1.5: The system shall permit usernames that differ only by letter case, such as Member and member.
- F3.1.6: The system shall reject missing or invalid registration information and identify the fields requiring correction.
- F3.1.7: The system shall create the Member account only after all registration information has been validated.
## F4: Member equipment-loan requests
### F4.1: View equipment availability
- F4.1.1: The system shall allow an authenticated member to view equipment types available for loan.
- F4.1.2: The system shall distinguish usable units physically available in storage from availability for a requested date range.
- F4.1.3: The system shall identify an equipment type as unavailable for a requested date range when no unit can be reserved throughout that range.
### F4.2: Create a loan request
- F4.2.1: The system shall allow an authenticated member to create a request for one unit of an existing equipment type.
- F4.2.2: The system shall require a start date and end date for each request.
- F4.2.3: The system shall allow optional request details to be provided.
- F4.2.4: The system shall reject a request whose end date occurs before its start date.
- F4.2.5: The system shall reject a request when reserving one unit would cause availability to fall below zero on any date in the requested range.
- F4.2.6: The system shall associate the request with the authenticated Member account.
- F4.2.7: The system shall record the request with a Pending status after successful submission.
- F4.2.8: The system shall record the date and time at which the request was submitted.
### F4.3: View personal requests
- F4.3.1: The system shall allow a member to view only loan requests associated with their account.
- F4.3.2: The system shall display each request’s equipment type, requested loan dates, details and current status.
- F4.3.3: The system shall distinguish Pending, Approved, Rejected, Awaiting Return Confirmation, Closed and Cancelled requests, and indicate whether an Approved loan has been collected.
- F4.3.4: The system shall display an Exco-provided rejection reason when a request has been rejected.
## F5: Member equipment-return reporting
### F5.1: Determine return eligibility
- F5.1.1: The system shall allow a return report to be submitted only for a collected, approved loan belonging to the authenticated member.
- F5.1.2: The system shall prevent a return report from being submitted for an uncollected loan or a Pending, Rejected, Cancelled or Closed request.
- F5.1.3: The system shall prevent more than one return report from being associated with the same approved loan.
### F5.2: Report an undamaged return
- F5.2.1: The system shall allow the member to report that the equipment was returned without damage.
- F5.2.2: The system shall associate the No Damage return status with the loan.
- F5.2.3: The system shall place the loan into Awaiting Return Confirmation status after submission.
### F5.3: Report a damaged return
- F5.3.1: The system shall allow the member to report that the equipment was returned damaged.
- F5.3.2: The system shall require a damage description.
- F5.3.3: The system shall require at least one damage image.
- F5.3.4: The system shall reject a damaged-return report that does not contain both a description and an acceptable image.
- F5.3.5: The system shall associate the Damaged return status, description and image with the loan.
- F5.3.6: The system shall place the loan into Awaiting Return Confirmation status after submission.
### F5.4: Report lost equipment
- F5.4.1: The system shall allow the member to report that the equipment was not returned because it was lost.
- F5.4.2: The system shall require a description of the loss.
- F5.4.3: The system shall associate the Lost return status and description with the loan.
- F5.4.4: The system shall place the loan into Awaiting Return Confirmation status after submission.
## F6: Exco member administration
### F6.1: View Member accounts
- F6.1.1: The system shall allow the Exco to view a list of Member accounts.
- F6.1.2: The system shall allow the Exco to view the details of an individual Member account.
- F6.1.3: The system shall allow the Exco to locate Member accounts by username.
### F6.2: Create Member accounts
- F6.2.1: The system shall allow the Exco to create a Member account.
- F6.2.2: The system shall apply the same case-sensitive username uniqueness rules used for self-registration.
- F6.2.3: The system shall reject missing or invalid account information.
### F6.3: Update Member accounts
- F6.3.1: The system shall allow the Exco to update permitted Member account details.
- F6.3.2: The system shall reject a username change that would duplicate another Member account’s exact case-sensitive username.
### F6.4: Delete Member accounts
- F6.4.1: The system shall require confirmation before deleting a Member account.
- F6.4.2: The system shall remove the member's Pending requests and cancel and release all approved reservations that have not been collected as part of account deletion.
- F6.4.3: The system shall require the Exco to select and confirm a final return status for each collected outstanding loan, including loans Awaiting Return Confirmation, and resolve the corresponding stock adjustment as part of account deletion.
- F6.4.4: The system shall prevent a deleted Member account from authenticating.
- F6.4.5: The system shall require the same return evidence and damage-usability decision during member deletion as during regular Exco return confirmation.
## F7: Exco loan-request administration
### F7.1: View loan requests
- F7.1.1: The system shall allow the Exco to view loan requests from all Member accounts.
- F7.1.2: The system shall display the requesting member, equipment type, loan dates, details, submission time and current status.
- F7.1.3: The system shall allow requests to be filtered by status, member, equipment type or requested loan period.
- F7.1.4: The system shall allow the Exco to view the complete details of a selected request.
### F7.2: Approve loan requests
- F7.2.1: The system shall allow the Exco to approve a Pending request.
- F7.2.2: The system shall revalidate availability for every date in the requested range at the time approval is attempted.
- F7.2.3: The system shall prevent approval if reserving one unit would cause availability to fall below zero on any date in the requested range.
- F7.2.4: The system shall reserve one unit for the inclusive start-to-end date range when approval succeeds, without reducing physical stock until collection is recorded.
- F7.2.5: The system shall change the request status from Pending to Approved when approval succeeds.
- F7.2.6: The system shall record the date and time of approval.
- F7.2.7: The system shall ensure that repeated approval of the same request cannot reserve capacity more than once.
### F7.3: Reject loan requests
- F7.3.1: The system shall allow the Exco to reject a Pending request.
- F7.3.2: The system shall require a rejection reason.
- F7.3.3: The system shall change the request status from Pending to Rejected without changing equipment stock.
- F7.3.4: The system shall record the rejection reason and date and time of rejection.
### F7.4: Record equipment collection
- F7.4.1: The system shall allow the Exco to explicitly record collection of an approved, uncollected loan.
- F7.4.2: The system shall reduce usable stock in storage by one only when collection is recorded and shall prevent collection when no usable unit is physically available.
- F7.4.3: The system shall convert the existing reservation into a collected loan without counting the same unit twice against capacity.
- F7.4.4: The system shall prevent repeated collection attempts from reducing stock more than once.
### F7.5: Manage reservation availability
- F7.5.1: The system shall count both the start date and end date as occupied when evaluating overlapping reservations.
- F7.5.2: The system shall treat a unit scheduled for return on a given date as available for a subsequent reservation no earlier than the following date.
- F7.5.3: The system shall evaluate date-range availability using usable equipment capacity and overlapping approved reservations and collected loans; Pending requests shall not reserve capacity.
- F7.5.4: The system shall keep an overdue collected unit unavailable until Exco return confirmation, including while Awaiting Return Confirmation.
- F7.5.5: The system shall flag approved reservations affected by overdue loans or confirmed lost or unusable units and revalidate availability before further approvals or collection.
- F7.5.6: The system shall release reserved capacity when an uncollected reservation is cancelled without increasing physical stock.
## F8: Exco return confirmation
### F8.1: Review return reports
- F8.1.1: The system shall allow the Exco to view loans awaiting return confirmation.
- F8.1.2: The system shall display the member-selected return status.
- F8.1.3: The system shall display the submitted description and image when applicable.
### F8.2: Confirm an undamaged return
- F8.2.1: The system shall allow the Exco to select and confirm No Damage as the final return status during regular return confirmation or member deletion, including when it differs from the member-reported status.
- F8.2.2: The system shall increase usable stock in storage by one upon confirmation of a No Damage return.
- F8.2.3: The system shall change the loan status to Closed upon confirmation.
### F8.3: Confirm a damaged return
- F8.3.1: The system shall allow the Exco to select and confirm Damaged as the final return status during regular return confirmation or member deletion, including when it differs from the member-reported status.
- F8.3.2: The system shall require the Exco to decide whether the damaged unit remains usable.
- F8.3.3: The system shall increase usable stock in storage by one when the Exco confirms that the damaged unit remains usable.
- F8.3.4: The system shall leave usable stock in storage unchanged and remove the unit from reservable capacity when the Exco confirms that the damaged unit is unusable.
- F8.3.5: The system shall record the Exco’s stock decision with the return confirmation.
- F8.3.6: The system shall change the loan status to Closed upon confirmation.
- F8.3.7: The system shall require a damage description and at least one acceptable image for the Exco-selected Damaged status; the Exco shall supply any missing evidence when changing the member-reported status or resolving a loan during member deletion.
### F8.4: Confirm lost equipment
- F8.4.1: The system shall allow the Exco to select and confirm Lost as the final return status during regular return confirmation or member deletion, including when it differs from the member-reported status.
- F8.4.2: The system shall leave usable stock in storage unchanged and remove the lost unit from reservable capacity upon confirmation.
- F8.4.3: The system shall change the loan status to Closed upon confirmation.
- F8.4.4: The system shall require a loss description for the Exco-selected Lost status; the Exco shall supply it when absent, including when changing the member-reported status or resolving a loan during member deletion.
### F8.5: Preserve confirmation integrity
- F8.5.1: The system shall allow each return report to be confirmed no more than once.
- F8.5.2: The system shall ensure that repeated confirmation attempts cannot alter stock more than once.
- F8.5.3: The system shall record the date and time of return confirmation.
- F8.5.5: The system shall apply stock adjustments according to the Exco-confirmed final return status and maintain only one effective return status per loan.
## F9: Equipment and inventory administration
### F9.1: View equipment
- F9.1.1: The system shall allow the Exco to view all equipment types.
- F9.1.2: The system shall display each equipment type's name, usable quantity in storage and availability for a selected date range.
- F9.1.3: The system shall allow the Exco to view outstanding loans associated with an equipment type.
### F9.2: Create equipment types
- F9.2.1: The system shall allow the Exco to create an equipment type with a unique name and initial quantity.
- F9.2.2: The system shall reject an equipment type name that duplicates an existing name according to the defined equipment-name comparison rules.
- F9.2.3: The system shall reject a negative initial quantity.
### F9.3: Update equipment types
- F9.3.1: The system shall allow the Exco to update an equipment type’s name and details.
- F9.3.2: The system shall prevent an update from producing an invalid or duplicate equipment type name.
- F9.3.3: The system shall preserve outstanding loan references when an equipment type is renamed.
### F9.4: Delete equipment types
- F9.4.1: The system shall require confirmation before deleting an equipment type.
- F9.4.2: The system shall prevent deletion when the equipment type has a Pending, Approved or Awaiting Return Confirmation loan.
- F9.4.4: The system shall prevent members from creating new requests for a deleted equipment type.
### F9.5: Conduct stock takes
- F9.5.1: The system shall allow the Exco to enter a counted quantity of usable units physically in storage for each equipment type, excluding units on loan.
- F9.5.2: The system shall reject a negative counted quantity.
- F9.5.3: The system shall display the difference between the recorded quantity and counted quantity before applying an adjustment.
- F9.5.4: The system shall require confirmation before applying a stock-take adjustment.
- F9.5.5: The system shall update usable stock in storage to the confirmed counted quantity only if the adjustment leaves sufficient capacity for all approved future reservations.
### F9.6: Remove available units
- F9.6.1: The system shall allow the Exco to remove units only from usable stock currently in storage and shall require confirmation.
- F9.6.2: The system shall prevent removal of units that are on loan or Awaiting Return Confirmation.
- F9.6.3: The system shall prevent removal of more units than are physically available in storage.
- F9.6.4: The system shall block removal when it would leave insufficient capacity for any approved future reservation.
# Non-functional requirements
## N1: Security
### N1.1: Protect credentials
- N1.1.1: The system shall never store passwords in readable plaintext.
- N1.1.2: The system shall store passwords using a salted, adaptive, one-way password-protection mechanism.
- N1.1.5: The system shall not display or log entered passwords.
### N1.2: Enforce authorization
- N1.2.1: The system shall verify authorization before performing every protected operation.
- N1.2.2: The system shall prevent Member sessions from accessing Exco operations.
- N1.2.3: The system shall prevent members from viewing or modifying another member’s account or loan information.
- N1.2.4: The system shall deny protected operations when no valid authenticated session exists.
### N1.3: Limit authentication attacks
- N1.3.1: The system shall introduce a progressively increasing delay after repeated unsuccessful login attempts.
- N1.3.2: The system shall avoid revealing whether a particular Member username exists in authentication error messages.
- N1.3.3: The system shall clear password fields following an unsuccessful login attempt.
## N2: Platform compatibility and distribution
### N2.1: Runtime compatibility
- N2.1.1: The system shall run using JDK 25 FX Zulu on Apple Silicon Macs and JDK 25 on all other supported platforms.
- N2.1.2: The system shall use JavaFX 25.0.3, or the JavaFX version bundled with JDK 25 FX Zulu on Apple Silicon Macs.
- N2.1.3: The system shall exhibit equivalent functional behavior on supported Windows, macOS and Linux platforms.
### N2.2: Distribution
- N2.2.1: The application shall be distributable as a Fat JAR containing its non-platform-specific runtime dependencies.
- N2.2.2: The distributed application shall not require an internet connection to install, start or perform its core functions.
- N2.2.3: The project build shall reproducibly generate the distributable Fat JAR using Gradle and the Shadow plugin's shadowJar task.
- N2.2.4: Platform-specific JavaFX runtime components shall be provided or documented for each supported operating system where they cannot be contained in one platform-neutral artifact.
## N3: Data persistence and integrity
### N3.1: Local persistence
- N3.1.1: The system shall store all application data locally on the machine running the application.
- N3.1.2: The system shall retain accounts, equipment, current requests, reservations, collection state, return reports and associated images across application restarts.
- N3.1.3: The system shall use a local SQL database that supports relationships and integrity constraints.
- N3.1.4: The system shall operate without relying on a separately managed database server.
### N3.2: Transactional integrity
- N3.2.1: The system shall apply each approval, collection, return confirmation, stock adjustment and member deletion with its associated loan resolutions as an indivisible operation.
- N3.2.2: The system shall leave affected account, request, reservation and stock records unchanged if such an operation cannot be completed in full.
- N3.2.3: The system shall prevent physical stock from becoming negative and reject approvals or voluntary stock reductions that would overcommit capacity on any affected date.
- N3.2.4: The system shall preserve referential integrity between accounts, equipment, loan requests and return reports.
- N3.2.5: The system shall recover to a consistent persisted state following an unexpected shutdown.
## N4: Performance and capacity
### N4.2: Supported data volume
- N4.2.2: The system shall provide filtering or pagination when a complete result set cannot be displayed responsively.
## N5: Usability and accessibility
### N5.1: User feedback
- N5.1.1: The system shall clearly identify required input fields.
- N5.1.2: The system shall display actionable validation messages adjacent to, or clearly associated with, invalid fields.
- N5.1.3: The system shall display confirmation after successful creation, update, approval, return confirmation and stock adjustment.
- N5.1.4: The system shall require confirmation before destructive or irreversible operations.
### N5.2: Interaction consistency
- N5.2.1: The system shall use consistent terminology for request and return statuses throughout the interface.
- N5.2.2: The system shall visually distinguish unavailable equipment and actions that cannot currently be performed.
- N5.2.3: The system shall preserve entered form data when validation fails, except for password fields where retention would create a security risk.
### N5.3: Accessibility
- N5.3.1: The system shall make all core functions operable using a keyboard.
- N5.3.2: The system shall provide visible keyboard-focus indicators.
- N5.3.3: The system shall not use colour as the sole means of communicating status or errors.
- N5.3.4: The system shall provide accessible text descriptions for meaningful images and controls.
## N6: Image handling
### N6.1: Validate evidence images
- N6.1.1: The system shall accept commonly supported raster-image formats for damage evidence.
- N6.1.2: The system shall reject files that do not contain a valid supported image.
- N6.1.3: The system shall enforce a configurable maximum image size and inform the member when the limit is exceeded.
- N6.1.4: The system shall store damage images so that they remain accessible without relying on the original selected file.
### N6.2: Protect local files
- N6.2.1: The system shall prevent uploaded filenames from changing the intended storage location.
- N6.2.2: The system shall prevent uploaded images from being treated as executable application content.
- N6.2.3: The system shall restrict damage-image access to the associated member and the Exco.
## N7: Reliability
### N7.1: Error handling
- N7.1.1: The system shall handle recoverable errors without terminating the application.
- N7.1.2: The system shall present a comprehensible error message when an operation cannot be completed.
- N7.1.3: The system shall avoid exposing passwords, password representations or sensitive internal details in error messages.
- N7.1.4: The system shall record sufficient diagnostic information locally to support troubleshooting.
### N7.2: State consistency
- N7.2.1: The system shall ensure that each loan has no more than one active lifecycle status at a time.
- N7.2.2: The system shall ensure that each approved loan has no more than one return status.
- N7.2.3: The system shall ensure that an inventory change caused by a loan event is applied exactly once.
- N7.2.4: The system shall prevent application restart or repeated input from duplicating a completed transaction.
## N8: Privacy and local operation
### N8.1: Data locality
- N8.1.1: The system shall not transmit account, loan, inventory or damage-report data to an external service during normal operation.
- N8.1.2: The system shall make all core functionality available while the machine is offline.
- N8.1.3: The system shall disclose the local location through which application data can be administered.
### N8.2: Data minimization
- N8.2.1: The system shall collect only Member information necessary for account and equipment-loan administration.
- N8.2.2: The system shall avoid including sensitive credentials in diagnostic records.

# Deferred scope

Loan history, administrative audit history, backup and restore, and Member password reset are deferred. Numerical password-length, response-time and capacity targets, and a retention policy are deferred. Existing requirement identifiers are retained; gaps correspond to deferred requirements.
