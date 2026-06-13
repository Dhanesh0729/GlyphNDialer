// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.glyphdialer.core.domain.model.AccentColor
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * Default accent for the app. Resolved defensively from the domain [AccentColor]
 * enum so the design system does not hard-depend on a specific constant name: the
 * first declared value is treated as the default (the domain is expected to list
 * the red/default accent first). [GlyphColors.accentColorOf] maps it to
 * [GlyphColors.GlyphRed] regardless.
 */
val DefaultAccent: AccentColor = AccentColor.entries.first()

/**
 * Build the dark [ColorScheme] from the brand palette, tinted with [accent]
 * (BUILD_SPEC §15.1 — monochrome base + single accent; dynamic color OFF).
 */
private fun glyphDarkColorScheme(accentValue: androidx.compose.ui.graphics.Color): ColorScheme =
    darkColorScheme(
        primary = accentValue,
        onPrimary = GlyphColors.OnAccent,
        secondary = GlyphColors.DarkOnSurfaceVariant,
        onSecondary = GlyphColors.DarkSurface,
        tertiary = accentValue,
        onTertiary = GlyphColors.OnAccent,
        background = GlyphColors.DarkSurface,
        onBackground = GlyphColors.DarkOnSurface,
        surface = GlyphColors.DarkSurface,
        onSurface = GlyphColors.DarkOnSurface,
        surfaceVariant = GlyphColors.DarkSurfaceElevated,
        onSurfaceVariant = GlyphColors.DarkOnSurfaceVariant,
        surfaceContainer = GlyphColors.DarkSurfaceElevated,
        surfaceContainerHigh = GlyphColors.DarkSurfaceElevated,
        outline = GlyphColors.DarkOutline,
        outlineVariant = GlyphColors.DarkDivider,
        error = GlyphColors.GlyphRed,
        onError = GlyphColors.OnAccent,
    )

/**
 * Build the light [ColorScheme] from the brand palette, tinted with [accent].
 */
private fun glyphLightColorScheme(accentValue: androidx.compose.ui.graphics.Color): ColorScheme =
    lightColorScheme(
        primary = accentValue,
        onPrimary = GlyphColors.OnAccent,
        secondary = GlyphColors.LightOnSurfaceVariant,
        onSecondary = GlyphColors.LightPaper,
        tertiary = accentValue,
        onTertiary = GlyphColors.OnAccent,
        background = GlyphColors.LightSurface,
        onBackground = GlyphColors.LightOnSurface,
        surface = GlyphColors.LightSurface,
        onSurface = GlyphColors.LightOnSurface,
        surfaceVariant = GlyphColors.LightPaper,
        onSurfaceVariant = GlyphColors.LightOnSurfaceVariant,
        surfaceContainer = GlyphColors.LightPaper,
        surfaceContainerHigh = GlyphColors.LightPaper,
        outline = GlyphColors.LightOutline,
        outlineVariant = GlyphColors.LightDivider,
        error = GlyphColors.GlyphRed,
        onError = GlyphColors.OnAccent,
    )

/**
 * The root theme for Glyph Dialer (CONVENTIONS.md §6 / BUILD_SPEC §15).
 *
 * Wraps [MaterialTheme] with the monochrome brand palette (dynamic color
 * DISABLED), the typography for the chosen [appFont], engineered shapes, and the
 * three design-system CompositionLocals ([LocalAppTypography], [LocalAppFont],
 * [LocalAccentColor], [LocalGlyphIntensity]).
 *
 * @param themeMode LIGHT / DARK / SYSTEM; [ThemeMode.SYSTEM] resolves via
 *   [isSystemInDarkTheme].
 * @param appFont the user-selected face; flows through [MaterialTheme.typography].
 * @param accent the user-selected accent; mapped to a brand hue and provided via
 *   [LocalAccentColor] and the M3 `primary` role.
 * @param glyphIntensity Glyph brightness mirror in `0f..1f` (BUILD_SPEC §17.4).
 */
@Composable
fun GlyphTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appFont: AppFont = AppFont.OG_DOT_MATRIX,
    accent: AccentColor = DefaultAccent,
    glyphIntensity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accentColor = GlyphColors.accentColorOf(accent)

    // Dynamic color is intentionally NOT used — the brand is monochrome (§15.1).
    val colorScheme = remember(darkTheme, accentColor) {
        if (darkTheme) glyphDarkColorScheme(accentColor) else glyphLightColorScheme(accentColor)
    }

    val typography = remember(appFont) { glyphTypography(appFont) }
    val bodyFamily = remember(appFont) { GlyphFonts.bodyFamilyFor(appFont) }
    val intensity = glyphIntensity.coerceIn(0f, 1f)

    CompositionLocalProvider(
        LocalAppTypography provides bodyFamily,
        LocalAppFont provides appFont,
        LocalAccentColor provides accentColor,
        LocalGlyphIntensity provides intensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = GlyphMaterialShapes,
            content = content,
        )
    }
}
