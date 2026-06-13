# Glyph SDK drop-in (`peripheral/glyph/libs/`)

This directory is the home for the **Nothing GDK** and **Glyph Matrix Developer Kit**
AARs. They are **NOT** checked in and are **NOT present at build time** — the
`:peripheral:glyph` controllers drive them **entirely via reflection** so the module
compiles and runs (as a graceful no-op) without them, and lights up at runtime once
they are present.

## TODO: drop in the real SDK to enable hardware Glyph

1. Register an app at the **Nothing Developer Programme** and obtain:
   - the **GDK AAR** (light-strip phones: Phone (1)/(2)/(2a)/(2a+)/(3a)/(4a)), and/or
   - the **Glyph Matrix Developer Kit AAR** (Phone (3); no API key required).
2. Copy the AAR file(s) into **this directory** (`peripheral/glyph/libs/`).
   `settings.gradle.kts` already registers a `flatDir` repository pointing here.
3. Add the dependency in `peripheral/glyph/build.gradle.kts`, e.g.:
   ```kotlin
   implementation(files("libs/glyph-developer-kit.aar"))
   // implementation(files("libs/glyph-matrix-developer-kit.aar"))
   ```
4. Set your real API key: replace `@string/nothing_api_key` (currently `"test"`) in
   `src/main/res/values/strings.xml` for release builds. Debug builds may keep `"test"`
   with the on-device debug toggle:
   `adb shell settings put global nt_glyph_interface_debug_enable 1` (auto-expires 48h).
5. Uncomment the keep rules in `consumer-rules.pro` so R8 preserves the reflected
   `com.nothing.ketchum.**` classes.

No code change is required to *consume* the AAR — `GdkGlyphController` /
`GlyphMatrixController` already locate `GlyphManager` / `GlyphMatrixManager` and their
builders reflectively. The reflective channel/pixel mappings (in
`GdkGlyphController.buildZoneChannelMap()` and `GlyphMatrixController.zonePixels()`) are
best-effort representative mappings; refine them per model against the shipped AAR if
pixel-perfect zone placement matters.
