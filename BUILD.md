# Building Glyph Dialer

This guide covers prerequisites, materializing the Gradle wrapper, signing,
the Glyph debug toggle, and dropping in the Nothing GDK AAR. For the high-level
overview see the *Building* section of [README.md](README.md).

---

## 1. Prerequisites

| Requirement | Value |
|---|---|
| JDK | **17** (`JavaVersion.VERSION_17`, `jvmTarget = "17"`) |
| Android Studio | Latest stable (Giraffe/Koala or newer) |
| Android SDK | `compileSdk` / `targetSdk` = **35**, `minSdk` = **29** |
| Gradle | **8.10.2** (via the wrapper) |
| AGP | 8.7.2 (pinned in `gradle/libs.versions.toml`) |
| Kotlin | 2.0.21 (Compose compiler plugin) |

All dependency versions are pinned in the version catalog
(`gradle/libs.versions.toml`) — the single source of truth. Do not add raw
coordinates in module build files.

### Point Gradle at your SDK

Create `local.properties` at the repo root (Android Studio writes this on first
sync):

```properties
# Windows
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
# macOS
# sdk.dir=/Users/<you>/Library/Android/sdk
# Linux
# sdk.dir=/home/<you>/Android/Sdk
```

`local.properties` is machine-specific and must **not** be committed.

---

## 2. Materialize the Gradle wrapper jar

`gradle/wrapper/gradle-wrapper.jar` is a binary and may not be present in a fresh
checkout. You need it before `./gradlew` works.

- **Android Studio (easiest):** opening the project triggers a Gradle sync that
  materializes the wrapper jar automatically. No action needed.
- **Command line:** if you have a system Gradle installed, generate the wrapper once:
  ```
  gradle wrapper --gradle-version 8.10.2
  ```
  This writes `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`,
  `gradlew`, and `gradlew.bat`. After that, always invoke the project through the
  wrapper:
  ```
  ./gradlew <task>      # Linux / macOS
  gradlew.bat <task>    # Windows (PowerShell / cmd)
  ```

---

## 3. Common tasks

```
./gradlew assembleDebug          # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # release APK (R8 + resource shrink)
./gradlew :app:bundleRelease     # optional signed AAB
./gradlew test                   # JUnit5 unit tests (useJUnitPlatform)
./gradlew clean                  # delete the root build dir
```

Install and become the default dialer:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then launch the app and accept the prompt to set Glyph Dialer as the default phone
app (it requests `RoleManager.ROLE_DIALER`), or set it under
*Settings → Apps → Default apps → Phone app*. Writing the call log and binding the
InCallService for live calls require this role.

---

## 4. Signing

Release signing is read from a **git-ignored** `keystore.properties` at the repo root.
When the file is absent, `assembleRelease` falls back to debug signing so a fresh
checkout still builds — but such an APK is **not** distributable.

1. Generate a keystore (once):
   ```
   keytool -genkeypair -v -keystore glyphdialer-release.jks \
     -alias glyphdialer -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy the template and fill it in:
   ```
   cp keystore.properties.template keystore.properties
   ```
   `keystore.properties`:
   ```properties
   storeFile=glyphdialer-release.jks
   storePassword=*****
   keyAlias=glyphdialer
   keyPassword=*****
   ```
3. Keep `keystore.properties` and the `.jks` out of version control. Never commit
   passwords or the real Glyph/Nothing API key.

> The release build also reads the production Glyph API key from a secured property —
> never commit it. Debug builds may keep the `"test"` key (see §6).

`keystore.properties.template` (copy this, do not edit in place):

```properties
# Copy to keystore.properties (git-ignored) and fill in real values.
storeFile=/absolute/or/relative/path/to/your-release.jks
storePassword=CHANGE_ME
keyAlias=CHANGE_ME
keyPassword=CHANGE_ME
```

---

## 5. Build types

- **debug** — `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`,
  no minification, verbose logging, Glyph `"test"` key usable with the debug toggle.
- **release** — R8 full-mode minification + resource shrinking. Keep rules preserve
  Hilt, Room, WebRTC, and the reflected Glyph classes (`com.nothing.ketchum.**`).

---

## 6. Glyph debug toggle (no Nothing API key needed for local testing)

On a supported Nothing device (Android 14+), enable the Glyph debug interface so the
`"test"` key works without a registered production key. The toggle **auto-expires
after 48 hours**:

```
adb shell settings put global nt_glyph_interface_debug_enable 1
```

On any non-Nothing device this is a no-op — `GlyphController.isAvailable` stays
`false` and the Glyph features are hidden. See [GLYPH_SETUP.md](GLYPH_SETUP.md).

---

## 7. Place the Glyph GDK AAR

Hardware Glyph requires Nothing's SDK, which is **not** on a public Maven repo and is
**not** checked in. The `:peripheral:glyph` controllers drive it via reflection, so the
app builds and runs as a no-op without it.

1. Register at the **Nothing Developer Programme** and obtain the **GDK AAR**
   (light-strip phones) and/or the **Glyph Matrix Developer Kit AAR** (Phone (3)).
2. Copy the AAR(s) into `peripheral/glyph/libs/` — `settings.gradle.kts` already
   registers a `flatDir` repository pointing there.
3. Add the dependency in `peripheral/glyph/build.gradle.kts`:
   ```kotlin
   implementation(files("libs/glyph-developer-kit.aar"))
   // implementation(files("libs/glyph-matrix-developer-kit.aar"))
   ```
4. Set a real API key by replacing `@string/nothing_api_key` (currently `"test"`) in
   `app/src/main/res/values/strings.xml` for release builds.
5. Uncomment the keep rules in `peripheral/glyph/consumer-rules.pro` so R8 preserves
   the reflected `com.nothing.ketchum.**` classes.

No source change is needed to *consume* the AAR — see
`peripheral/glyph/libs/README.md` and [GLYPH_SETUP.md](GLYPH_SETUP.md).

---

## 8. Fonts

Licensed `.ttf` binaries are not checked in; the app falls back to system
Monospace/SansSerif until you add them. See [FONTS.md](FONTS.md).

---

## 9. Troubleshooting

- **`gradlew: command not found` / missing wrapper jar** — run
  `gradle wrapper --gradle-version 8.10.2` (§2) or sync once in Android Studio.
- **`SDK location not found`** — create `local.properties` with `sdk.dir` (§1).
- **`assembleRelease` produced a debug-signed APK** — `keystore.properties` is
  missing; add it (§4) for a distributable build.
- **Glyph does nothing on a Nothing phone** — confirm Android 14+, run the debug
  toggle (§6) or set a real key, and that the GDK AAR is present (§7).
