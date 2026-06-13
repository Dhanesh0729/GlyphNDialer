# Legal & attribution

Glyph Dialer is an **independent** application, released under **Apache-2.0** (see the
SPDX headers on each source file). This document covers the call-recording legal
disclaimer, user responsibility, and attribution for the licensed fonts and the Nothing
Glyph SDK. Nothing here is legal advice.

---

## 1. Call recording — your responsibility

**Call-recording law varies by jurisdiction and you are solely responsible for
compliance.** Many regions require **all-party (two-party) consent** before a call may
be recorded; recording without the required consent can be a civil or criminal offence.
Other regions allow one-party consent. The applicable law may depend on where *each*
participant is located, not only where you are.

Glyph Dialer follows an honesty principle (BUILD_SPEC §2, CONVENTIONS.md §9):

- It records only at the **highest tier the platform actually supports** and shows that
  tier in the UI (two-way / "my side only" / unavailable). It never claims to capture
  the remote party when the OS does not allow it.
- The **"record without announcement"** setting exists for jurisdictions where that is
  lawful, but it ships with this disclaimer, and the app **never bypasses an
  OS-mandated recording announcement**.
- Exporting or sharing a recording requires explicit in-app consent.

By using the recording feature you confirm that you will obtain any consent required by
law in your and the other party's jurisdictions and that you accept full responsibility
for your use. The authors and contributors disclaim all liability arising from your use
of the recording feature, to the maximum extent permitted by law.

---

## 2. Trademarks & independence

"Glyph Dialer" is **not** affiliated with, endorsed by, or sponsored by Nothing
Technology Limited. "Nothing", "Glyph", "Glyph Interface", "Glyph Matrix", and related
names and logos are trademarks of their respective owners and are used here only
descriptively to identify compatible hardware and SDKs. The app's visual style is an
*independent interpretation* inspired by the Nothing OS aesthetic; it bundles none of
Nothing's proprietary assets.

---

## 3. Font attribution

Glyph Dialer does **not** bundle Nothing's proprietary `NDot` / `NType 82` typefaces.
It uses open-licensed substitutes that the integrator drops in (see [FONTS.md](FONTS.md));
until then it falls back to the system Monospace / SansSerif families. Replace the
placeholders below with the actual font name, author, and license once a real `.ttf` is
added, and surface this list on the in-app About/legal screen.

| Role | Font | License | Attribution placeholder |
|---|---|---|---|
| OG dot-matrix (`OG_DOT_MATRIX`) | _<SIL OFL dot-matrix face — NOT NDot>_ | SIL Open Font License 1.1 | _Copyright (c) <year> <author>. Reserved Font Name "<name>"._ |
| Grotesque (`NEW_GROTESQUE`) | _<Space Grotesk / Inter>_ | SIL OFL 1.1 | _Copyright (c) <year> <author>._ |
| Monospaced numerals | _<Space Mono / JetBrains Mono>_ | SIL OFL 1.1 / Apache-2.0 | _Copyright (c) <year> <author>._ |

The SIL OFL requires that the font's own license/copyright accompany distribution and
that you not use the Reserved Font Names for derivatives — keep each font's `OFL.txt`
alongside the `.ttf` and reference it here.

---

## 4. Nothing Glyph SDK

Hardware Glyph features integrate Nothing's official **Glyph Developer Kit (GDK)** and
**Glyph Matrix Developer Kit**. These SDKs are **Nothing's property**, are obtained
separately through the **Nothing Developer Programme**, and are governed by Nothing's
own license terms — **not** by Glyph Dialer's Apache-2.0 license. They are **not**
redistributed with this project. Glyph Dialer integrates them only behind the
`GlyphController` abstraction and via reflection, so non-Nothing builds are unaffected
and the SDK is never required to build or run the app. See [GLYPH_SETUP.md](GLYPH_SETUP.md).

The `com.nothing.ketchum.permission.ENABLE` permission and the `NothingKey` meta-data
are declared per Nothing's integration requirements and are inert on non-Nothing devices.

---

## 5. Third-party library licenses

Glyph Dialer depends on open-source libraries pinned in `gradle/libs.versions.toml`,
including (non-exhaustive): AndroidX (Core, Lifecycle, Compose, Navigation, Room,
DataStore, WorkManager, CameraX, Media3) — Apache-2.0; Kotlin & Coroutines — Apache-2.0;
Dagger Hilt — Apache-2.0; Material Components — Apache-2.0; Accompanist — Apache-2.0;
Coil — Apache-2.0; Timber — Apache-2.0; libphonenumber — Apache-2.0; Ktor — Apache-2.0;
`io.getstream:stream-webrtc-android` (WebRTC) — see its BSD-style WebRTC license; Lottie
— Apache-2.0; test libraries (JUnit, Truth, MockK, Turbine, Robolectric, Espresso) under
their respective licenses.

Generate the authoritative, version-accurate list at build time (e.g. an OSS-licenses
Gradle plugin) and display it on the in-app About/legal screen alongside the recording
disclaimer and font/SDK attributions (BUILD_SPEC §21). Each library's full license text
must accompany distribution as that license requires.
