// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.glyphdialer.core.domain.model.AppFont

/**
 * CompositionLocals exposed by [GlyphTheme] (CONVENTIONS.md §6).
 *
 * Consumers read these to honor the active font, accent, and Glyph intensity
 * without threading them through every composable signature. They are PROVIDED by
 * [GlyphTheme]; reading them outside it yields the documented defaults.
 */

/**
 * The chosen body [FontFamily] (CONVENTIONS.md §6: "LocalAppTypography
 * CompositionLocal providing the chosen FontFamily").
 *
 * Components that need the family directly — e.g. to compose a custom style or to
 * mix in [NumberStyle] for monospaced numerals — read this. For ordinary text,
 * prefer `MaterialTheme.typography`, which is already built from this family.
 *
 * Default: [GlyphFonts.OG_DOT_MATRIX].
 */
val LocalAppTypography = staticCompositionLocalOf { GlyphFonts.OG_DOT_MATRIX }

/**
 * The currently selected [AppFont] enum, provided alongside [LocalAppTypography]
 * for components that must branch on *which* face is active (e.g. to special-case
 * the dot-matrix look) rather than just use its [FontFamily].
 *
 * Default: [AppFont.OG_DOT_MATRIX].
 */
val LocalAppFont = staticCompositionLocalOf { AppFont.OG_DOT_MATRIX }

/**
 * The active accent color (resolved from the user's `AccentColor` setting via
 * [GlyphColors.accentColorOf]). Used by components that paint the single accent —
 * the record dot, active-call breathing, key-press flash.
 *
 * Default: [GlyphColors.GlyphRed].
 */
val LocalAccentColor = staticCompositionLocalOf { GlyphColors.GlyphRed }

/**
 * Glyph light intensity in `0f..1f` (BUILD_SPEC §17.4 intensity slider). Mirrors
 * the user's preference so on-screen "mirrored dot" animations can match the
 * physical Glyph brightness. Not static — it can change at runtime from settings.
 *
 * Default: `1f` (full intensity).
 */
val LocalGlyphIntensity = compositionLocalOf { 1f }
