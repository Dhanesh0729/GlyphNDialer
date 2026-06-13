# Fonts — :core:designsystem

Per CONVENTIONS.md §10 and BUILD_SPEC §16, Glyph Dialer ships **its own** typeface
assets and must **not** bundle Nothing's proprietary `NDot` / `NType 82` fonts.

## Current state (generated build)

Real `.ttf` binaries cannot be generated here, and an empty/placeholder `.ttf` is
**not** valid (it would fail to inflate). So `theme/Type.kt` deliberately does
**not** reference any `R.font.*` resource. Instead it uses built-in system
families as stand-ins:

| `AppFont` / role        | Placeholder family       | Intended licensed face                     |
|-------------------------|--------------------------|--------------------------------------------|
| `OG_DOT_MATRIX`         | `FontFamily.Monospace`   | An SIL OFL dot-matrix face (NOT NDot)      |
| `NEW_GROTESQUE`         | `FontFamily.SansSerif`   | Space Grotesk / Inter                      |
| `MonoNumerals` (digits) | `FontFamily.Monospace`   | Space Mono / JetBrains Mono                |

## How to drop in the real fonts

1. Place OFL/open-licensed `.ttf` files under
   `core/designsystem/src/main/res/font/`:
   - `og_dotmatrix.ttf`
   - `new_grotesque.ttf`
   - `mono_numerals.ttf`
2. In `theme/Type.kt`, replace the placeholder `FontFamily` assignments in
   `GlyphFonts` with resource-backed families, routed through `fontFamilyOrDefault`
   for crash-proof fallback, e.g.:

   ```kotlin
   val OG_DOT_MATRIX: FontFamily = fontFamilyOrDefault(
       runCatching { FontFamily(Font(R.font.og_dotmatrix)) }.getOrNull()
   )
   ```
3. Verify glyph coverage for target locales; `fontFamilyOrDefault` falls back to
   the system font for unsupported scripts so the app never crashes (§16).
4. Add open-font attribution to the About/legal screen (BUILD_SPEC §21).

Numerals on the dialpad, timers, and call log must always render through
`MonoNumerals` — apply the `NumberStyle` `TextStyle` at those call sites.
