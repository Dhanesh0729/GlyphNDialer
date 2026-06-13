// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.glyphdialer.core.domain.model.AccentColor

/**
 * The Glyph Dialer palette (BUILD_SPEC §15.1 / CONVENTIONS.md §6).
 *
 * Monochrome by design: true-black dark surfaces and paper-white light surfaces,
 * with a single restrained accent (default [GlyphRed]). Material 3 dynamic color
 * is DISABLED everywhere — the brand identity is the monochrome base plus one
 * curated accent the user may swap.
 *
 * These are raw brand tokens. [Theme.kt] maps them onto M3 `ColorScheme` roles;
 * components should prefer `MaterialTheme.colorScheme` over these constants so
 * theme switching stays centralized. The named tokens are exposed for the few
 * places that need an exact brand value (e.g. the record dot, dotted dividers).
 */
object GlyphColors {

    // ---- Dark theme (default) -------------------------------------------------
    /** True black — the signature dark surface (§15.1). */
    val DarkSurface = Color(0xFF000000)

    /** Slightly lifted black for cards/elevated surfaces so hairline borders read. */
    val DarkSurfaceElevated = Color(0xFF0B0B0B)

    /** Near-white primary text/icons on dark. */
    val DarkOnSurface = Color(0xFFEDEDED)

    /** Mid-grey secondary text/icons on dark. */
    val DarkOnSurfaceVariant = Color(0xFF9A9A9A)

    /** Dimmer grey for tertiary/disabled content on dark. */
    val DarkOutline = Color(0xFF5A5A5A)

    /** Hairline dotted divider on dark (§15.1). */
    val DarkDivider = Color(0xFF2A2A2A)

    // ---- Light theme ----------------------------------------------------------
    /** Off-white base surface (§15.1). */
    val LightSurface = Color(0xFFF5F5F5)

    /** Paper white for elevated cards. */
    val LightPaper = Color(0xFFFFFFFF)

    /** Near-black primary text/icons on light. */
    val LightOnSurface = Color(0xFF0A0A0A)

    /** Mid-grey secondary text/icons on light. */
    val LightOnSurfaceVariant = Color(0xFF595959)

    /** Lighter grey outline on light. */
    val LightOutline = Color(0xFFAFAFAF)

    /** Hairline dotted divider on light (a touch lighter than dark, §15.1). */
    val LightDivider = Color(0xFFD9D9D9)

    // ---- Accent ---------------------------------------------------------------
    /** The default single red accent (§15.1). Used sparingly. */
    val GlyphRed = Color(0xFFD7263D)

    /** A subdued "on accent" foreground for content drawn atop the accent. */
    val OnAccent = Color(0xFFFFFFFF)

    /**
     * Curated accent palette layered over the monochrome base (BUILD_SPEC §18 —
     * "Themeable accent (curated set)"). The keys are the domain [AccentColor]
     * enum; the values are the exact brand hues.
     *
     * NOTE: this map is resolved defensively via [accentColorOf] so that if the
     * domain enum gains or renames constants in a parallel build, the design
     * system still falls back to [GlyphRed] instead of throwing.
     */
    val accentSwatches: Map<AccentColor, Color> = buildMap {
        AccentColor.entries.forEach { accent ->
            put(accent, swatchFor(accent.name))
        }
    }

    /**
     * Resolve a domain [AccentColor] to its brand [Color], defaulting to
     * [GlyphRed] for any value not present in [accentSwatches].
     */
    fun accentColorOf(accent: AccentColor): Color =
        accentSwatches[accent] ?: GlyphRed

    /**
     * Map an accent enum *name* to a curated hue. Matching is done on the name so
     * the design system is resilient to the exact constant set declared in
     * :core:domain. Unknown names fall back to [GlyphRed].
     */
    private fun swatchFor(name: String): Color = when (name.uppercase()) {
        "RED", "GLYPH_RED", "DEFAULT" -> GlyphRed
        "AMBER", "ORANGE" -> Color(0xFFFF7A00)
        "YELLOW" -> Color(0xFFF5C518)
        "GREEN", "LIME" -> Color(0xFF3DDC84)
        "CYAN", "TEAL" -> Color(0xFF1FB6C1)
        "BLUE" -> Color(0xFF2F6BFF)
        "INDIGO", "VIOLET", "PURPLE" -> Color(0xFF7C4DFF)
        "PINK", "MAGENTA" -> Color(0xFFFF4D8D)
        "WHITE", "MONO", "MONOCHROME" -> Color(0xFFEDEDED)
        else -> GlyphRed
    }
}
