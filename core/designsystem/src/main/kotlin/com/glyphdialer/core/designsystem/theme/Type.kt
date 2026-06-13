// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glyphdialer.core.domain.model.AppFont

/**
 * Typography for Glyph Dialer (BUILD_SPEC §16 / CONVENTIONS.md §10).
 *
 * Two user-selectable families flow through [MaterialTheme.typography] via the
 * [AppFont] setting:
 *  - [AppFont.OG_DOT_MATRIX] — the dot-matrix "OG" face.
 *  - [AppFont.NEW_GROTESQUE] — a clean grotesque face.
 *
 * IMPORTANT — why no `R.font.*` here:
 * Real `.ttf` binaries are NOT bundled in this generated module. Referencing
 * `R.font.og_dotmatrix` / `R.font.new_grotesque` / `R.font.mono_numerals` would
 * fail to compile (the resource ids don't exist) and would crash at inflation if
 * the files were missing. So we use built-in system stand-ins now and document
 * the swap. See FONTS.md.
 *
 * SWAP PROCEDURE (documented for the human who drops in licensed fonts):
 *  1. Add OFL/open `.ttf` files under `src/main/res/font/`:
 *       - `og_dotmatrix.ttf`   (an SIL OFL dot-matrix face — never NDot)
 *       - `new_grotesque.ttf`  (e.g. Space Grotesk / Inter)
 *       - `mono_numerals.ttf`  (e.g. Space Mono / JetBrains Mono)
 *  2. Replace the placeholder [FontFamily] assignments below with
 *       `FontFamily(Font(R.font.og_dotmatrix))` etc., wrapped in
 *       [fontFamilyOrDefault] so a missing/locale-unsupported font degrades to
 *       the system font instead of crashing (BUILD_SPEC §16 fallback).
 */
object GlyphFonts {

    /**
     * Dot-matrix "OG" face. Placeholder = [FontFamily.Monospace]; swap for an
     * OFL dot-matrix `.ttf` (see file header). The monospace stand-in keeps the
     * mechanical, fixed-grid feel until the real face is dropped in.
     */
    val OG_DOT_MATRIX: FontFamily = FontFamily.Monospace

    /**
     * Grotesque "new" face. Placeholder = [FontFamily.SansSerif]; swap for
     * Space Grotesk / Inter.
     */
    val NEW_GROTESQUE: FontFamily = FontFamily.SansSerif

    /**
     * Dedicated monospaced numerals. Dialpad, timers, and call-log durations
     * ALWAYS render through this family regardless of the selected [AppFont]
     * (BUILD_SPEC §16: "Numerals ... always use the monospaced cut").
     * Placeholder = [FontFamily.Monospace]; swap for Space Mono / JetBrains Mono.
     */
    val MonoNumerals: FontFamily = FontFamily.Monospace

    /**
     * Resolve an [AppFont] to its body [FontFamily], defaulting to the system
     * font for anything unexpected. Once real fonts are wired, this is also the
     * place to honor [fontFamilyOrDefault] glyph-coverage fallback.
     */
    fun bodyFamilyFor(appFont: AppFont): FontFamily = when (appFont) {
        AppFont.OG_DOT_MATRIX -> OG_DOT_MATRIX
        AppFont.NEW_GROTESQUE -> NEW_GROTESQUE
    }
}

/**
 * Returns [preferred] if it is non-null (i.e. a real font resource resolved),
 * otherwise the system [FontFamily.Default]. This is the crash-proof seam called
 * out in BUILD_SPEC §16: when a licensed `.ttf` is absent or lacks glyph coverage
 * for the active locale, fall back to the platform font so the app never crashes.
 *
 * With placeholders the system families are always non-null, so this is a no-op
 * today — but consumers and the future `R.font.*` swap should route through it:
 * `fontFamilyOrDefault(runCatching { FontFamily(Font(R.font.og_dotmatrix)) }.getOrNull())`.
 */
fun fontFamilyOrDefault(preferred: FontFamily?): FontFamily =
    preferred ?: FontFamily.Default

/**
 * A reusable [TextStyle] forcing [GlyphFonts.MonoNumerals]. Components rendering
 * digits (dialpad keys, [MonoTimer], call-log durations) should apply this on top
 * of their base style so numerals stay monospaced even under the grotesque face.
 */
val NumberStyle: TextStyle = TextStyle(
    fontFamily = GlyphFonts.MonoNumerals,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum", // tabular figures — even columns in timers/logs
)

/**
 * Build the Material 3 [Typography] for the selected [appFont].
 *
 * All text styles use the chosen body family, EXCEPT that callers who render
 * numerals should layer [NumberStyle] on top (we cannot force-monospace only the
 * digit glyphs within an M3 style, so numeral-monospacing is applied at the call
 * site via [NumberStyle]). Display/headline scales lean slightly tighter to suit
 * the engineered, dot-matrix aesthetic.
 */
fun glyphTypography(appFont: AppFont): Typography {
    val body = GlyphFonts.bodyFamilyFor(appFont)

    return Typography(
        displayLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}
