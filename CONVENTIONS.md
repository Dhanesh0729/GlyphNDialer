# Glyph Dialer — Engineering Conventions (the binding contract)

This file is the contract every module must follow. It exists so independently
generated modules fit together. **If you are generating a module, read this in
full before writing any file.** Honor the package roots, the dependency graph,
the shared APIs, and the version-catalog rule exactly.

> Product note: this is an independent product (**Glyph Dialer**), *visually
> inspired by* Nothing OS. It is NOT a Nothing product. Do not bundle Nothing's
> proprietary fonts (NDot / NType 82) or brand it as official. Use OFL/open-font
> substitutes (see §Fonts). Glyph hardware features integrate the official
> Nothing GDK only behind the `GlyphController` abstraction.

---

## 1. Platform & toolchain

| Item | Value |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 (dynamic color DISABLED — brand is monochrome) |
| `minSdk` | 29 |
| `targetSdk` / `compileSdk` | 35 |
| JVM target | 17 (`JavaVersion.VERSION_17`, `jvmTarget = "17"`) |
| Kotlin | 2.0.21 (Compose compiler via `org.jetbrains.kotlin.plugin.compose`) |
| Annotation processing | **KSP** (not kapt) for Room + Hilt |
| DI | Hilt |
| Async | Coroutines + Flow; UI state via `StateFlow` |

## 2. Application id & package roots

- `applicationId` / `namespace` root: **`com.glyphdialer`**
- Each module's `namespace` and Kotlin package root:

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

Kotlin sources live under `src/main/kotlin/<package path>/`. (The build files set
`kotlin.srcDirs` to include `src/main/kotlin`.)

## 3. Module dependency graph (who may depend on whom)

```
:app  ──▶ every other module (it wires the DI graph + navigation host)

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

Rules:
- **Domain is pure Kotlin/Android-light**: entities, repository *interfaces*, use
  cases. No Compose, no Android framework imports beyond plain types. Prefer a
  `com.android.library` module with no Compose.
- **Features never depend on each other.** Cross-feature navigation goes through
  route constants exposed by each feature and assembled in `:app`.
- **No module except `:peripheral:glyph` may import `com.nothing.ketchum.*`** or
  any GDK/Matrix type. Everyone else talks to the `GlyphController` interface
  (declared in `:core:domain`, see §6).

## 4. Build-file conventions

- Reference all deps/plugins via the **version catalog** (`libs.*`). Do NOT write
  raw coordinates and do NOT edit `gradle/libs.versions.toml` (parallel edits
  conflict — everything you need is already there; if truly missing, leave a
  `// TODO(deps)` and use the closest alias).
- Android library module skeleton:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // add only if the module uses them:
    // alias(libs.plugins.kotlin.compose)
    // alias(libs.plugins.ksp)
    // alias(libs.plugins.hilt)
    // alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.glyphdialer.<module.path>"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }   // only for UI-bearing modules
}

dependencies {
    implementation(project(":core:common"))
    // ...
}
```

- A UI-bearing module also adds `implementation(platform(libs.androidx.compose.bom))`
  and `implementation(libs.bundles.compose)` + `androidTestImplementation(platform(...))`.
- Pure-Kotlin/JVM-testable logic may live in a `com.android.library` module and be
  unit-tested with JUnit5 (`useJUnitPlatform()` in `tasks.withType<Test>`).
- Every module ships a `consumer-rules.pro` (may be empty with a header comment).

## 5. Architecture & code style

- **Clean Architecture + MVVM.** Presentation (Compose + ViewModel) → Domain
  (use cases, repo interfaces) → Data (repo impls, Room, DataStore, providers).
- ViewModels expose a single immutable `data class XxxUiState` via
  `StateFlow<XxxUiState>` (`val uiState: StateFlow<...>`), and receive user intent
  through `fun onEvent(event: XxxEvent)` or explicit functions. One-shot effects
  (navigation, toasts) via a `Channel`/`SharedFlow<XxxEffect>`.
- Use **constructor injection** everywhere; `@HiltViewModel` for view models;
  `@Inject` use cases. Repositories are `interface` in `:core:domain`, `*Impl` in
  `:core:data` (or the relevant peripheral module), bound via Hilt `@Binds` modules.
- Dispatchers are injected, never hard-coded. Use the qualifiers from
  `:core:common` (see §6): `@Dispatcher(IO)` etc.
- Wrap fallible operations in the shared `Result`/`AppResult` type from
  `:core:common` (see §6) rather than throwing across layers.
- Compose: `@Composable` functions are PascalCase; stateless composables take
  state + lambdas (state hoisting); screen-level composables collect state with
  `collectAsStateWithLifecycle()`. Add `@Preview` for visual components.
- Logging via Timber. No `println`/`Log.d`.
- Naming: `XxxScreen`, `XxxViewModel`, `XxxUiState`, `XxxRepository`(+`Impl`),
  `XxxUseCase`, `XxxDao`, `XxxEntity`.

## 6. Shared APIs every module can rely on (declared in core)

These are the contracts core modules MUST declare and others MUST consume.
(Generators of consumer modules: read the actual files off disk under
`core/common/...`, `core/domain/...`, `core/designsystem/...`, `core/ui/...`
before referencing — match the real signatures.)

**`:core:common`**
- `AppResult<out T>` sealed type: `Success(data)`, `Failure(throwable, message?)`,
  plus `inline fun <T> appResultOf(block): AppResult<T>` and `map`/`getOrNull`.
- `@Qualifier annotation class Dispatcher(val type: GlyphDispatcher)` with enum
  `GlyphDispatcher { DEFAULT, IO, MAIN }`, and a Hilt module providing them.
- `DispatcherProvider` interface (`io`, `default`, `main`) + default impl.
- Misc: `Constants`, extension utils, `Intent`/flow helpers.

**`:core:domain`** — entities + repo interfaces + use cases:
- Entities: `CallModel`, `CallState` (enum: NEW, RINGING, DIALING, ACTIVE,
  HOLDING, DISCONNECTED, CONNECTING, CONFERENCE...), `CallDirection`, `Contact`,
  `PhoneNumber`, `CallLogEntry`, `CallType` (INCOMING/OUTGOING/MISSED/REJECTED/
  BLOCKED/VOICEMAIL), `Favorite`, `SpeedDialSlot`, `BlockedNumber`, `Voicemail`,
  `Recording`, `RecordingTier` (SYSTEM_TWO_WAY, VOIP_TWO_WAY, LOCAL_ONE_SIDED,
  UNAVAILABLE), `Transcript`, `TranscriptSegment`, `CallNote`, `AudioRoute`
  (EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET).
- Settings models: `ThemeMode` (LIGHT, DARK, SYSTEM), `AppFont` (OG_DOT_MATRIX,
  NEW_GROTESQUE), `AccentColor`, `TranscriptionEngineType`, `RetentionWindow`
  (DAYS_30, DAYS_90, DAYS_180, NEVER), `UserPreferences` data class aggregating
  all settings.
- **`GlyphController`** interface (the abstraction; impls in `:peripheral:glyph`):
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
- Repository interfaces (impls live in `:core:data` unless noted):
  `TelecomRepository` (active calls `StateFlow`, place/answer/end/hold/mute/route/
   dtmf/merge/swap/split — impl in `:telecom`), `ContactsRepository`,
  `CallLogRepository`, `FavoritesRepository`, `SpeedDialRepository`,
  `BlockedNumberRepository`, `VoicemailRepository`, `RecordingRepository`
   (impl coordinates `:peripheral:recording`), `TranscriptRepository`
   (impl coordinates `:peripheral:transcription`), `SettingsRepository`
   (DataStore-backed), `CallNoteRepository`.
- Peripheral contracts (interfaces in domain; impls in peripheral modules):
  `CallRecorder` (with `fun supportedTier(): RecordingTier`, start/stop),
  `TranscriptionEngine` (`suspend fun transcribeFile(path): Transcript`,
   `fun liveCaptions(): Flow<String>`), `WebRtcClient`.
- Use cases (suspend `operator fun invoke`): `PlaceCallUseCase`,
  `AnswerCallUseCase`, `EndCallUseCase`, `ToggleHoldUseCase`, `ToggleMuteUseCase`,
  `SetAudioRouteUseCase`, `SendDtmfUseCase`, `MergeConferenceUseCase`,
  `SwapCallUseCase`, `SplitFromConferenceUseCase`, `SearchContactsUseCase`,
  `T9SearchUseCase`, `GetCallLogUseCase`, `BlockNumberUseCase`,
  `RecordCallUseCase`, `TranscribeCallUseCase`, `PurgeOldDataUseCase`,
  `ObservePreferencesUseCase`, `UpdatePreferencesUseCase`, ...

**`:core:designsystem`** — theme + tokens:
- `GlyphTheme(themeMode, appFont, accent, content)` Composable wrapping
  `MaterialTheme` with the palette below; dynamic color OFF.
- Colors (see §Palette): dark default #000000 surface / #EDEDED text; light
  #F5F5F5 / #0A0A0A; one accent (default red `#D7263D`). Hairline dotted divider
  `#2A2A2A` (dark) / lighter (light).
- `LocalAppTypography` CompositionLocal providing the chosen `FontFamily`;
  numerals always monospaced. `LocalGlyphIntensity`, `LocalAccentColor`.
- Motion tokens: `GlyphSprings` (`spring(stiffness = Spring.StiffnessMedium,
  dampingRatio = Spring.DampingRatioLowBouncy)`), durations.
- Shapes, spacing grid, `Dimens`.

**`:core:ui`** — signature components (stateless, themed):
`DotMatrixText`, `GlyphKey`, `GlyphWaveform`, `DottedDivider`, `EngineeredCard`,
`TickerCaption`, `MonoTimer`, `DotMatrixAvatar`, `DotMatrixSpinner`,
`RationaleSheet` (permission rationale), `EmptyState`.

## 7. Navigation

- Single-Activity (`MainActivity`) + Compose `NavHost` assembled in `:app`.
- Each feature exposes top-level route constants + an extension
  `fun NavGraphBuilder.<feature>Graph(navController, ...)` or a
  `fun NavController.navigateToXxx()`. The `:app` host calls them.
- Top-level destinations (bottom of dialpad / nav): **Recents (call log)**,
  **Contacts**, **Dialpad** (primary/FAB), with Settings + Voicemail reachable
  from overflow. In-call is launched by `:telecom` via the InCallService and
  routed to a full-screen `incall` destination/Activity.

## 8. Permissions (declared per-module manifest, requested in :app/feature)

`CALL_PHONE, READ_PHONE_STATE, READ_PHONE_NUMBERS, MANAGE_OWN_CALLS,
READ_CONTACTS, WRITE_CONTACTS, READ_CALL_LOG, WRITE_CALL_LOG, RECORD_AUDIO,
CAMERA, READ_VOICEMAIL, WRITE_VOICEMAIL, POST_NOTIFICATIONS, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_PHONE_CALL, FOREGROUND_SERVICE_MICROPHONE,
FOREGROUND_SERVICE_CAMERA, INTERNET, ACCESS_NETWORK_STATE,
com.nothing.ketchum.permission.ENABLE`. The default-dialer role gates call-log
write etc. Manifest merging combines per-module declarations into `:app`.

## 9. The §2 honesty principle (NON-NEGOTIABLE — product requirement)

The real Android platform forbids several things. Code and UI must reflect this
truthfully — never fake an unsupported capability:
- **Call recording**: third-party apps cannot reliably capture the remote party
  on stock Android 10+. Implement tiers (SYSTEM_TWO_WAY / VOIP_TWO_WAY /
  LOCAL_ONE_SIDED / UNAVAILABLE) and surface the *active tier* in the UI.
- **"No announcement" recording**: jurisdiction-dependent and OS-mandated
  announcements must not be bypassed; expose a setting + legal disclaimer.
- **Carrier video (ViLTE)**: not available to third-party dialers. "Switch to
  video" is in-app WebRTC VoIP only, gated to in-app calls.
- **Transcription of remote party on cellular**: inherits the recording limits;
  full only for VoIP. State this in the UI.
- **Glyph**: only on Nothing hardware (Android 14+) with API key; otherwise
  `GlyphController.isAvailable == false` and every method no-ops. UI hides Glyph
  settings when unavailable. Never crash on non-Nothing devices.

Each capability has a runtime flag; the UI shows availability state explicitly.

## 10. Fonts (§16)

Two user-selectable families via `AppFont`:
- `OG_DOT_MATRIX` — an OFL dot-matrix face (placeholder filename:
  `res/font/og_dotmatrix.ttf`). Ship a `FONTS.md` note: replace with a real
  OFL-licensed dot-matrix font (do NOT ship NDot).
- `NEW_GROTESQUE` — grotesque (e.g. Space Grotesk / Inter) + monospaced numerals
  (Space Mono / JetBrains Mono). Placeholders `res/font/new_grotesque.ttf`,
  `res/font/mono_numerals.ttf`.
Because real font binaries can't be generated here, ship a tiny placeholder/empty
`.ttf` is NOT valid — instead reference the font resources in code and document in
FONTS.md that the user must drop licensed `.ttf` files in. Provide a
`fontFamilyOrDefault()` fallback to the system font so the app never crashes when
a font file is absent.

## 11. Tests

- Pure logic (T9 matching, libphonenumber formatting, retention math, Glyph stroke
  timing, transcript alignment, reducer/state) → JUnit5 + Turbine + Truth in
  `src/test`. Where Android types intrude, Robolectric is acceptable.
- Don't over-invest in instrumented tests in this pass; a couple of representative
  Compose UI tests + a telecom state-transition test are enough to show shape.

## 12. Headers

Top of each Kotlin file: a one-line `// SPDX-License-Identifier: Apache-2.0`
comment is fine. No Nothing copyright. Keep comments purposeful.
