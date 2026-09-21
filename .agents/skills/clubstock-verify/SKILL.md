---
name: clubstock-verify
description: Verify ClubStock changes with focused Gradle checks, packaged JavaFX smoke checks, and specification-based workflow scenarios. Use for test requests, build or runtime failures, and validation of behavior changes; a review alone does not authorize feature edits.
---

# ClubStock Verification

Select checks that establish the requested behavior and expose cross-role state errors. Report build, test, and desktop evidence separately.

## Scope the checks

Read [AGENTS.md](../../../AGENTS.md), the [project overview](../../../docs/ProjectDescription.md), [requirements](../../../docs/ProjectRequirements.md), [shared specification](../../../docs/Shared.md), and affected role specifications: [Exco](../../../docs/ExcoSpec.md), [Member](../../../docs/MemberSpec.md).

Inspect the diff or requested feature, build configuration, test sources, and CI workflow. Select affected requirements instead of running every scenario for each edit. For request, allocation, return, or loss behavior, use the relevant cases in [references/workflow-scenarios.md](references/workflow-scenarios.md).

Flag conflicting statements and unresolved product decisions before treating either interpretation as a test oracle. A missing policy is not a demonstrated implementation defect. If the request is review-only, report findings; do not silently change application behavior.

## Build and runtime checks

Run commands from the repository root with the committed wrapper. Check current task names and configuration before using these examples:

| Purpose | Windows PowerShell | macOS/Linux |
| --- | --- | --- |
| Inspect Gradle JVM | .\gradlew.bat --version | ./gradlew --version |
| Compile changed Java/resources | .\gradlew.bat classes | ./gradlew classes |
| Run configured tests | .\gradlew.bat test | ./gradlew test |
| Run build checks | .\gradlew.bat build | ./gradlew build |
| Package the application | .\gradlew.bat shadowJar | ./gradlew shadowJar |
| Launch from Gradle | .\gradlew.bat run | ./gradlew run |

- Follow AGENTS.md for JDK 25 and platform-specific JavaFX setup. Inspect build.gradle and gradle/wrapper/gradle-wrapper.properties rather than upgrading dependencies to make a check pass.
- Check the actual test source set and runner. A successful build or a test task reporting NO-SOURCE does not demonstrate passing domain tests.
- Use a targeted test filter when the suite supports it. Add meaningful regression coverage when fixing a substantive defect; do not add tests that merely check edited text or mirror implementation.
- Do not run every command in the table automatically. Use compilation for Java wiring, relevant tests for rules, and packaging/launch checks for resource or distribution changes.
- Check the build's current archive name under build/libs. Launch that artifact with java -jar when packaged runtime behavior matters; compilation alone cannot catch missing FXML, CSS, images, or launcher errors.
- Preserve the configured non-Application Launcher entry point. Check it and classpath resource loading when Gradle run works but the JAR fails.
- In the current build, macJavaFxPlatform selects mac or mac-aarch64. Build only one macOS native variant into an artifact; alternate variants overwrite the same output path, so preserve an artifact before creating another.
- A local launch validates only that OS/architecture. Do not infer Windows, Linux, and both macOS variants work from a single host.
- Distinguish source failures from a missing JDK, dependency/network failures, or unavailable GUI access. Use --stacktrace for an unexplained Gradle failure; report the actionable cause without changing unrelated environment settings.

The [Developer Guide](../../../docs/DeveloperGuide.md) supplies build context, but confirm descriptions against current source. Its statement that no launcher or application exists may be stale.

## Exercise shared workflows

Use disposable fixtures or temporary storage. Follow the existing repository implementation; do not reset a user's database, alter their inventory, or add demo records to normal startup.

For an affected transition, inspect both the direct result and related records: request status, approved quantity, Loan count/status, item condition/availability, and the other role's view. For a rejected action, verify that these remain unchanged.

Exercise invalid operations through the service boundary where possible as well as checking disabled UI actions. Keep business-rule tests independent of JavaFX; use desktop checks for rendering, navigation, validation presentation, and resources. Use an existing UI automation stack when available rather than introducing one just for a smoke check.

## Report the evidence

State which checks ran, their outcome, and which were not executable. For defects, include a reproduction, expected behavior with the requirement reference, observed behavior, and the relevant file or operation.

If GUI access or fixtures are unavailable, supply a focused manual scenario and mark it unexecuted. Report test absence plainly. A documentation-only change normally needs structural/link checks, not an application build.
