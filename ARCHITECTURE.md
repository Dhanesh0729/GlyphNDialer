# Architecture

Glyph Dialer follows **Clean Architecture + MVVM** with a strict, inward-only
dependency rule and unidirectional data flow. This document explains the layers, the
module dependency graph, how data flows through a call, and the key abstractions that
keep platform-specific and honesty-sensitive concerns behind interfaces.

The binding rules live in [CONVENTIONS.md](CONVENTIONS.md); the product intent lives in
[docs/BUILD_SPEC.md](docs/BUILD_SPEC.md). This file is the navigable explanation.

---

## 1. Layers

```
┌─────────────────────────────────────────────────────────────┐
│ PRESENTATION                                                  │
│   Compose screens (stateless) + @HiltViewModel view models    │
│   StateFlow<XxxUiState>  ·  onEvent(XxxEvent)  ·  XxxEffect    │
│   :feature:*  ·  :core:ui  ·  :core:designsystem               │
└───────────────▲───────────────────────────────┬──────────────┘
                │ observes state / sends intent  │ calls
┌───────────────┴───────────────────────────────▼──────────────┐
│ DOMAIN  (pure Kotlin, Android-light)                          │
│   Entities  ·  Repository INTERFACES  ·  Use cases            │
│   GlyphController · CallRecorder · TranscriptionEngine ·       │
│   WebRtcClient (interfaces only)                              │
│   :core:domain                                               │
└───────────────▲───────────────────────────────┬──────────────┘
                │ implements interfaces           │ provides data
┌───────────────┴───────────────────────────────▼──────────────┐
│ DATA + PERIPHERAL                                             │
│   Repo impls: Room, DataStore, ContactsContract, CallLog,    │
│   VoicemailContract, BlockedNumberContract                   │
│   :core:data                                                 │
│   System services + integrations:                            │
│   :telecom  ·  :peripheral:recording / transcription /        │
│   webrtc / glyph                                             │
└──────────────────────────────────────────────────────────────┘
```

- **Presentation** never touches Android content providers, Room, or the GDK directly.
  It collects `StateFlow<UiState>` with `collectAsStateWithLifecycle()` and emits user
  intent up. Composables are state-hoisted; screen-level composables own the view model.
- **Domain** has no Compose and no Android framework imports beyond plain types. It owns
  the entities, the repository *interfaces*, the use cases, and the peripheral
  *contracts* (`GlyphController`, `CallRecorder`, `TranscriptionEngine`, `WebRtcClient`).
- **Data / Peripheral** provides the concrete implementations, bound into the graph by
  Hilt `@Binds`/`@Provides` modules.

### MVVM contract (CONVENTIONS.md §5)

- ViewModels expose a single immutable `data class XxxUiState` via
  `val uiState: StateFlow<XxxUiState>`.
- User intent arrives via `fun onEvent(event: XxxEvent)` or explicit functions.
- One-shot effects (navigation, toasts) flow through a `Channel`/`SharedFlow<XxxEffect>`.
- **Constructor injection** everywhere; `@HiltViewModel` for view models, `@Inject` for
  use cases. Repositories are `interface` in `:core:domain`, `*Impl` in `:core:data`
  (or the relevant peripheral module).
- **Dispatchers are injected**, never hard-coded, via the `@Dispatcher(...)` qualifiers
  from `:core:common`.
- Fallible operations return the shared `AppResult<T>` rather than throwing across layers.
- Logging is Timber only.

---

## 2. Module dependency graph

Dependencies flow inward only. Features are siblings that never depend on one another;
cross-feature navigation is assembled in `:app`.

```
:app  ──▶ every other module (wires DI graph + NavHost)

:feature:*  ──▶ :core:ui, :core:designsystem, :core:domain, :core:common
:telecom     ──▶ :core:domain, :core:common   (+ :peripheral:glyph for choreography)
:peripheral:recording      ──▶ :core:domain, :core:common
:peripheral:transcription  ──▶ :core:domain, :core:common
:peripheral:webrtc         ──▶ :core:domain, :core:common, :telecom
:peripheral:glyph          ──▶ :core:common   (+ GDK aar from libs/)

:core:data  ──▶ :core:domain, :core:common
:core:ui    ──▶ :core:designsystem, :core:common
:core:designsystem ──▶ (nothing internal)
:core:domain ──▶ :core:common
:core:common ──▶ (nothing internal)
```

Invariants the build relies on:

- **`:core:domain` is the only place repository/peripheral interfaces are declared.**
  Everyone depends on the interface, never on a concrete impl in another peripheral.
- **Only `:peripheral:glyph` may import `com.nothing.ketchum.*`** or any GDK / Glyph
  Matrix type. Everyone else talks to the `GlyphController` interface.
- **Features never import each other.** Routes are exposed as constants +
  `NavGraphBuilder` extensions and stitched together by the `:app` `NavHost`.

### Package roots (CONVENTIONS.md §2)

| Module | namespace / package root |
|---|---|
| `:app` | `com.glyphdialer` |
| `:core:common` | `com.glyphdialer.core.common` |
| `:core:domain` | `com.glyphdialer.core.domain` |
| `:core:data` | `com.glyphdialer.core.data` |
| `:core:designsystem` | `com.glyphdialer.core.designsystem` |
| `:core:ui` | `com.glyphdialer.core.ui` |
| `:feature:dialpad` | `com.glyphdialer.feature.dialpad` |
| `:feature:calllog` | `com.glyphdialer.feature.calllog` |
| `:feature:contacts` | `com.glyphdialer.feature.contacts` |
| `:feature:incall` | `com.glyphdialer.feature.incall` |
| `:feature:voicemail` | `com.glyphdialer.feature.voicemail` |
| `:feature:settings` | `com.glyphdialer.feature.settings` |
| `:telecom` | `com.glyphdialer.telecom` |
| `:peripheral:glyph` | `com.glyphdialer.peripheral.glyph` |
| `:peripheral:recording` | `com.glyphdialer.peripheral.recording` |
| `:peripheral:transcription` | `com.glyphdialer.peripheral.transcription` |
| `:peripheral:webrtc` | `com.glyphdialer.peripheral.webrtc` |

---

## 3. Data flow

### Single-Activity Compose host

`:app` owns the single `MainActivity` and a Compose `NavHost`. Each feature contributes
top-level route constants and a `NavGraphBuilder.<feature>Graph(...)` extension; `:app`
calls them. Top-level destinations are **Recents (call log)**, **Contacts**, and
**Dialpad** (primary/FAB), with Settings and Voicemail reachable from overflow. The
in-call screen is launched by `:telecom` via the InCallService and routed to a
full-screen in-call destination.

### A live call (read path)

```
System telephony
      │ Call API callbacks
      ▼
:telecom  GlyphInCallService  ──maps──▶  CallModel / CallState
      │ writes
      ▼
TelecomRepository (impl in :telecom)  StateFlow<List<CallModel>>
      │ observed via use cases
      ▼
:feature:incall  InCallViewModel  ──reduces──▶  InCallUiState (StateFlow)
      │ collectAsStateWithLifecycle()
      ▼
InCallScreen (Compose)   ── side-effect ──▶  GlyphController.showOnCall(...)
```

### In-call action (write path)

```
InCallScreen onEvent(ToggleMute)
      ▼
InCallViewModel → ToggleMuteUseCase()        (returns AppResult)
      ▼
TelecomRepository.setMuted(...)  →  Call API
```

The same shape applies to answer / end / hold / route / DTMF / merge / swap / split via
their dedicated use cases (`AnswerCallUseCase`, `EndCallUseCase`, `ToggleHoldUseCase`,
`SetAudioRouteUseCase`, `SendDtmfUseCase`, `MergeConferenceUseCase`, `SwapCallUseCase`,
`SplitFromConferenceUseCase`).

### Settings (DataStore)

`SettingsRepository` (DataStore-backed, in `:core:data`) exposes a `Flow<UserPreferences>`
consumed via `ObservePreferencesUseCase`; writes go through `UpdatePreferencesUseCase`.
`GlyphTheme` in `:core:designsystem` reads theme/font/accent and switches live.

### Background work (WorkManager)

`:app` provides the `HiltWorkerFactory` (it implements `Configuration.Provider` and the
default `WorkManagerInitializer` is removed in the manifest). Workers include
`PurgeOldDataWorker` (retention purge of recordings/transcripts), a contacts-cache
worker (rebuilds the T9 index on contact changes), and a transcription worker
(constrained to charging/idle for heavy on-device models).

---

## 4. Key abstractions

These interfaces are declared in `:core:domain`; their implementations live in the
peripheral/system modules and are bound by Hilt. Each carries the honesty principle
(CONVENTIONS.md §9 / BUILD_SPEC §2): expose availability, never fake capability.

### `GlyphController` — `:peripheral:glyph`

```kotlin
interface GlyphController {
    val isAvailable: Boolean
    fun playDigitStroke(digit: Char)
    fun playIncomingShow(contactSeed: Int)
    fun showRecording(active: Boolean)
    fun renderWaveform(amplitude: Float)   // 0f..1f
    fun showOnCall(state: CallVisual)
    fun release()
}
enum class CallVisual { RINGING, ACTIVE, HOLD, CONFERENCE, ENDED }
```

- The only module allowed to import `com.nothing.ketchum.*`. It ships two impls — a GDK
  controller (light-strip phones) and a Glyph Matrix controller (Phone (3)) — both
  driven **via reflection**, so the module compiles and runs without the AAR present.
- On non-Nothing hardware (or a missing key/permission) a no-op impl reports
  `isAvailable == false`; every method does nothing and the UI hides Glyph settings.
- Per-digit strokes (1–9, 0, *, #) follow the zone/shape/period table in BUILD_SPEC
  §17.4; strokes are non-blocking and debounced so fast typing overlaps gracefully.
- Other choreography: incoming per-contact light show seeded from a hash of the number,
  steady glow on active, breathing on hold, multi-zone pulse for conference, persistent
  recording indicator, and a waveform that mirrors `renderWaveform(amplitude)`.

See [GLYPH_SETUP.md](GLYPH_SETUP.md).

### `CallRecorder` (tiers) — `:peripheral:recording`

```kotlin
interface CallRecorder {
    fun supportedTier(): RecordingTier   // SYSTEM_TWO_WAY | VOIP_TWO_WAY | SPEAKER_TWO_WAY | LOCAL_ONE_SIDED | UNAVAILABLE
    // start / stop ...
}
```

- Resolves the **highest supported tier** at call start and the UI shows it
  ("Recording: two-way" / "two-way (speakerphone)" / "my side only" / "unavailable").
  See the README *Reality & limitations* table.
- Tier A (system/OEM call audio) is the only reliable clean two-sided cellular capture;
  Tier B (VoIP/WebRTC) records both tracks because the app owns the media; Tier C
  (`SPEAKER_TWO_WAY`) routes stock cellular calls to the loudspeaker so the mic captures
  both sides acoustically (`SpeakerphoneTwoWayRecorder`, lower fidelity — degrades to
  Tier D if it can't engage speaker); Tier D is local mic only, labelled "my side only"
  and never presented as full call recording.
- Recording starts with an audible announcement (`RecordingAnnouncer`: TTS, tone
  fallback) unless the user disabled it; `Recording.announced` records whether it fired.
- Storage: encrypted audio in app-private storage (MediaStore opt-in for export);
  metadata in Room (`RecordingEntity`); Media3 player with waveform scrubber; export
  behind an explicit consent dialog. Auto-purge via `PurgeOldDataWorker` honours the
  user's retention window (default 180 days; also 30/90/never).
- A "no-announcement" setting exists with a legal disclaimer and never bypasses an
  OS-mandated announcement.

### `TranscriptionEngine` — `:peripheral:transcription`

```kotlin
interface TranscriptionEngine {
    suspend fun transcribeFile(path: String): Transcript
    fun liveCaptions(): Flow<String>
}
```

- Pluggable; the user picks the engine in Settings. Intended impls: on-device Whisper
  (privacy default, via whisper.cpp/TFLite — a `// TODO` native drop-in), Android
  `SpeechRecognizer` (low-latency live captions), and ML Kit / cloud STT behind a flag.
- Live captions render as a Nothing-style ticker-tape / dot-matrix overlay (`TickerCaption`),
  **full for VoIP, limited for cellular** per the audio-source limits (BUILD_SPEC §2.4).
- Post-call transcripts are stored in Room (`TranscriptEntity` + `TranscriptSegmentEntity`
  with timestamps), full-text searchable, and tap-to-seek aligned. Two-track VoIP
  recordings get speaker labels.

### `WebRtcClient` — `:peripheral:webrtc`

- In-app VoIP/video only (no carrier video). WebSocket signaling (Ktor) exchanges
  SDP/ICE; a WebRTC peer connection carries the media; the call is routed through a
  self-managed `ConnectionService` so it joins the system call UX, audio focus, and
  Bluetooth. Audio↔video is a renegotiation that adds/removes the video track and
  prompts the remote party. STUN + TURN are required for NAT traversal (TURN server URL
  is an external `// TODO`). Video controls appear **only** for an in-app VoIP call whose
  peer supports video.

### Telecom services — `:telecom`

Implements the Android Telecom contract: `InCallService` (system binds it for every call
when Glyph Dialer is the default dialer), a self-managed `ConnectionService`
(`CAPABILITY_SELF_MANAGED`, so WebRTC calls integrate with system call UX), and a
`CallScreeningService` (caller-ID, spam labeling, silence/block decisions). It mirrors
Call API state into `TelecomRepository` and maps in-call actions onto the Call API.

---

## 5. Shared primitives (`:core:common`)

- `AppResult<out T>` — `Success(data)` / `Failure(throwable, message?)`, plus
  `appResultOf { }`, `map`, `getOrNull`. The boundary type for fallible operations.
- `@Dispatcher(GlyphDispatcher)` qualifier (`DEFAULT`, `IO`, `MAIN`) + a `DispatcherProvider`
  (`io`, `default`, `main`) so dispatchers are injected, not hard-coded.
- `Constants`, extension utilities, Intent/Flow helpers.

## 6. Design system (`:core:designsystem`) and components (`:core:ui`)

`GlyphTheme(themeMode, appFont, accent, content)` wraps `MaterialTheme` with a
monochrome palette (dynamic color **disabled**): dark `#000000` surface / `#EDEDED`
text, light `#F5F5F5` / `#0A0A0A`, a single red accent (default `#D7263D`), hairline
dotted dividers `#2A2A2A`. `LocalAppTypography` provides the chosen `FontFamily`;
numerals are always monospaced. Motion uses spring physics (`StiffnessMedium`,
`DampingRatioLowBouncy`). Signature stateless components live in `:core:ui`:
`DotMatrixText`, `GlyphKey`, `GlyphWaveform`, `DottedDivider`, `EngineeredCard`,
`TickerCaption`, `MonoTimer`, `DotMatrixAvatar`, `DotMatrixSpinner`, `RationaleSheet`,
`EmptyState`.

See [FONTS.md](FONTS.md) for typeface substitution and fallback.
