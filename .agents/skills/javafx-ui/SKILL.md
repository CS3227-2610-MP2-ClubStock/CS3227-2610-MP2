---
name: javafx-ui
description: Create or refine ClubStock JavaFX screens, dialogs, layouts, and styles for Member or Exco workflows. Use for desktop UI implementation or visual polish; not for web pages or business-rule-only changes.
---

# ClubStock JavaFX UI

Build a usable desktop interface for the requested workflow, with role boundaries and state feedback grounded in the project specifications.

## Establish the screen contract

- Follow the repository's [AGENTS.md](../../../AGENTS.md). Read the [project overview](../../../docs/ProjectDescription.md), [requirements](../../../docs/ProjectRequirements.md), and [shared specification](../../../docs/Shared.md), plus the affected [Exco](../../../docs/ExcoSpec.md) or [Member](../../../docs/MemberSpec.md) specification.
- Inspect the current entry point, navigation, controllers, resources, and services before choosing a layout. Work within the existing approach; FXML is an option, not a requirement.
- Identify the actor, visible data, permitted actions, relevant requirement IDs, and feedback after each action. Read both role specifications when the screen exposes a shared transition.
- Flag conflicting requirements before implementing affected behavior. In particular, check the overview against the role specifications for registration and zero-stock requests. Do not resolve those differences by designing a screen around one document silently.
- Treat layout, spacing, and colors as implementation choices unless the user provides a design. Requirements section 5 contains unresolved product decisions; do not turn those into assumed validation rules or extra required fields.

## Shape the interaction

- Give the screen a clear title and primary action. Use tables for request queues or item inventories, a detail pane or dialog for assessment, and labeled forms for data entry when they fit the task.
- Reuse the app's spacing, typography, styles, and navigation. If none exist, establish a small consistent stylesheet for the requested screens instead of introducing a UI framework.
- Model the states the screen actually needs: empty data, no selection, invalid input, operation in progress, success, and failure. Preserve entered values when an operation fails, and explain why an action is unavailable.
- Use resizable layout containers and table constraints instead of fixed coordinates. Keep labels readable, keyboard focus visible, form traversal sensible, and status understandable without relying only on color.
- Keep Member and Exco navigation appropriate to their roles. UI visibility complements service authorization; hiding a control is not the only enforcement.

## Connect JavaFX to shared state

- Follow the configured JDK and JavaFX versions in AGENTS.md and the build. Preserve the separate non-Application launcher used for packaged execution.
- Put source under the existing Java source tree and CSS/FXML/images under classpath resources. Resolve resources through the classpath rather than a working-directory path so packaged execution can find them.
- Use JavaFX CSS properties and controls. Do not paste browser CSS or assume that HTML layout behavior applies.
- Controllers should translate input, call shared services, and display results. Keep allocation, stock counts, authorization, and state transitions out of view code.
- Refresh or bind affected views to the same shared data after a successful mutation. A switch between roles must not show an independent, stale inventory.
- Keep blocking storage or image work off the JavaFX application thread when relevant. Apply UI updates on that thread; scope listeners and background work to the view's lifetime.

## Check the relevant domain details

Consult the linked specifications for exact rules; these are common UI traps:

- Member browsing and requests concern EquipmentTypes. Individual IDs appear only for equipment assigned to that Member.
- Check the zero-stock rule conflict before implementing that flow. The shared and Member specifications call for a warning before submission, while still allowing a valid request.
- Request approval selects specific available items and displays the approved quantity. Do not represent an individual Loan with an APPROVED state.
- Return and Report Lost are separate actions for individual Loans. Damage reporting needs an image and description; loss reporting needs a description.
- Member reports and Exco-verified condition are distinct. Exco's damaged-return workflow includes the availability decision.
- Overdue is a visible indicator on an ON_LOAN Loan, not an added Loan status.

## Verify the screen

Compile the changed code with the repository wrapper. When desktop interaction is available, open the affected flow with disposable data and inspect navigation, resizing, keyboard operation, validation, and the relevant state changes. Check resource loading in the packaged JAR when resource paths or packaging changed.

Use native desktop inspection for JavaFX when available; a browser preview cannot verify the running desktop app. If launching or inspecting it is unavailable, report that limit and provide focused manual checks. Distinguish compilation from visual verification.
