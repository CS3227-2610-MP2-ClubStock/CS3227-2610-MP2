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

Application source and a launcher have not been added yet. The Fat JAR currently
packages dependencies only and is not runnable. When implementing the GUI, add
sources under `src/main/java` and resources under `src/main/resources`, apply the
Gradle `application` plugin, and configure its `mainClass` to a launcher that does
not extend JavaFX `Application`, as described in the guide.
