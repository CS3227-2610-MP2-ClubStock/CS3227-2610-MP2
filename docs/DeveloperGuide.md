# Developer Guide

## JavaFX build setup

Use JDK 25 FX Zulu on Apple Silicon Macs and JDK 25 on other supported platforms.
The build uses JavaFX 25.0.3 and Gradle 9.1.0 through the committed wrapper.

Import the repository as a Gradle project and select JDK 25 as the Gradle JVM.
Run `./gradlew build` to build and `./gradlew shadowJar` to package dependencies
into a versioned JAR under `build/libs/`. On Windows, use `gradlew.bat`.
The first build requires network access to download Gradle and dependencies.

Dependencies follow the [SE-EDU JavaFX Part 1 guide](https://se-education.org/guides/tutorials/javaFxPart1.html),
using the project's JavaFX version. Native libraries target Windows x64, Linux x64,
and one macOS architecture per artifact, because macOS native filenames overlap.
The macOS classifier defaults to `mac-aarch64` on ARM hosts and `mac` otherwise.
Use `./gradlew shadowJar -PmacJavaFxPlatform=mac` for Intel macOS or
`./gradlew shadowJar -PmacJavaFxPlatform=mac-aarch64` for Apple Silicon.
The output classifier follows the selected macOS architecture:

| Output (version from `build.gradle`) | Target platforms |
| --- | --- |
| `ClubStock-0.1.0-desktop.jar` | Windows x64, Linux x64, Intel Mac |
| `ClubStock-0.1.0-apple-silicon.jar` | Apple Silicon Mac; also contains Windows x64 and Linux x64 libraries |

Run both commands above to produce both variants. Their filenames are distinct, so
they can coexist; do not run `clean` between them because it deletes both outputs.
Other architectures require matching native libraries.

The JARs bundle application dependencies, including JavaFX, but do not bundle Java.
Install Java 25 for the target architecture, then run, for example:

```sh
java -jar build/libs/ClubStock-0.1.0-desktop.jar
```

On Apple Silicon, use the `apple-silicon` JAR and an ARM64 Java 25 installation.

## Downloadable CI builds

Pull requests and pushes to `master` run the existing Windows, Linux and macOS
build/test matrix. After all build jobs succeed, packaging produces both JAR variants
and uploads intermediate `candidate-ClubStock-<variant>-<full commit SHA>` artifacts
for seven days. These candidates also appear on pull requests and are not verified
release downloads. Candidate names are stable within a workflow run so rerunning failed
jobs can reuse successful packaging outputs; rerunning packaging replaces its candidate.
If candidates have expired, rerun all jobs.

Four smoke jobs download those exact candidates and run `--verify-install` using Java 25:

| Platform | Runner | JAR variant |
| --- | --- | --- |
| Windows x64 | `windows-2025` | `desktop` |
| Linux x64 | `ubuntu-24.04` with Xvfb | `desktop` |
| Intel Mac | `macos-15-intel` | `desktop` |
| Apple Silicon | `macos-15` (ARM64) | `apple-silicon` |

Verification must exit successfully and print `INSTALL_VERIFICATION_OK`. Each command has
a two-minute step timeout, in addition to the application's 60-second watchdog, and each
smoke job has a ten-minute timeout. All four jobs run even if another platform fails.
Smoke jobs capture stdout/stderr and verbose Prism pipeline output in
`verification-<runner>-<full commit SHA>-<run attempt>` diagnostic artifacts retained for
seven days, including JVM fatal-error logs and macOS Java crash reports when generated.
Uploads run even after verification fails. Startup markers identify screen loading,
window creation, CSS, layout, snapshot, window closure, and toolkit shutdown.
Intel Mac also runs a separate software-rendering comparison (`-Dprism.order=sw`) even
if its default-rendering check fails. This comparison is diagnostic only: its success
cannot override a failed default check, and its failure does not block an otherwise
successful default check.

Only after all four pass on a push to `master` are the same candidate binaries uploaded
as final distribution artifacts. Pull requests never upload final distribution artifacts.

In GitHub, open **Actions → Java CI with Gradle → the successful master run → Artifacts**.
Download the final artifact without the `candidate-` prefix and extract the ZIP before
running the JAR with Java 25.
Artifact names are `ClubStock-<variant>-<full commit SHA>-<run attempt>`, linking each
download to its source and avoiding collisions when a workflow is rerun. They are
retained for 30 days. Missing JARs cause the upload step to fail.

These are development builds, not published GitHub Releases. Packaging runs on Linux,
then the actual JARs are checked on all four target platforms. Smoke checks cover startup
and installation dependencies, not complete business workflows. A manually gated release
workflow remains a subsequent pipeline stage.

## Current implementation and roadmap

`src/main/java/clubstock/Launcher.java` starts `ClubStockApplication` from a separate,
non-`Application` main class. The Gradle `application` plugin uses `clubstock.Launcher`
as its main class. Use `./gradlew run` to launch it.

The core domain and SLICE-001 SQLite foundation exist under `clubstock.domain`,
`clubstock.application` and `clubstock.infrastructure`. Persistence tests use temporary
databases. The FXML shell now initializes `clubstock.db` under `${user.home}/.clubstock`,
or the directory selected by the `clubstock.dataDir` system property, and provides role
selection, Exco setup/login views, Member login, guarded role hosts, logout wiring and shared CSS.
`ApplicationContext` composes PBKDF2 password hashing, account authentication, the in-memory
session manager and the UI authentication adapter around the same SQLite database. A fresh
installation routes Exco to one-time password setup; configured Exco accounts and active
Exco-created Members can then sign in.

Follow the [parallel implementation roadmap](plans/parallel-role-implementation.md)
for ownership and delivery. The [shared application design](plans/shared-application-design.md)
defines authentication, evidence storage and packaging verification. The noninteractive
`--verify-install` mode is described below and is run by the separate packaged smoke matrix
after the build/test matrix succeeds.


## Verify a packaged installation

With Java 25 installed for the host architecture, run the matching packaged JAR:

```sh
java -jar build/libs/ClubStock-0.1.0-desktop.jar --verify-install
```

On Apple Silicon:

```sh
java -jar build/libs/ClubStock-0.1.0-apple-silicon.jar --verify-install
```

Verification requires a working graphical display and may briefly show the startup window.
It is noninteractive, not display-free. Linux CI uses Xvfb to supply its virtual display.

The command creates disposable storage, ignoring `clubstock.dataDir` and the normal user
application directory. It initializes SQLite, commits Exco setup using a disposable credential,
reopens the context to verify persistence, reads every routed FXML and the shared CSS, and
loads and snapshots the real role-selection screen. It does not load every role screen or
verify complete Member/Exco workflows.

Progress is printed by phase. Success prints `INSTALL_VERIFICATION_OK` only after cleanup
and JavaFX shutdown. Failures print diagnostics to stderr without blocking error dialogs.

| Exit status | Meaning |
| --- | --- |
| `0` | All checks and cleanup succeeded |
| `1` | Startup, verification, or cleanup failed |
| `2` | Extra arguments accompanied `--verify-install` |
| `3` | The 60-second verification timeout expired |

Temporary storage is removed after normal success or failure. Cleanup errors report the
remaining directory; timeout or forced termination may leave temporary files behind. A passing
local run validates only that host platform. Check the four CI smoke-job results for
cross-platform evidence; changing the workflow locally does not establish those results.
