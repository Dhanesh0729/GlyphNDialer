# Roadmap

Phased delivery plan, derived from BUILD_SPEC §24. Each phase builds on the previous
and maps onto the feature checklist in [README.md](README.md). Honesty-principle
constraints (BUILD_SPEC §2) apply throughout: every capability exposes a runtime
availability flag and the UI shows it.

| Phase | Theme | Scope |
|---|---|---|
| 1 | **Foundation** | Module structure, design system, theme + font switch (Light/Dark/System, OG/New), navigation shell. |
| 2 | **Become the dialer** | `RoleManager.ROLE_DIALER` request, `InCallService`, place/answer/end, dialpad + live T9 search. |
| 3 | **Parity core** | Call log/Recents (grouping, type icons, filters, search), contacts (list, fast-scroll, detail, create/edit), favorites/speed dial, libphonenumber formatting. |
| 4 | **In-call depth** | Mute, speaker/route picker, hold/resume, in-call DTMF, add call, merge/conference, swap, split. |
| 5 | **Glyph** | `GlyphController` + GDK/Matrix impls (reflection), per-digit strokes (§17.4), incoming-call show, recording indicator, waveform mirror, no-op fallback on non-Nothing. |
| 6 | **Recording** | Tiered `CallRecorder` (system/VoIP/local-one-sided/unavailable), encrypted storage, Media3 player + waveform scrubber, retention auto-purge, settings + no-announcement disclaimer. |
| 7 | **Transcription** | Pluggable `TranscriptionEngine`, on-device Whisper default, post-call transcripts (searchable, timestamp-aligned), live captions (full for VoIP, limited for cellular). |
| 8 | **VoIP / video (optional)** | Self-managed `ConnectionService` + WebRTC peer connection + signaling, audio↔video switching with renegotiation, STUN/TURN. |
| 9 | **Parity finish** | Caller-ID & spam screening (`CallScreeningService`), call blocking (`BlockedNumberContract`), visual voicemail / carrier VM shortcut, home-screen widget + Quick Settings tile. |
| 10 | **Polish** | Spring motion, haptics, dot-matrix delight, accessibility, performance, signing & output (signed APK + optional AAB). |

## Definition of done (BUILD_SPEC §25)

Glyph Dialer is "done" when it is a default phone app that places/receives real cellular
calls with a full in-call UI; has dialpad T9, call log, contacts, favorites, speed dial;
supports in-call hold/mute/route/DTMF/add-call/conference/merge/swap; fires a distinct
Glyph stroke per dialpad key on Nothing (A14+) with incoming choreography and
recording/transcription mirrored, while running cleanly with Glyph hidden on non-Nothing
devices; switches Light/Dark/System + OG/New live with monospaced numerals; records at
the highest supported tier (shown to the user), playable and exportable with consent and
auto-purged; transcribes searchably and timestamp-aligned with live captions where
allowed; optionally does VoIP audio↔video; and produces a signed APK that installs.
