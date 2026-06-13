# Build Spec — "Glyph Dialer": A Nothing-OS-Styled Android Phone App

> This is the canonical product/engineering specification. CONVENTIONS.md is the
> binding implementation contract derived from it. Where the two overlap,
> CONVENTIONS.md wins on concrete names/versions/structure; this file is the
> source of intent and feature detail.

---

## 1. Product Vision
A drop-in replacement for the default Android phone app, with full Google-Dialer
feature parity, wrapped in a monochrome, dot-matrix, "engineered-transparency"
Nothing-OS aesthetic. Signature differentiator: the Glyph Interface on the back of
Nothing phones reacts to dialpad input with a unique light stroke per key, and
choreographs incoming calls, recording, and transcription. Supports light/dark
themes and a user-selectable typeface (a dot-matrix "OG" face vs. a clean
grotesque "new" face). Must feel fast, tactile, and minimal: monospaced numerals,
dotted dividers, spring-physics motion, restrained use of a single red accent,
subtle haptics.

## 2. Critical Feasibility & Legal Reality (HONESTY PRINCIPLE — non-negotiable)
- **2.1 Call recording.** Since Android 10 the OS blocks third-party apps from
  capturing in-call audio; Play policy (2022) bans the Accessibility API for it.
  Reliable two-sided silent recording only when the app is the system/default
  dialer on an OEM build exposing call audio, or rooted/custom-ROM, or via OEM
  call-audio APIs. A plain sideloaded APK on stock Android generally CANNOT
  capture the remote party. Implement tiered approach (§12); show the active tier.
- **2.2 "Without announcement."** Recording without notifying the other party is
  restricted/illegal in two-party-consent regions. Expose a setting; ship a clear
  disclaimer that the user is responsible for legal compliance. Never hard-code
  deceptive behavior; honor the user's choice and the law. Never bypass an
  OS-mandated announcement.
- **2.3 Video calling.** Carrier video (ViLTE/ViWiFi) is IMS, not exposed to
  third-party dialer UIs. "Switch to video" = in-app VoIP video over WebRTC (§11),
  only when both parties use this app / a federated backend. Cellular voice calls
  cannot be upgraded to carrier video from a third-party app.
- **2.4 Transcription audio source.** Transcribing the remote party on a cellular
  call needs the same call-audio access as recording (inherits 2.1 limits). For
  VoIP/WebRTC the app owns the audio and transcription works fully; for cellular,
  available only where call-audio capture is, else limited to the local mic.

## 3. Target Platform & Device Matrix
- Kotlin + Jetpack Compose (Material 3, heavily themed).
- minSdk 29; target/compile 35; default-dialer via RoleManager.ROLE_DIALER.
- Glyph (light strip phones): Nothing GDK — Phone (1)/(2)/(2a)/(2a Plus)/(3a)/(4a).
  Requires Nothing device on Android 14+ and a Nothing API key.
- Glyph (Phone (3)): Glyph Matrix Developer Kit (pixel LED matrix, no API key).
- Non-Nothing devices: app runs fully; Glyph features are graceful no-ops behind a
  capability flag.
- Output: signed APK (primary) + optional AAB.

## 4. Technology Stack
- UI: Compose, Material 3, Compose Navigation, Accompanist (permissions), Coil,
  Lottie/Compose animation.
- Architecture/DI: Clean Architecture + MVVM, Hilt, Coroutines + Flow.
- Telephony: Telecom framework — InCallService, ConnectionService (self-managed
  VoIP), CallScreeningService, TelecomManager, Call, CallAudioState, RoleManager.
  Providers: ContactsContract, CallLog, VoicemailContract, BlockedNumberContract.
- Persistence: Room (recordings/transcripts metadata, blocklist, favorites, notes),
  DataStore (settings/theme/font/glyph toggles). EncryptedFile/MediaStore for audio.
- Media: Media3 (ExoPlayer) playback; CameraX for video.
- VoIP/Video (optional module): WebRTC + signaling backend; STUN/TURN.
- Transcription: pluggable engine — Android SpeechRecognizer, ML Kit, on-device
  Whisper (whisper.cpp/TFLite), optional cloud STT behind a flag.
- Background: WorkManager (contacts sync, 30/180-day auto-purge, transcription).
- Glyph: Nothing GDK AAR (com.nothing.ketchum) + Glyph Matrix SDK for Phone (3).
- Build: Gradle Kotlin DSL, version catalogs, R8/ProGuard.
- Quality: ktlint/detekt, JUnit5, Turbine, Compose UI tests, Espresso.

## 5. Architecture
Clean Architecture, three layers + peripheral integration layer. Unidirectional
data flow; ViewModels expose immutable UiState via StateFlow; UI emits events up.
Presentation → Domain (use cases, entities, repo interfaces) → Data (repo impls,
Room, DataStore, content providers). Peripheral/system services: InCallService,
ConnectionService, CallScreeningService, GlyphController, AudioRecorder,
TranscriptionEngine, WebRtcClient, WorkManager workers.

## 6. Module Structure
`:app`, `:core:designsystem`, `:core:ui`, `:core:common`, `:core:data`,
`:core:domain`, `:feature:dialpad|calllog|contacts|incall|voicemail|settings`,
`:telecom`, `:peripheral:glyph|recording|transcription|webrtc`. The glyph module
exposes a `GlyphController` interface so the rest of the app never references GDK
types directly (no-op fallback on non-Nothing devices).

## 7. Telecom Integration (core)
- 7.1 Become default dialer: request RoleManager.ROLE_DIALER on first run.
- 7.2 In-call UI provider: implement InCallService; system binds it for every call
  when default dialer. Track calls via the Call API; mirror state into a
  CallRepository (StateFlow<List<CallModel>>) the in-call screen observes.
  Manifest: BIND_INCALL_SERVICE, meta IN_CALL_SERVICE_UI=true,
  IN_CALL_SERVICE_RINGING=true, intent-filter android.telecom.InCallService.
- 7.3 Placing calls: TelecomManager.placeCall(uri, extras); dialpad → tel: URI.
- 7.4 Self-managed VoIP: ConnectionService + PhoneAccount with
  CAPABILITY_SELF_MANAGED so WebRTC calls join system call UX/audio focus/BT.
- 7.5 Call screening/spam: CallScreeningService for caller-ID, spam labeling,
  silence/block decisions on incoming calls.
- 7.6 In-call actions map to the Call API: answer(), disconnect(), hold()/unhold(),
  playDtmfTone()/stopDtmfTone(), setMuted(), setAudioRoute()/requestBluetoothAudio(),
  conference()/splitFromConference()/mergeConference()/swapConference().

## 8. Google Dialer Parity
- Dialpad (T9): numeric keypad with letters, long-press shortcuts (0→+, 1→voicemail,
  speed-dial 2–9), live T9 smart search filtering contacts + call log, paste,
  backspace long-press clear, add-to-contacts, prominent call button. Monospaced
  numerals.
- Call log / Recents: grouped by contact + relative time, type icons
  (incoming/outgoing/missed/rejected/blocked/voicemail), tap to call back, swipe for
  message/details, missed-only filter, search, bulk delete. CallLog.Calls.
- Contacts: list with fast-scroll alphabet index, search, detail, create/edit
  (ContactsContract), favorites, set default number.
- Favorites / speed dial: grid with photos; speed-dial assignment from dialpad
  long-press.
- Caller ID & spam: lookup vs contacts; CallScreeningService labeling; verified
  caller where available; libphonenumber formatting.
- Call blocking: BlockedNumberContract-backed list; add/remove; block & report.
- Voicemail: visual voicemail via VoicemailContract where supported; play/pause/
  scrub, transcription (reuse §13), call back, delete; else carrier VM dial shortcut.
- Search: unified across contacts, call log, places.
- Number formatting / dialing aids: libphonenumber for formatting, region inference,
  validation.

## 9. In-Call Feature Spec
Full-screen AOD-minimal in-call screen wired to the Call API: Mute, Speaker/route
picker (earpiece/speaker/BT/wired), Hold/resume (+ Glyph breathing), in-call DTMF
keypad, Add call → dialpad → place second → merge, Merge/conference (§10), Swap,
Switch to video/back (§11, VoIP only), Record (§12, per tier), Live transcription
toggle (captions overlay, §13), End call. Metadata: name/number, photo, monospaced
duration timer, SIM/line indicator on dual-SIM, signal/HD-voice badge.

## 10. Conference Call
Telecom conference model. With two independent calls expose Merge →
Call.conference(otherCall); the system/carrier creates a conference Call with child
participants. Provide participant list with per-participant hold/disconnect where
CAPABILITY_MANAGE_CONFERENCE; Add more; Split (splitFromConference()); conference
duration + count; Glyph multi-party pattern. Cellular conferences are
carrier-mediated (~5 parties cap); VoIP conferences limited by your SFU.

## 11. Video ↔ Audio Switching (in-app VoIP, WebRTC)
Carrier video out of scope. In-app calling layer: WebSocket signaling (Ktor or
managed provider) exchanging SDP/ICE; WebRTC peer connection; CameraX/WebRTC
capturer for local video; route through self-managed ConnectionService (§7.4);
upgrade/downgrade renegotiates to add/remove the video track and prompts the remote
party; STUN + TURN. Video controls appear only when the active call is an in-app
VoIP call AND the peer supports video.

## 12. Call Recording Module (tiered, honest)
`CallRecorder` interface with availability detection; resolve highest supported tier
at call start and show it ("Recording: two-way" / "my side only" / "unavailable").
- Tier A — System/OEM call-audio (best, two-sided): default dialer + device exposes
  call audio (OEM/system/custom-ROM/root). Only reliable remote-party capture on
  cellular.
- Tier B — VoIP/WebRTC: own the media; record both tracks; full quality.
- Tier C — Local-side fallback: local mic + speaker where permitted; label "my side
  only"; do NOT present as full call recording.
Storage & lifecycle: encrypted audio in app-private storage (or MediaStore opt-in
for export); metadata in Room (recordingId, callId, number, contactName, startedAt,
durationMs, tier, filePath, transcriptId?); Media3 player with waveform scrubber;
share/export with explicit consent dialog. Auto-purge: WorkManager deletes
recordings/transcripts older than a user-set window (default 180 days; also
30/90/never). No-announcement setting with §2.2 disclaimer; never bypass mandated
announcement.

## 13. Audio Transcription Module
Pluggable `TranscriptionEngine`; ship multiple impls; user chooses in Settings.
- On-device Whisper (recommended default for privacy): bundled small/quantized model
  (whisper.cpp via JNI or TFLite). Transcribe recorded files and, where audio access
  permits, stream live.
- Android SpeechRecognizer (on-device where supported): low-latency live captions.
- ML Kit / Cloud STT: optional behind a flag.
- Live captions: Nothing-style ticker-tape/dot-matrix overlay (full for VoIP,
  limited for cellular per §2.4).
- Post-call transcripts: for any recording; stored in Room (transcriptId,
  recordingId, language, segments[] with timestamps, text); full-text searchable;
  tap-to-seek alignment. Language auto-detect + manual override; speaker labels for
  two-track VoIP recordings.

## 14. Google Contacts Sync
Primary: read/write via ContactsContract (aggregates Google + other accounts kept in
sync by Android's SyncAdapter). Filter RawContacts.ACCOUNT_TYPE = "com.google" for a
Google-only view. Respect READ/WRITE_CONTACTS. Live updates via ContentObserver.
Optional deep sync via Google People API (OAuth) only if a feature needs server-side
data not mirrored locally. WorkManager refreshes derived caches (T9 index, favorites)
on contact changes.

## 15. Nothing-OS Design System (`:core:designsystem`) — own assets only
- 15.1 Palette. Dark (default): true black #000000 surfaces, near-white #EDEDED text,
  mid-greys secondary, hairline dotted dividers #2A2A2A. Light: off-white #F5F5F5 /
  paper #FFFFFF, near-black #0A0A0A. Accent: a single red (e.g. #D7263D) used
  sparingly (active call, record dot, key press); everything else monochrome. M3
  dark/light color schemes mapped to the above; dynamic color DISABLED.
- 15.2 Theme switching: Light/Dark/System; persist in DataStore; cross-fade animate.
- 15.3 Layout language: generous negative space, grid alignment, exposed
  "engineered" framing (thin rules, corner ticks, index labels), dotted separators,
  dot-matrix flourishes. Flat cards with hairline borders, not heavy shadows.
- 15.4 Motion: spring physics (stiffness Medium, dampingRatio LowBouncy), quick
  mechanical easing; dot-matrix "fill" transitions; subtle haptics on key press.
- 15.5 Signature components (in :core:ui): DotMatrixText, GlyphKey, GlyphWaveform,
  DottedDivider, EngineeredCard, TickerCaption, MonoTimer.

## 16. Font System — OG (dot-matrix) vs New (grotesque), user-selectable
DO NOT bundle Nothing's NDot / NType 82. Recreate the aesthetic with licensed
substitutes: OG = an SIL OFL dot-matrix font; New = an open grotesque (Space
Grotesk/Inter) + monospaced numerals (Space Mono/JetBrains Mono). Define two
FontFamilys and LocalAppTypography via CompositionLocal; the selected family flows
through MaterialTheme.typography. Numerals on dialpad/timers/call log always use the
monospaced cut. Verify glyph coverage for the locale; fall back to system font for
unsupported scripts.

## 17. Glyph Interface Integration (`:peripheral:glyph`)
Wrap the Nothing SDK behind `GlyphController` so the app degrades to a no-op on
non-Nothing hardware.
- 17.1 Setup (light-strip phones — GDK): add GDK AAR; namespace com.nothing.ketchum;
  manifest uses-permission com.nothing.ketchum.permission.ENABLE; meta-data
  NothingKey = @string/nothing_api_key (debug builds may use "test"). Register for a
  key via the Nothing Developer Programme. Debug toggle (auto-expires 48h):
  `adb shell settings put global nt_glyph_interface_debug_enable 1`. GDK works only on
  Nothing devices on Android 14+.
- 17.2 Lifecycle: GlyphManager.init(callback) in onCreate; on service connected,
  register(<device constant>), then openSession(); build frames; close()/unInit() on
  teardown. Detect model via isXXXXX() helpers (Phone (1)=20111, (2)=22111,
  (2a)=23111, (2a Plus)=23113, (3a)/(3a Pro)=24111, (4a)=25111). Follow the official
  GDK README for exact current signatures (buildChannel*, buildPeriod, buildCycles,
  buildInterval, toggle/animate/displayProgress).
- 17.3 Phone (3) — Glyph Matrix: use the Glyph Matrix Developer Kit (pixel LED, no
  API key). Provide a matrix impl of GlyphController rendering digit glyphs and a true
  dot-matrix waveform.
- 17.4 Unique per-key strokes (the requested differentiator): each dialpad key fires
  a distinct, recognizable light stroke (different zones, durations, ramp).

  | Key | Stroke concept | Zones / shape | Period |
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

  Strokes must be non-blocking and debounced so fast typing queues/overlaps
  gracefully (cancel-and-restart or short queue). Provide a master "Glyph on dialpad"
  toggle and an intensity slider.
- 17.5 Other choreography: Incoming call per-contact light show (seed pattern from a
  hash of the number) + Glyph torch flash on connect; Active call slow steady glow;
  Hold breathing; Conference multi-zone pulse by party count; Recording persistent
  indicator pattern; Transcription/voice level mirror GlyphWaveform amplitude;
  optional Glyph Composer-style custom ringtone light patterns.
- 17.6 Fallback: on non-Nothing devices (or missing key/permission) isAvailable=false
  and all methods no-op; the UI hides Glyph settings and never crashes.

## 18. "Cool" UI/UX Features (★ = implement at least these)
- ★ Per-digit Glyph choreography on the dialpad (§17.4) with on-screen mirrored dot
  animation.
- ★ AOD-minimal in-call screen: mostly black, monospaced timer, single breathing
  accent, engineered corner ticks, live dot-matrix waveform of call audio (where
  available).
- ★ Dot-matrix everything: connecting spinner, pull-to-refresh, empty states,
  transcription ticker tape.
- Caller "fingerprint": deterministic dot-matrix avatar + Glyph signature from number.
- Quick actions on long-press of a call-log/contact row (call/message/record next/
  copy/block) in a Nothing-style radial/strip menu.
- Haptic morse option per key.
- Themeable accent (curated set) over the monochrome base.
- Home-screen widget (Glyph-styled favorites/speed-dial) + Quick Settings tile to
  toggle recording.
- In-call notes attached to the call record (Room), shown in history.
- "Now playing" recording mini-player with dot-matrix waveform scrubbing.

## 19. Data Layer & Persistence
Room entities (min): RecordingEntity(id, callId, number, contactName, startedAt,
durationMs, tier, filePath, hasTranscript); TranscriptEntity(id, recordingId,
language, fullText) + TranscriptSegmentEntity(id, transcriptId, startMs, endMs, text,
speaker?); BlockedNumberEntity; FavoriteEntity(contactLookupKey, position,
defaultNumber); CallNoteEntity(id, callId, number, text, createdAt);
SpeedDialEntity(slot, contactLookupKey, number).
DataStore (prefs): theme mode, font, glyph master toggle, glyph intensity, accent,
recording enabled, no-announcement, retention window, transcription engine,
default SIM.
WorkManager: PurgeOldDataWorker (retention), ContactsCacheWorker, TranscriptionWorker;
periodic + expedited as appropriate; constrain transcription to charging/idle for
heavy on-device models.

## 20. Permissions & Manifest
Runtime with rationale UI: CALL_PHONE, READ_PHONE_STATE, READ_PHONE_NUMBERS,
MANAGE_OWN_CALLS, READ_CONTACTS, WRITE_CONTACTS, READ_CALL_LOG, WRITE_CALL_LOG,
RECORD_AUDIO, CAMERA, READ_VOICEMAIL, WRITE_VOICEMAIL, POST_NOTIFICATIONS,
FOREGROUND_SERVICE + FOREGROUND_SERVICE_PHONE_CALL/MICROPHONE/CAMERA,
com.nothing.ketchum.permission.ENABLE, INTERNET/network. Declare InCallService,
ConnectionService, CallScreeningService with BIND_* permissions + intent filters.
Default-dialer role gates several capabilities (call-log write, etc.).

## 21. Settings Screen
Grouped, monospaced labels: Appearance (theme, font OG/New, accent); Glyph (master,
dialpad strokes, intensity, incoming-call show, recording indicator; hidden on
non-Nothing); Calls (default SIM, speed-dial, caller-ID/spam, blocked numbers);
Recording (enable, two-way vs my-side read-only per tier, no-announcement +
disclaimer, retention, storage/export); Transcription (engine, live captions,
language, auto-transcribe); Contacts (account filter, sync status); About/legal
(licenses, recording-law disclaimer, open-font + SDK attribution).

## 22. Build, Signing & Output
Gradle Kotlin DSL + version catalog. Build types: debug (Glyph test key, verbose
logging) and release (R8, resource shrinking, real Glyph key from a secured property —
never commit it). Signed APK primary; optional signed AAB. Output
app/build/outputs/apk/release/app-release.apk. R8 keep rules for Hilt, Room, WebRTC,
Glyph SDK (keep com.nothing.ketchum.**).

## 23. Testing
Unit: use cases, T9 matching, libphonenumber formatting, retention/purge, transcript
alignment (JUnit5 + Turbine). Telecom: instrument InCallService/Call; verify
hold/merge/swap transitions. UI: Compose tests for dialpad, call log, in-call,
theme/font switch. Glyph: no-op fallback on non-Nothing emulator; manual on hardware.

## 24. Phased Roadmap
1. Foundation: modules, design system, theme + font switch, navigation shell.
2. Become the dialer: role request, InCallService, place/answer/end, dialpad + T9.
3. Parity core: call log, contacts, favorites/speed dial, search, formatting.
4. In-call depth: mute/speaker/route, hold, DTMF, add call, merge/conference, swap.
5. Glyph: GlyphController + GDK, per-digit strokes, incoming show, fallback.
6. Recording: tiered recorder, storage, player, retention purge, settings + disclaimer.
7. Transcription: engine interface, on-device Whisper default, post-call, live captions.
8. VoIP/video (optional): ConnectionService + WebRTC, audio↔video switch.
9. Parity finish: spam/screening, blocking, voicemail, widget + QS tile.
10. Polish: motion, haptics, delight, accessibility, perf, signing/output.

## 25. Definition of Done
Default phone app placing/receiving real cellular calls with a full in-call UI;
dialpad T9, call log, contacts, favorites, speed dial; in-call hold/mute/route/DTMF/
add-call/conference/merge/swap; on Nothing (A14+) each dialpad key fires a distinct
Glyph stroke, incoming choreography, recording/transcription mirrored; non-Nothing
runs with Glyph hidden; Light/Dark/System + OG/New live switch, monospaced numerals;
recording at the highest supported tier shown to the user, playable, exportable with
consent, auto-purged; transcription searchable + timestamp-aligned + live captions
where allowed; optional VoIP audio↔video; signed APK builds and installs.
