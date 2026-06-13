# Contributing

Thanks for your interest in Glyph Dialer. This is a modular, multi-module Android
project; the most important thing is to respect the **binding engineering contract** in
[CONVENTIONS.md](CONVENTIONS.md) so independently built modules keep fitting together.

## Before you start

- Read [CONVENTIONS.md](CONVENTIONS.md) (the contract) and the relevant section of
  [docs/BUILD_SPEC.md](docs/BUILD_SPEC.md) (the intent).
- Set up your environment per [BUILD.md](BUILD.md) (JDK 17, Android SDK 35, Gradle
  8.10.2 wrapper, `local.properties`).
- Skim [ARCHITECTURE.md](ARCHITECTURE.md) for layers and the dependency graph.

## Ground rules

- **Module boundaries.** Keep to the dependency graph (CONVENTIONS.md §3). Features
  never depend on each other; only `:peripheral:glyph` may import `com.nothing.ketchum.*`;
  domain stays pure (no Compose, no Android framework beyond plain types).
- **Versions live in one place.** Reference dependencies only via `libs.*` aliases from
  `gradle/libs.versions.toml`. Do not add raw coordinates and do not casually edit the
  catalog or `settings.gradle.kts` (parallel edits conflict).
- **Architecture.** Clean Architecture + MVVM (CONVENTIONS.md §5): immutable
  `StateFlow<UiState>`, `onEvent(...)`, effects via `Channel`/`SharedFlow`, constructor
  injection, injected dispatchers (`@Dispatcher(...)`), `AppResult<T>` across layers.
- **Honesty principle (non-negotiable).** Never fake an unsupported platform capability
  (recording, carrier video, transcription source, Glyph). Expose an availability flag
  and surface it in the UI. See CONVENTIONS.md §9 / BUILD_SPEC §2 and [LEGAL.md](LEGAL.md).
- **Style.** Idiomatic Kotlin; Compose composables are PascalCase and state-hoisted;
  logging via **Timber** (no `Log`/`println`); naming `XxxScreen`/`XxxViewModel`/
  `XxxUiState`/`XxxRepository(+Impl)`/`XxxUseCase`/`XxxDao`/`XxxEntity`.
- **Headers.** Every Kotlin file starts with `// SPDX-License-Identifier: Apache-2.0`.
  No Nothing copyright; no proprietary fonts or assets.
- **Each module ships a `consumer-rules.pro`** (a header comment is fine if no rules are
  needed).

## Tests

Add JUnit5 + Turbine + Truth unit tests for pure logic (T9 matching, libphonenumber
formatting, retention math, Glyph stroke timing, transcript alignment, reducers). Use
Robolectric where Android types intrude. A representative Compose UI test and a telecom
state-transition test are enough to show shape (CONVENTIONS.md §11). Run:

```
./gradlew test
```

## Pull requests

1. Branch off the default branch.
2. Keep the change scoped to one module/concern where possible.
3. Ensure `./gradlew assembleDebug` and `./gradlew test` pass.
4. Update the relevant docs (README checklist, ARCHITECTURE, etc.) if behavior changes.
5. Note any honesty-principle implications (new availability flags, UI disclosure).

By contributing you agree your contributions are licensed under **Apache-2.0**.
