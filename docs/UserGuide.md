---
title: User Guide
---

# ClubStock User Guide

ClubStock is a desktop application for managing club equipment on one computer. Members
request equipment and report returns or losses. Exco manages accounts and inventory,
approves requests, and verifies returned or lost items. Both roles share the same local data.

## Contents

- [Getting Started](#getting-started)
- [Overview of Interface](#overview-of-interface)
- [Features](#features)
- [Frequently Asked Questions](#frequently-asked-questions)

## Getting Started

### Installing dependencies

You need a graphical desktop and Java 25 to run ClubStock. Use the Java installation that
matches your operating system and processor:

| Computer | Java installation | ClubStock JAR variant |
| --- | --- | --- |
| Apple Silicon Mac | JDK 25 FX Zulu, ARM64 | `apple-silicon` |
| Intel Mac | JDK 25, x64 | `desktop` |
| Windows x64 | JDK 25, x64 | `desktop` |
| Linux x64 | JDK 25, x64 | `desktop` |

Install JDK 25 FX Zulu on Apple Silicon Macs as required by this project. For other listed
platforms, install JDK 25 for your operating system. Follow the JDK installer's instructions,
then open Terminal or PowerShell and check:

```sh
java -version
```

The output should identify Java 25. If `java` is not found, add the installed JDK's `bin`
directory to your `PATH`, then reopen the terminal. If an older version appears, update
`PATH` to select Java 25.

The distributed JAR includes JavaFX 25.0.3 and the application's other libraries. You do
not need to install Gradle, a database server, or a separate JavaFX SDK to run it. Java
itself is not included in the JAR.

### Downloading the JAR

1. Open the project's [Releases page](https://github.com/CS3227-2610-MP2-ClubStock/CS3227-2610-MP2/releases).
2. Open the release supplied for your club and expand **Assets**.
3. Download the JAR variant from the table above. Do not choose the source-code ZIP or TAR archive.
4. Save the JAR in a folder you can easily locate, such as a `ClubStock` folder in Downloads.

The current repository version is `0.4.0`, whose package names are
`ClubStock-0.4.0-desktop.jar` and `ClubStock-0.4.0-apple-silicon.jar`. Release availability
may differ from the repository version. If no suitable JAR is published, obtain the packaged
JAR from the project team. Developers can use the [Developer Guide](DeveloperGuide.md) to
build one.

### Running the downloaded JAR

Open Terminal or PowerShell in the folder containing the downloaded JAR. For Windows x64,
Linux x64, or Intel macOS, run:

```sh
java -jar ClubStock-0.4.0-desktop.jar
```

For Apple Silicon macOS, run:

```sh
java -jar ClubStock-0.4.0-apple-silicon.jar
```

Replace the filename with the exact name of your downloaded JAR if its version differs.
ClubStock opens the role-selection screen. There is no separate application installer.

### First-time setup and signing in

1. On a new installation, choose **Exco** and set the Exco password. There is no default
   Exco password. Passwords must contain at least eight characters and cannot consist only
   of whitespace.
2. Exco creates Member accounts through **Manage Members**, providing each Member with
   their Member ID and password.
3. Exco uses **Manage inventory** to create equipment types, offer them to Members, add
   physical items, and release suitable items for borrowing.
4. Members choose **Member** on the role-selection screen, enter their credentials, and
   select **Sign in**. Member IDs are case-sensitive.

Use **Log out** on your home screen when finished, especially before another person uses
the application. Restarting ClubStock also requires you to sign in again.

### Saved data

ClubStock saves accounts, equipment, requests, loans, and damage evidence locally in the
`.clubstock` folder inside your user home directory. Closing the application preserves this
data. Members and Exco must use the same application data on the same computer; separate
computers do not automatically synchronise.

To back up your data, close every running ClubStock instance and copy the entire `.clubstock`
folder, including its database and damage images, to a separate backup location.

## Overview of Interface

The screenshot placeholders below describe the images to add when preparing the final guide.

### Role selection and sign-in screens

Choose **Member** or **Exco**. Members sign in using a Member ID and password; Exco signs in
using its password. The Exco screen prompts for password setup on first use.

> **Screenshot placeholder:** Role-selection screen showing the Member and Exco buttons.

> **Screenshot placeholder:** Member sign-in screen and Exco first-time password setup.

### Member home: Equipment catalogue

The catalogue shows equipment types offered for borrowing and their available quantities.
Use **New request**, **My requests**, or **My loans** to open the corresponding screen.
**Refresh** reloads the catalogue, and **Log out** ends the session.

> **Screenshot placeholder:** Member catalogue with equipment types, available quantities,
> and the navigation buttons, including a type with zero available stock.

### Member menu: New loan request

Choose an equipment type, enter a quantity and dates, and optionally add details. **Review
request** displays a summary before **Confirm and submit**. A zero-stock request includes
an acknowledgement checkbox.

> **Screenshot placeholder:** New loan request form and review summary with the zero-stock warning.

### Member menu: My requests

The **Your requests** screen lists your submitted requests, their dates and statuses, and
approved quantities where applicable. Select a pending request to use **Cancel selected
request**. Use **Refresh** to reload the list.

> **Screenshot placeholder:** Request list showing pending, approved, rejected, and cancelled
> examples, with a pending request selected.

### Member menu: My loans

Each assigned item has its own row, including its Equipment ID, loan status, dates, and
an overdue indicator. Select an item to use **Return** or **Report Lost**.

> **Screenshot placeholder:** My loans table with one overdue item and the Return and Report Lost buttons.

> **Screenshot placeholder:** Damaged-return form with image selection and the separate lost-report form.

### Exco home menu

The Exco home screen provides **Manage Members**, **Manage inventory**, **Manage pending
requests**, **View active loans**, and **Verify pending reports**. **Log out** ends the session.

> **Screenshot placeholder:** Exco home showing all five workflow buttons.

### Exco menu: Manage Members

View Member accounts and create, edit, or remove a Member. Editing supports changing the
Member's name or replacing their password; the Member ID remains fixed.

> **Screenshot placeholder:** Member administration screen and the create/edit Member dialog.

### Exco menu: Manage inventory

The inventory screen lists equipment types and individual physical items. It shows type
visibility and available quantities, plus each item's Equipment ID, condition, and availability.
Select a type or item to manage it.

> **Screenshot placeholder:** Inventory administration showing the equipment-type and physical-item tables.

### Exco menu: Manage pending requests

The pending-request queue shows the Member, equipment type, requested quantity, available
quantity, and submission time. Select a request to inspect its dates and details, then
approve or reject it. Approval opens item selection.

> **Screenshot placeholder:** Pending-request queue with selected request details and approval controls.

> **Screenshot placeholder:** Approval dialog showing selectable available Equipment IDs.

### Exco menu: View active loans

The active-loan screen lists unresolved loans, their Members, assigned Equipment IDs,
statuses, start times, end dates, and overdue indicators.

> **Screenshot placeholder:** Exco active-loan table containing an overdue loan and a pending return.

### Exco menu: Verify pending reports

Select a return or loss report to review its details and any damage image. Exco can verify
a good return, choose availability for a damaged return, or confirm a lost item.

> **Screenshot placeholder:** Report-verification screen with report details, damage-image access,
> and verification buttons.

## Features

### Request equipment as a Member

1. From the catalogue, select **New request**.
2. Choose the equipment type and enter a positive whole-number quantity.
3. Select the requested start and end dates. The end date must be on or after the start date.
4. Add optional details, then select **Review request**.
5. Check the summary. If no items are available, acknowledge the zero-stock warning.
6. Select **Confirm and submit**. The request starts as `PENDING`.

A request does not reserve equipment. The requested start date helps Exco assess your needs;
the actual loan begins immediately when Exco approves it. Exco chooses individual items,
so their Equipment IDs become visible to you only after allocation.

### Track or cancel requests

Open **My requests** to view your requests and approved quantities.

| Request status | Meaning |
| --- | --- |
| `PENDING` | Awaiting Exco's decision; you may cancel it. |
| `APPROVED` | Exco assigned one or more items. Check My loans. |
| `REJECTED` | The request was rejected manually or because another approval exhausted stock. |
| `CANCELLED` | You cancelled the pending request. |

To cancel, select your pending request and choose **Cancel selected request**. Approved,
rejected, and cancelled requests cannot be cancelled or reopened. If only part of your
requested quantity is approved, submit a new request for any additional items you still need.

### Return an item or report a loss

Open **My loans** and select an item whose status is `ON_LOAN`.

- **Good return:** Select **Return**, choose **Good condition**, and select **Submit Return**.
- **Damaged return:** Select **Return**, choose **Damaged**, enter a description, and choose
  a JPEG or PNG image from 1 byte through 5 MiB. Neither side may exceed 10,000 pixels, and
  the image may contain at most 16 million pixels total. Select **Submit Return**.
- **Lost item:** Select **Report Lost**, enter a description, and select **Submit Lost Report**.

Handle each item separately, even when several items came from the same request. A return
changes its loan to `RETURN_PENDING`; a loss report changes it to `LOST_PENDING`. The item
remains unavailable until Exco verifies the report. You cannot submit another return or
loss report for that pending loan.

| Loan status | Meaning |
| --- | --- |
| `ON_LOAN` | The item is assigned to you and can be returned or reported lost. |
| `RETURN_PENDING` | Exco must verify the submitted return. |
| `LOST_PENDING` | Exco must verify the submitted loss report. |
| `COMPLETED` | Exco has resolved the return or loss. It is no longer an active loan. |

An `ON_LOAN` item is overdue after its end date. ClubStock does not extend loans or apply
automatic fines. Contact Exco to arrange any overdue follow-up.

### Manage Member accounts as Exco

Open **Manage Members** to create an account with a unique Member ID, name, and password.
Provide those credentials to the Member. You can later change the name or replace the
password, but cannot change the Member ID. Removal is blocked when it would leave unresolved
requests or loans without a valid Member.

### Manage equipment as Exco

1. Open **Manage inventory** and choose **Create equipment type**.
2. Select the type and use **Manage selected type** to offer it to Members.
3. Use **Add item to selected type** to add each physical item with a unique Equipment ID.
4. Select an item and use **Manage selected item** to release it when suitable for borrowing.

New types start unoffered. New items start in `GOOD` condition with `UNAVAILABLE` availability;
adding an item does not automatically release it.

Only `AVAILABLE` items count towards the catalogue's available quantity and can be allocated.
`ON_LOAN` items are already assigned, and `UNAVAILABLE` items cannot be allocated. A damaged
item may be available if Exco judges it suitable, but a lost item must remain unavailable.
An item cannot be removed while an unresolved loan references it.

### Approve or reject requests as Exco

1. Open **Manage pending requests** and select a pending request.
2. Review the Member, quantity, dates, details, and current availability.
3. To approve, choose **Approve selected request**, select available items of the requested
   type, and confirm the allocation. To reject, choose **Reject selected request** and
   follow the confirmation prompt.

Approval requires at least one available item. You may approve fewer items than requested,
but cannot exceed the requested quantity. Each assigned item creates a separate loan and
immediately becomes `ON_LOAN`.

If approval uses the last available item of that type, all other requests currently pending
for the same type are automatically rejected. They do not reopen when stock returns.

### Monitor loans and verify reports as Exco

Use **View active loans** to review unresolved loans and follow up with overdue Members.
Loan end dates cannot be changed, and active loans cannot be cancelled.

Use **Verify pending reports** to select a report, inspect the returned item or loss details,
and review any submitted damage image. Choose the appropriate outcome:

| Verification outcome | Final item condition | Final availability |
| --- | --- | --- |
| Good return | `GOOD` | `AVAILABLE` |
| Damaged return, suitable for borrowing | `DAMAGED` | `AVAILABLE` |
| Damaged return, unsuitable for borrowing | `DAMAGED` | `UNAVAILABLE` |
| Confirmed loss | `LOST` | `UNAVAILABLE` |

Each verification completes that individual loan. The Member's reported condition is
advisory; Exco determines the final condition and availability.

## Frequently Asked Questions

### Why does the JAR not open?

Run it from Terminal or PowerShell so you can read the error. Check `java -version` reports
Java 25 and that the JAR variant matches your processor, especially on macOS. Use a graphical
desktop session. An “Unable to access jarfile” error usually means the filename or current
folder is wrong; navigate to the download folder or supply the full JAR path in quotes.

### Do I need an internet connection?

You need internet access to download Java and the application. Normal ClubStock use stores
data locally and does not require an online account or database service.

### Can I register my own Member account or reset my password?

Exco creates Member accounts and can replace Member passwords. Ask Exco for help if you
cannot sign in. There is no default Exco password or built-in Exco password-recovery screen.

### Why is the catalogue empty or my new equipment unavailable?

Exco must offer an equipment type before Members can see it. Exco must also explicitly
release newly added items before they count as available. An offered type with zero stock
still appears in the catalogue. Refresh the view after inventory changes.

### Can I request equipment when the available quantity is zero?

Yes. Acknowledge the warning before submitting. The request remains subject to Exco approval,
and Exco cannot approve it until at least one suitable item is available.

### Why was my request rejected, or why did I receive fewer items?

Exco may reject a request or approve fewer items than requested. A request is also rejected
automatically if another approval exhausts that type's available stock while your request
is pending. Submit a new request if you still need equipment; rejected requests and the
unfulfilled portion of partial approvals do not remain pending.

### Why can I no longer cancel my request or return my item?

Only pending requests can be cancelled. An approved request has already created individual
loans, which must be resolved through return or loss reporting. Return and loss actions are
available only for `ON_LOAN` items, so a submitted report cannot be submitted again while
awaiting verification.

### Why is my returned item still pending?

Exco must verify the return before the loan completes and the item's final condition and
availability are set. Submitting a report alone does not make the item available again.

### Why is my damage image rejected?

Choose a valid, non-empty JPEG or PNG file from 1 byte through 5 MiB. Neither side may exceed
10,000 pixels, and the image may contain at most 16 million pixels total. Provide a damage
description. Resize high-resolution images to meet both dimension limits; reducing file size
alone does not lower the pixel count. Renaming another file format to `.jpg` or `.png` does
not convert it.

### Can I extend a loan or cancel an active loan?

No. The loan end date is fixed after approval. Resolve an active loan through the return or
loss workflow and contact Exco about any practical arrangements.

### Where is my data, and will downloading a new JAR erase it?

Data is stored separately from the JAR in your user home directory's `.clubstock` folder.
Back up the entire folder with ClubStock closed before upgrading. Follow the release's
upgrade instructions and use the same data directory to retain your records.

### Why does another computer show different records?

ClubStock is a single-computer application. Each computer or operating-system user normally
has its own local data. Signing in on another computer does not synchronise the club's records.
