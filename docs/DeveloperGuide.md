# Developer Guide

## JavaFX build setup

Use JDK 25 FX Zulu on Apple Silicon Macs and JDK 25 on other supported platforms.
The build uses JavaFX 25.0.3 and Gradle 9.1.0 through the committed wrapper.

Import the repository as a Gradle project and select JDK 25 as the Gradle JVM.
Run `./gradlew build` to build and `./gradlew shadowJar` to package dependencies
into `build/libs/ClubStock-0.1.0-all.jar`. On Windows, use `gradlew.bat`.
The first build requires network access to download Gradle and dependencies.

Dependencies follow the [SE-EDU JavaFX Part 1 guide](https://se-education.org/guides/tutorials/javaFxPart1.html),
using the project's JavaFX version. Native libraries target Windows x64, Linux x64,
and one macOS architecture per artifact, because macOS native filenames overlap.
The macOS classifier defaults to `mac-aarch64` on ARM hosts and `mac` otherwise.
Use `./gradlew shadowJar -PmacJavaFxPlatform=mac` for Intel macOS or
`./gradlew shadowJar -PmacJavaFxPlatform=mac-aarch64` for Apple Silicon.
These commands overwrite the same output JAR, so copy it before building another variant.
Other architectures require matching native libraries.

## Current implementation and roadmap

`src/main/java/clubstock/Launcher.java` starts `ClubStockApplication` from a separate,
non-`Application` main class. The Gradle `application` plugin uses `clubstock.Launcher`
as its main class. Use `./gradlew run` to launch it.

The core domain and SLICE-001 SQLite foundation exist under `clubstock.domain`,
`clubstock.application` and `clubstock.infrastructure`. Persistence tests use temporary
databases. The FXML shell now initializes `clubstock.db` under `${user.home}/.clubstock`,
or the directory selected by the `clubstock.dataDir` system property, and provides role
selection, Exco setup/login views, guarded role hosts, logout wiring and shared CSS.

The shell consumes authentication through `AuthenticationGateway`. Until Keith's shared
authentication/session implementation and Member-login view are integrated, startup uses a safe
unavailable adapter: the shell can be inspected, but real Exco or Member authentication is not yet
available. The Member-login FXML is an explicit non-authenticating integration placeholder.

Follow the [parallel implementation roadmap](plans/parallel-role-implementation.md)
for ownership and delivery. The [shared application design](plans/shared-application-design.md)
defines authentication, evidence storage and packaging verification. The noninteractive
`--verify-install` mode and comprehensive cross-platform packaging checks remain future work and
must not be treated as available until implemented.
