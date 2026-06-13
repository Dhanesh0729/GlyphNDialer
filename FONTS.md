# Fonts

Glyph Dialer offers two user-selectable typeface families (a dot-matrix "OG" face and a
clean grotesque "New" face) with **always-monospaced numerals** on the dialpad, timers,
and call log. To honor licensing and trademark constraints (CONVENTIONS.md §10,
BUILD_SPEC §16) the app **ships no font binaries** and **must not bundle Nothing's
proprietary `NDot` / `NType 82`**. Instead it falls back to the system Monospace /
SansSerif families until you drop in your own SIL OFL / open-licensed `.ttf` files.

> A module-level companion note lives at
> [`core/designsystem/FONTS.md`](core/designsystem/FONTS.md) with the exact code
> changes for `theme/Type.kt`. This root doc is the orientation; that one is the recipe.

---

## What to drop in, and where

Place open-licensed `.ttf` files under:

```
core/designsystem/src/main/res/font/
├── og_dotmatrix.ttf      # OG_DOT_MATRIX  — an SIL OFL dot-matrix face (NOT NDot)
├── new_grotesque.ttf     # NEW_GROTESQUE  — Space Grotesk / Inter
└── mono_numerals.ttf     # numerals       — Space Mono / JetBrains Mono
```

| `AppFont` / role | Intended licensed face | Placeholder until added |
|---|---|---|
| `OG_DOT_MATRIX` | An SIL OFL dot-matrix font (recreates the aesthetic; **not** NDot) | `FontFamily.Monospace` |
| `NEW_GROTESQUE` | Space Grotesk or Inter | `FontFamily.SansSerif` |
| Mono numerals (all digits) | Space Mono or JetBrains Mono | `FontFamily.Monospace` |

Verify glyph coverage for your target locales; the fallback path covers unsupported
scripts so the app never crashes.

---

## How the fallback works (why an empty `.ttf` is NOT acceptable)

A zero-byte or placeholder `.ttf` is invalid — Android fails to inflate it at runtime.
So `core/designsystem/theme/Type.kt` deliberately does **not** reference any `R.font.*`
resource by default; it assigns built-in system families as stand-ins. A
`fontFamilyOrDefault(...)` helper wraps font construction in `runCatching { }` and
returns the system font if the resource is missing or malformed:

```kotlin
val OG_DOT_MATRIX: FontFamily = fontFamilyOrDefault(
    runCatching { FontFamily(Font(R.font.og_dotmatrix)) }.getOrNull()
)
```

The chosen family flows through `LocalAppTypography` (a CompositionLocal) into
`MaterialTheme.typography`. Numerals always render through the monospaced cut — apply
the dedicated `NumberStyle` `TextStyle` at every digit call site (dialpad, `MonoTimer`,
call log).

---

## After adding fonts

1. Replace the placeholder `FontFamily` assignments in `GlyphFonts` (in `theme/Type.kt`)
   with resource-backed families routed through `fontFamilyOrDefault` (see above and
   `core/designsystem/FONTS.md`).
2. Keep each font's `OFL.txt` / license file beside the `.ttf`.
3. Add the open-font attribution (name, author, license) to [LEGAL.md](LEGAL.md) and to
   the in-app About/legal screen (BUILD_SPEC §21).
