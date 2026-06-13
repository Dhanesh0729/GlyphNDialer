# Glyph setup

The Glyph Interface (the lights on the back of Nothing phones) is Glyph Dialer's
signature differentiator: each dialpad key fires a distinct light stroke, and incoming
calls, recording, and transcription are choreographed. This is **optional hardware
integration** — the app is fully functional without it.

Per the honesty principle (CONVENTIONS.md §9 / BUILD_SPEC §2 & §17.6): on any
non-Nothing device, on a device missing the API key/permission, or before the SDK AAR
is present, `GlyphController.isAvailable == false`, **every Glyph method is a no-op**,
and the Glyph settings group is **hidden**. The app **never crashes** because of Glyph.

---

## How the integration is wired (reflection — no AAR required to build)

`:peripheral:glyph` is the **only** module allowed to touch Nothing's SDK
(`com.nothing.ketchum.*`). It exposes the `GlyphController` interface (declared in
`:core:domain`) and ships:

- `GdkGlyphController` — light-strip phones, drives `GlyphManager` via reflection.
- `GlyphMatrixController` — Phone (3) pixel matrix, drives `GlyphMatrixManager` via
  reflection.
- A no-op controller selected when no hardware/SDK is available.

Because both controllers locate the SDK classes and builders **reflectively**, the
module compiles and runs even when the AAR is absent (graceful no-op), and lights up at
runtime once the AAR is present — **no source change is needed to consume it**. See
`peripheral/glyph/libs/README.md`.

---

## Supported devices

| Glyph type | Devices | SDK | API key |
|---|---|---|---|
| Light strip (GDK) | Phone (1) `20111`, (2) `22111`, (2a) `23111`, (2a Plus) `23113`, (3a)/(3a Pro) `24111`, (4a) `25111` | Nothing GDK AAR | **Required** |
| Pixel matrix | Phone (3) | Glyph Matrix Developer Kit AAR | **Not** required |
| Anything else | All non-Nothing devices | — | n/a — no-op |

All Glyph hardware requires **Android 14+**. The controller detects the model via the
SDK's `isXXXXX()` helpers before opening a session.

---

## 1. Get a Nothing Developer Programme key (light-strip phones)

Register your app at the **Nothing Developer Programme** to obtain a Glyph (GDK) API
key. Phone (3)'s Glyph Matrix kit does **not** require a key.

## 2. Drop in the SDK AAR

1. Obtain the **GDK AAR** and/or **Glyph Matrix Developer Kit AAR** from the Nothing
   Developer Programme.
2. Copy the AAR(s) into `peripheral/glyph/libs/` (the `flatDir` repo in
   `settings.gradle.kts` already points there).
3. Add the dependency in `peripheral/glyph/build.gradle.kts`:
   ```kotlin
   implementation(files("libs/glyph-developer-kit.aar"))
   // implementation(files("libs/glyph-matrix-developer-kit.aar"))
   ```
4. Uncomment the keep rules in `peripheral/glyph/consumer-rules.pro` so R8 preserves the
   reflected `com.nothing.ketchum.**` classes in release builds.

## 3. API key meta-data & permission

These are already declared by `:peripheral:glyph` and merged into the app manifest — you
do not re-declare them:

- Permission: `com.nothing.ketchum.permission.ENABLE` (inert on non-Nothing devices).
- Meta-data: `NothingKey = @string/nothing_api_key`.

Set your key by editing `app/src/main/res/values/strings.xml`:

```xml
<string name="nothing_api_key">YOUR_REAL_KEY_HERE</string>
```

The default is `"test"`, which works with the debug toggle below for local testing on a
Nothing device. **Use a real key for release builds and never commit it.**

## 4. Debug toggle (test without a production key)

On a supported Nothing phone (Android 14+), enable the Glyph debug interface so the
`"test"` key works. **The toggle auto-expires after 48 hours.**

```
adb shell settings put global nt_glyph_interface_debug_enable 1
```

On non-Nothing devices this command is harmless and has no effect.

---

## Per-key stroke choreography (BUILD_SPEC §17.4)

Each dialpad key fires a distinct, recognizable stroke. Strokes are **non-blocking and
debounced** so fast typing queues/overlaps gracefully (cancel-and-restart or short
queue). A master "Glyph on dialpad" toggle and an intensity slider live in Settings.

| Key | Concept | Zones / shape | Period |
|---|---|---|---|
| 1 | single short tick | top-right zone, sharp on/off | 120 ms |
| 2 | rising sweep | bottom strip L→R ramp up | 200 ms |
| 3 | double pulse | camera ring, two beats | 2×90 ms |
| 4 | descending sweep | bottom strip R→L | 200 ms |
| 5 | center bloom | center out to edges | 220 ms |
| 6 | corner arc | top-right 16-zone arc | 240 ms |
| 7 | long fade | full breathe up then down | 320 ms |
| 8 | infinity flicker | alternate two zones | 2×80 ms |
| 9 | spiral | sequential zone chase | 280 ms |
| 0 | full flash | all zones, brief | 100 ms |
| * | sparkle | random zones twinkle | 180 ms |
| # | hash blink | grid-like alternation | 150 ms |

Other choreography: incoming-call per-contact light show (seeded from a hash of the
number) + torch flash on connect; steady glow while active; breathing on hold;
multi-zone pulse for conference by party count; persistent recording indicator; and a
waveform that mirrors `GlyphController.renderWaveform(amplitude)` for voice level /
transcription.

> The reflective channel/pixel mappings in `GdkGlyphController.buildZoneChannelMap()` and
> `GlyphMatrixController.zonePixels()` are best-effort representative mappings. Refine
> them per model against the shipped AAR if pixel-perfect zone placement matters.

---

## Legal / attribution

The Nothing GDK and Glyph Matrix SDK are **Nothing's property**, obtained separately and
governed by Nothing's own terms — they are not redistributed with this project and are
not covered by Glyph Dialer's Apache-2.0 license. See [LEGAL.md](LEGAL.md).
