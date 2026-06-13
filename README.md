# Glyph Dialer

A drop-in replacement for the default Android phone app, wrapped in a monochrome,
dot-matrix, "engineered-transparency" aesthetic *visually inspired by* Nothing OS.
It aims for Google-Dialer feature parity (dialpad + T9, call log, contacts,
favorites/speed dial, in-call controls, conference, blocking, voicemail) and adds a
signature differentiator: on Nothing phones the **Glyph Interface** on the back of the
device reacts to dialpad input with a unique light stroke per key and choreographs
incoming calls, recording, and transcription.

The look is deliberately restrained: true-black/paper surfaces, monospaced numerals,
hairline dotted dividers, spring-physics motion, a single red accent, and subtle
haptics. Light/Dark/System themes and a user-selectable typeface (a dot-matrix "OG"
face vs. a clean grotesque "New" face) switch live.

> **This is an independent product.** Glyph Dialer is *not* a Nothing product and is
> not affiliated with, endorsed by, or sponsored by Nothing Technology Limited. See
> [LEGAL.md](LEGAL.md) and the *Legal & trademarks* section below.

---

## Reality & limitations (READ THIS FIRST)

Android the platform forbids several things that a "call recorder / video dialer"
might naively promise. Glyph Dialer follows a strict **honesty principle**
(BUILD_SPEC §2, CONVENTIONS.md §9): it never fakes an unsupported capability, it
exposes a runtime availability flag for every such feature, and the UI surfaces that
state explicitly. Concretely:

### Call recording is tiered — and often partial

Since Android 10 the OS blocks third-party apps from capturing the *remote party's*
in-call audio, and Play policy (2022) bans using the Accessibility API to do it. A
plain sideloaded APK on stock Android generally **cannot** record the other person.
Glyph Dialer resolves the highest tier available at call start and **shows it in the
UI**:

| Tier | Name | What you get | When it applies |
|---|---|---|---|
| A | `SYSTEM_TWO_WAY` | Both sides, full quality | Default dialer on an OEM/system build, custom ROM, or rooted device that exposes call audio |
| B | `VOIP_TWO_WAY` | Both sides, full quality | In-app WebRTC VoIP calls (the app owns the media) |
| C | `LOCAL_ONE_SIDED` | **Your side only** — labelled "my side only", not full call recording | Local mic capture where permitted |
| — | `UNAVAILABLE` | Nothing | Recording not possible on this device/call |

"Record without announcement" is **jurisdiction-dependent** and may be illegal in
two-party-consent regions. The setting exists but ships with a disclaimer; the app
never bypasses an OS-mandated announcement. See [LEGAL.md](LEGAL.md).

### No carrier video

Carrier video (ViLTE / ViWiFi) is an IMS feature and is **not** exposed to
third-party dialer UIs. "Switch to video" is **in-app VoIP video over WebRTC only**,
available solely when both parties are on this app / a federated backend and the peer
supports video. A cellular voice call cannot be upgraded to carrier video.

### Transcription inherits the recording limits

Transcribing the remote party on a **cellular** call needs the same call-audio access
as recording (so it is full only where Tier A applies; otherwise limited to the local
mic). On **VoIP/WebRTC** calls the app owns the audio, so transcription works fully.
The UI states which case is active.

### Glyph only on Nothing hardware (Android 14+)

The Glyph Interface lights up only on supported Nothing phones running Android 14+
with a Nothing API key (or the Glyph Matrix kit on Phone (3)). On every other device
`GlyphController.isAvailable == false`, all Glyph methods are graceful no-ops, and
Glyph settings are hidden. The app **never crashes** on non-Nothing devices. See
[GLYPH_SETUP.md](GLYPH_SETUP.md).

---

## Architecture overview

Clean Architecture + MVVM with a unidirectional data flow:

- **Presentation** — Jetpack Compose screens + `@HiltViewModel` view models that
  expose an immutable `StateFlow<XxxUiState>` and receive intent via `onEvent(...)`;
  one-shot effects flow through a `Channel`/`SharedFlow`.
- **Domain** — pure, Android-light: entities, repository *interfaces*, and use cases
  (`operator fun invoke`). Defines the `GlyphController` abstraction so no other module
  ever references Nothing's GDK types.
- **Data** — repository implementations backed by Room, DataStore, and Android content
  providers (ContactsContract, CallLog, VoicemailContract, BlockedNumberContract).
- **Peripheral/system layer** — telecom services (InCallService, ConnectionService,
  CallScreeningService) and the recording / transcription / WebRTC / Glyph integrations
  behind interfaces declared in domain.

Dependencies flow inward only (Presentation → Domain → Data; peripherals → Domain).
Features never depend on each other; cross-feature navigation is assembled in `:app`.
Full detail is in [ARCHITECTURE.md](ARCHITECTURE.md).

### Module map

```
GlyphDialer
│
├── :app                         single Activity, NavHost, DI graph, WorkManager
│
├── core
│   ├── :core:common             AppResult, Dispatcher qualifiers, Constants, utils
│   ├── :core:domain             entities, repository interfaces, use cases,
│   │                            GlyphController interface
│   ├── :core:data               repo impls (Room, DataStore, content providers)
│   ├── :core:designsystem       GlyphTheme, palette, typography, motion tokens
│   └── :core:ui                 signature components (DotMatrixText, GlyphKey,
│                                GlyphWaveform, DottedDivider, EngineeredCard, ...)
│
├── feature                      (no feature depends on another)
│   ├── :feature:dialpad         T9 dialpad, per-digit Glyph choreography
│   ├── :feature:calllog         Recents, grouping, filters, search
│   ├── :feature:contacts        list, fast-scroll, detail, create/edit, favorites
│   ├── :feature:incall          AOD-minimal in-call screen, conference, controls
│   ├── :feature:voicemail       visual voicemail / carrier VM shortcut
│   └── :feature:settings        appearance, glyph, calls, recording, transcription
│
├── :telecom                     InCallService, ConnectionService, CallScreeningService,
│                                TelecomRepository impl
│
└── peripheral
    ├── :peripheral:glyph        GlyphController impls (GDK + Glyph Matrix via reflection)
    ├── :peripheral:recording    tiered CallRecorder
    ├── :peripheral:transcription pluggable TranscriptionEngine (Whisper / SpeechRecognizer)
    └── :peripheral:webrtc        in-app VoIP/video WebRtcClient + signaling
```

Dependency direction (who may depend on whom):

```
:app  ──▶ every other module

:feature:*  ──▶ :core:ui, :core:designsystem, :core:domain, :core:common
:telecom     ──▶ :core:domain, :core:common  (+ :peripheral:glyph)
:peripheral:recording      ──▶ :core:domain, :core:common
:peripheral:transcription  ──▶ :core:domain, :core:common
:peripheral:webrtc         ──▶ :core:domain, :core:common, :telecom
:peripheral:glyph          ──▶ :core:common  (+ GDK aar from libs/)

:core:data  ──▶ :core:domain, :core:common
:core:ui    ──▶ :core:designsystem, :core:common
:core:designsystem ──▶ (nothing internal)
:core:domain ──▶ :core:common
:core:common ──▶ (nothing internal)
```

---

## Building

Full instructions and signing are in [BUILD.md](BUILD.md); the short version:

### Prerequisites

- **JDK 17** (the project targets `JavaVersion.VERSION_17`, `jvmTarget = "17"`).
- **Android Studio** (latest stable) with the Android SDK; `compileSdk` / `targetSdk`
  are **35**, `minSdk` is **29**.
- Gradle **8.10.2** via the wrapper (see the wrapper note below).

### Steps

1. Open the project root in Android Studio (it imports the Gradle build automatically).
2. Create `local.properties` at the repo root and point it at your SDK:
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk    # Windows
   # sdk.dir=/Users/<you>/Library/Android/sdk                 # macOS
   ```
   Android Studio writes this for you on first sync.
3. **Gradle wrapper jar note:** `gradle/wrapper/gradle-wrapper.jar` is a binary and may
   not be checked in. Android Studio materializes it on first sync. From a terminal
   without it, run once with a system Gradle:
   ```
   gradle wrapper --gradle-version 8.10.2
   ```
   After that, use `./gradlew` (Linux/macOS) or `gradlew.bat` (Windows).
4. Build the debug APK:
   ```
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`.
5. **Become the default dialer.** Install the APK, launch the app, and accept the
   prompt to set Glyph Dialer as your default phone app (it requests
   `RoleManager.ROLE_DIALER`). Several capabilities — writing the call log, binding the
   InCallService for live calls — are gated on holding this role. You can also set it
   under *Settings → Apps → Default apps → Phone app*.

> The Glyph SDK AARs and licensed font binaries are **not** checked in. The app builds
> and runs without them (Glyph no-ops; fonts fall back to system families). See
> [GLYPH_SETUP.md](GLYPH_SETUP.md) and [FONTS.md](FONTS.md) to enable them.

### Code quality & static analysis

Formatting follows the **Kotlin official style** and is pinned in
[`.editorconfig`](.editorconfig) (ktlint-compatible; matches `kotlin.code.style=official`).

**detekt** runs from the standalone CLI against the shared config at
[`config/detekt/detekt.yml`](config/detekt/detekt.yml) — no module `build.gradle.kts`
needs editing. From the project root:

```sh
# one-off (downloads the CLI jar once)
DETEKT_VERSION=1.23.7
curl -sSLo detekt.jar \
  "https://github.com/detekt/detekt/releases/download/v${DETEKT_VERSION}/detekt-cli-${DETEKT_VERSION}-all.jar"

java -jar detekt.jar \
  --config config/detekt/detekt.yml \
  --input . \
  --excludes '**/build/**,**/resources/**,**/*.kts' \
  --build-upon-default-config
```

CI runs `assembleDebug`, the JVM unit tests, Android `lintDebug`, and detekt on every
push / PR via [`.github/workflows/build.yml`](.github/workflows/build.yml) (JDK 17 /
Temurin, Gradle cached). Some jobs are `continue-on-error` until the full multi-module
build is reliably green and any OEM AARs are dropped in — see the comments in that file.

---

## Feature checklist (mapped to the roadmap)

Tracks the phased roadmap in BUILD_SPEC §24 / [ROADMAP.md](ROADMAP.md).

- [ ] **1. Foundation** — modules, design system, theme + font switch, navigation shell
- [ ] **2. Become the dialer** — role request, InCallService, place/answer/end, dialpad + T9
- [ ] **3. Parity core** — call log, contacts, favorites/speed dial, search, formatting
- [ ] **4. In-call depth** — mute/speaker/route, hold, DTMF, add call, merge/conference, swap
- [ ] **5. Glyph** — GlyphController + GDK, per-digit strokes, incoming show, no-op fallback
- [ ] **6. Recording** — tiered recorder, storage, player, retention purge, settings + disclaimer
- [ ] **7. Transcription** — engine interface, on-device Whisper default, post-call + live captions
- [ ] **8. VoIP/video (optional)** — ConnectionService + WebRTC, audio↔video switch
- [ ] **9. Parity finish** — spam/screening, blocking, voicemail, widget + QS tile
- [ ] **10. Polish** — motion, haptics, delight, accessibility, perf, signing/output

(Checkboxes reflect product intent; this repository is a coordinated multi-agent
scaffold — tick items as the corresponding module reaches its definition of done.)

---

## Legal & trademarks

- **Independent product.** "Glyph Dialer" is an independent application visually
  inspired by Nothing OS. It is **not** affiliated with, endorsed by, or sponsored by
  Nothing Technology Limited. "Nothing", "Glyph", and related marks are the property of
  their respective owners and are referenced only descriptively.
- **No proprietary fonts.** The app does **not** bundle Nothing's `NDot` / `NType 82`
  typefaces. It uses SIL OFL / open-licensed substitutes (dot-matrix + grotesque +
  monospaced numerals) and falls back to system fonts until you drop licensed `.ttf`
  files in. See [FONTS.md](FONTS.md).
- **Glyph SDK.** Hardware Glyph features use Nothing's official **Glyph Developer Kit
  (GDK)** / **Glyph Matrix SDK**, which remain Nothing's property and are obtained
  separately via the Nothing Developer Programme. They are integrated only behind the
  `GlyphController` abstraction and via reflection, so non-Nothing builds are unaffected.
- **Call recording is your responsibility.** Recording laws vary by jurisdiction
  (many require all-party consent). You are solely responsible for complying with the
  law where you and the other party are located. See [LEGAL.md](LEGAL.md).
- Third-party library licenses are noted in [LEGAL.md](LEGAL.md) and surfaced on the
  in-app About/legal screen.

---

## Documentation index

| Doc | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, dependency graph, data flow, key abstractions |
| [BUILD.md](BUILD.md) | Prerequisites, wrapper jar, signing, Glyph debug toggle, GDK AAR |
| [LEGAL.md](LEGAL.md) | Recording-law disclaimer, attributions, library licenses |
| [FONTS.md](FONTS.md) | Which fonts to drop in and how fallback works |
| [GLYPH_SETUP.md](GLYPH_SETUP.md) | Nothing dev key, meta-data, debug toggle, supported devices |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [ROADMAP.md](ROADMAP.md) | Phased delivery plan |
| [CONVENTIONS.md](CONVENTIONS.md) | The binding engineering contract |
| [docs/BUILD_SPEC.md](docs/BUILD_SPEC.md) | Canonical product/engineering spec |

Licensed under Apache-2.0 (see SPDX headers on source files).
