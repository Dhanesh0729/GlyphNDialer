Gradle Wrapper
==============

This directory holds the Gradle wrapper configuration for Glyph Dialer.

Files
-----
- gradle-wrapper.properties  (tracked)   — pins the Gradle distribution (8.10.2).
- gradle-wrapper.jar         (REQUIRED)  — the wrapper bootstrap binary.

IMPORTANT: gradle-wrapper.jar is a BINARY and is intentionally NOT committed by
the code generators (binaries can't be authored as text). You must materialise it
before the wrapper scripts (../../gradlew / ../../gradlew.bat) will work.

How to obtain gradle-wrapper.jar
--------------------------------
Pick ONE of:

1. Open the project in Android Studio. On Gradle sync, Studio detects the
   wrapper config and generates gradle-wrapper.jar automatically.

2. With a system-installed Gradle (any 8.x), from the project root run:

       gradle wrapper --gradle-version 8.10.2 --distribution-type bin

   This regenerates gradle-wrapper.jar plus the scripts, matching the version
   pinned in gradle-wrapper.properties.

3. Copy gradle/wrapper/gradle-wrapper.jar from any other Gradle 8.10.2 project.

Verification
------------
After the jar is present:

       ./gradlew --version        (macOS/Linux)
       .\gradlew.bat --version    (Windows)

should print "Gradle 8.10.2".

The pinned distributionUrl in gradle-wrapper.properties matches the Gradle
version expected by AGP 8.7.2 (see gradle/libs.versions.toml).
