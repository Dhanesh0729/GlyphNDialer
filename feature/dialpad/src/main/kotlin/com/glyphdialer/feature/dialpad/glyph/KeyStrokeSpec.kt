// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad.glyph

/**
 * The on-screen mirror of the per-digit Glyph light stroke (BUILD_SPEC §17.4 + §18 ★).
 *
 * The physical Glyph choreography lives behind `GlyphController.playDigitStroke` in
 * `:peripheral:glyph`; this feature module is NOT allowed to reference GDK types
 * (CONVENTIONS.md §3). So we keep an independent, pure description of each key's
 * stroke *concept* here and render it as a small dot animation on the dialpad
 * (the "on-screen mirrored dot-stroke" required by the task). The two are seeded from
 * the same §17.4 table so the mirror visually matches the physical pattern.
 *
 * Pure and allocation-free to look up; fully unit-testable (no Android, no Compose).
 */
enum class StrokeShape {
    /** Single short tick — a brief flash of one zone. */
    TICK,

    /** A directional sweep that ramps across the dot strip. */
    SWEEP,

    /** Two quick beats. */
    DOUBLE_PULSE,

    /** Fills from the center outwards. */
    BLOOM,

    /** An arc traced through a corner. */
    ARC,

    /** A long breathe up then down. */
    FADE,

    /** Alternating flicker between two zones. */
    FLICKER,

    /** Sequential zone chase. */
    SPIRAL,

    /** All zones flash briefly. */
    FLASH,

    /** Random zones twinkle. */
    SPARKLE,

    /** Grid-like alternation. */
    HASH,
}

/** Sweep direction for [StrokeShape.SWEEP]. */
enum class StrokeDirection { NONE, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

/**
 * A fully-resolved descriptor for one key's stroke animation.
 *
 * @property shape the visual concept (mirrors the §17.4 "Stroke concept" column).
 * @property periodMillis total animation duration in ms (the §17.4 "Period" column;
 *   double-beat keys use the per-beat value × beats).
 * @property beats how many discrete pulses the shape has (1 for continuous shapes).
 * @property direction sweep direction (only meaningful for [StrokeShape.SWEEP]).
 */
data class KeyStrokeSpec(
    val shape: StrokeShape,
    val periodMillis: Int,
    val beats: Int = 1,
    val direction: StrokeDirection = StrokeDirection.NONE,
) {
    companion object {
        /**
         * Resolve the §17.4 stroke for [digit]. Unknown characters fall back to a
         * neutral short [StrokeShape.TICK] so the mirror never throws.
         */
        fun forDigit(digit: Char): KeyStrokeSpec = when (digit) {
            '1' -> KeyStrokeSpec(StrokeShape.TICK, periodMillis = 120)
            '2' -> KeyStrokeSpec(StrokeShape.SWEEP, periodMillis = 200, direction = StrokeDirection.LEFT_TO_RIGHT)
            '3' -> KeyStrokeSpec(StrokeShape.DOUBLE_PULSE, periodMillis = 180, beats = 2)
            '4' -> KeyStrokeSpec(StrokeShape.SWEEP, periodMillis = 200, direction = StrokeDirection.RIGHT_TO_LEFT)
            '5' -> KeyStrokeSpec(StrokeShape.BLOOM, periodMillis = 220)
            '6' -> KeyStrokeSpec(StrokeShape.ARC, periodMillis = 240)
            '7' -> KeyStrokeSpec(StrokeShape.FADE, periodMillis = 320)
            '8' -> KeyStrokeSpec(StrokeShape.FLICKER, periodMillis = 160, beats = 2)
            '9' -> KeyStrokeSpec(StrokeShape.SPIRAL, periodMillis = 280)
            '0' -> KeyStrokeSpec(StrokeShape.FLASH, periodMillis = 100)
            '*' -> KeyStrokeSpec(StrokeShape.SPARKLE, periodMillis = 180)
            '#' -> KeyStrokeSpec(StrokeShape.HASH, periodMillis = 150)
            else -> KeyStrokeSpec(StrokeShape.TICK, periodMillis = 120)
        }
    }
}
