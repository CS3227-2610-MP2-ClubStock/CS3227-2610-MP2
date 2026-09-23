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

`src/main/java/Launcher.java` starts the JavaFX `Main` application. The Gradle
`application` plugin uses `Launcher` as its main class, and the application still
shows the Hello World demonstration screen. Use `./gradlew run` to launch it.

The core domain and SLICE-001 SQLite foundation exist under `clubstock.domain`,
`clubstock.application` and `clubstock.infrastructure`. Persistence tests use temporary
databases. The demonstration UI does not yet compose those services or open an
application database; authentication and feature screens are planned work.

Follow the [parallel implementation roadmap](plans/parallel-role-implementation.md)
for ownership and delivery. The [shared application design](plans/shared-application-design.md)
defines the planned data directory, authentication, evidence storage and packaging
verification. Those future startup and `--verify-install` behaviors must not be treated
as available until implemented. Update this guide as the real shell and features land.
