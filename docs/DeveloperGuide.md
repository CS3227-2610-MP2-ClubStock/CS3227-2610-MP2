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
build/test matrix. After every build job succeeds on a push to `master`, a separate
packaging matrix builds both explicit macOS variants and uploads their JARs.
Pull requests do not upload distribution artifacts.

In GitHub, open **Actions → Java CI with Gradle → the successful master run → Artifacts**.
Download the desired variant and extract the ZIP before running the JAR with Java 25.
Artifact names are `ClubStock-<variant>-<full commit SHA>-<run attempt>`, linking each
download to its source and avoiding collisions when a workflow is rerun. They are
retained for 30 days. Missing JARs cause the upload step to fail.

These are development builds, not published GitHub Releases. Packaging runs on Linux
and includes the selected native libraries; it does not establish that the actual
JAR launches on every target platform. Packaged smoke checks and a manually gated
release workflow are subsequent pipeline stages.

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
`--verify-install` mode and comprehensive cross-platform packaging checks remain future work and
must not be treated as available until implemented.
