// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph.choreography

/**
 * Pure-Kotlin, SDK-free description of the per-dialpad-key Glyph light strokes from
 * BUILD_SPEC §17.4. This is a *data model* only — it carries no Android/GDK types —
 * so the choreography is unit-testable in isolation (CONVENTIONS.md §11) and the same
 * description can drive either a light-strip phone (GDK zones) or the Phone (3) Glyph
 * Matrix.
 *
 * A [GlyphStroke] is an ordered list of [GlyphFrame]s. Each frame names the abstract
 * [GlyphZone]s it lights, the intensity ramp across the frame ([fromIntensity] →
 * [toIntensity], 0f..1f), and its [durationMs]. The stroke's total [GlyphStroke.periodMs]
 * is the sum of its frame durations and must match the "Period" column of §17.4.
 *
 * Controllers translate the abstract zones to concrete hardware: GDK channel ids for
 * light-strip phones, matrix pixel regions for the Glyph Matrix. The library itself is
 * deliberately hardware-agnostic.
 */

/**
 * Abstract Glyph zones, named after the physical regions §17.4 references. Controllers
 * map these onto the concrete channel/pixel layout of the specific device. Order is the
 * natural "chase" order used by sequential strokes (spiral, sweeps).
 */
enum class GlyphZone {
    TOP_LEFT,
    TOP_RIGHT,
    CAMERA_RING,
    CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    ALL,
}

/**
 * One timed step of a stroke.
 *
 * @param zones the zones lit during this frame (empty = darkness/gap).
 * @param fromIntensity intensity at the start of the frame (0f..1f).
 * @param toIntensity intensity at the end of the frame (0f..1f); a linear ramp between
 *   the two models §17.4's "ramp up", "fade", and sharp on/off (from == to) shapes.
 * @param durationMs how long this frame lasts, in milliseconds (> 0).
 */
data class GlyphFrame(
    val zones: List<GlyphZone>,
    val fromIntensity: Float,
    val toIntensity: Float,
    val durationMs: Int,
) {
    init {
        require(durationMs > 0) { "frame durationMs must be > 0, was $durationMs" }
        require(fromIntensity in 0f..1f) { "fromIntensity out of range: $fromIntensity" }
        require(toIntensity in 0f..1f) { "toIntensity out of range: $toIntensity" }
    }

    /** The peak intensity reached anywhere in this frame. */
    val peakIntensity: Float get() = maxOf(fromIntensity, toIntensity)
}

/**
 * A complete per-key light stroke: an ordered list of [frames] plus the human-facing
 * [concept] from §17.4 (for logging/diagnostics).
 */
data class GlyphStroke(
    val key: Char,
    val concept: String,
    val frames: List<GlyphFrame>,
) {
    init {
        require(frames.isNotEmpty()) { "stroke '$key' must have at least one frame" }
    }

    /** Total period of the stroke (sum of frame durations) — matches §17.4's Period column. */
    val periodMs: Int get() = frames.sumOf { it.durationMs }

    /** Every distinct zone touched by the stroke. */
    val zonesTouched: Set<GlyphZone> get() = frames.flatMap { it.zones }.toSet()
}

/**
 * A tiny builder making each stroke definition read like its §17.4 row. Not strictly
 * necessary, but keeps the table below declarative and tweakable.
 */
class GlyphStrokeBuilder(private val key: Char, private val concept: String) {
    private val frames = mutableListOf<GlyphFrame>()

    /** A flat (constant-intensity) frame. */
    fun hold(vararg zones: GlyphZone, intensity: Float, durationMs: Int) = apply {
        frames += GlyphFrame(zones.toList(), intensity, intensity, durationMs)
    }

    /** A ramping frame (linear [from] → [to]). */
    fun ramp(vararg zones: GlyphZone, from: Float, to: Float, durationMs: Int) = apply {
        frames += GlyphFrame(zones.toList(), from, to, durationMs)
    }

    /** A dark gap (no zones lit) used to separate beats. */
    fun gap(durationMs: Int) = apply {
        frames += GlyphFrame(emptyList(), 0f, 0f, durationMs)
    }

    fun build(): GlyphStroke = GlyphStroke(key, concept, frames.toList())
}

private fun stroke(key: Char, concept: String, block: GlyphStrokeBuilder.() -> Unit): GlyphStroke =
    GlyphStrokeBuilder(key, concept).apply(block).build()

/**
 * The full §17.4 stroke table for 0-9, `*` and `#`. Each entry is a distinct,
 * recognizable shape whose total period matches the spec.
 *
 * Periods (spec → realised):
 * `1`=120, `2`=200, `3`=2×90=180, `4`=200, `5`=220, `6`=240, `7`=320, `8`=2×80=160,
 * `9`=280, `0`=100, `*`=180, `#`=150.
 */
object GlyphStrokeLibrary {

    /** Sequential chase order used by spiral/sweep strokes. */
    private val CHASE = listOf(
        GlyphZone.TOP_LEFT,
        GlyphZone.TOP_RIGHT,
        GlyphZone.CAMERA_RING,
        GlyphZone.BOTTOM_RIGHT,
        GlyphZone.BOTTOM_CENTER,
        GlyphZone.BOTTOM_LEFT,
    )

    private val strokes: Map<Char, GlyphStroke> = buildMap {

        // 1 | single short tick | top-right zone, sharp on/off | 120 ms
        put('1', stroke('1', "single short tick") {
            hold(GlyphZone.TOP_RIGHT, intensity = 1f, durationMs = 120)
        })

        // 2 | rising sweep | bottom strip L→R ramp up | 200 ms
        put('2', stroke('2', "rising sweep") {
            ramp(GlyphZone.BOTTOM_LEFT, from = 0.1f, to = 0.6f, durationMs = 70)
            ramp(GlyphZone.BOTTOM_CENTER, from = 0.4f, to = 0.8f, durationMs = 65)
            ramp(GlyphZone.BOTTOM_RIGHT, from = 0.6f, to = 1f, durationMs = 65)
        })

        // 3 | double pulse | camera ring, two beats | 2×90 ms
        put('3', stroke('3', "double pulse") {
            hold(GlyphZone.CAMERA_RING, intensity = 1f, durationMs = 60)
            gap(30)
            hold(GlyphZone.CAMERA_RING, intensity = 1f, durationMs = 60)
            gap(30)
        })

        // 4 | descending sweep | bottom strip R→L | 200 ms
        put('4', stroke('4', "descending sweep") {
            ramp(GlyphZone.BOTTOM_RIGHT, from = 1f, to = 0.6f, durationMs = 70)
            ramp(GlyphZone.BOTTOM_CENTER, from = 0.8f, to = 0.4f, durationMs = 65)
            ramp(GlyphZone.BOTTOM_LEFT, from = 0.6f, to = 0.1f, durationMs = 65)
        })

        // 5 | center bloom | center out to edges | 220 ms
        put('5', stroke('5', "center bloom") {
            ramp(GlyphZone.CENTER, from = 0f, to = 1f, durationMs = 80)
            hold(GlyphZone.CENTER, GlyphZone.CAMERA_RING, intensity = 0.9f, durationMs = 70)
            ramp(GlyphZone.ALL, from = 0.9f, to = 0f, durationMs = 70)
        })

        // 6 | corner arc | top-right 16-zone arc | 240 ms
        put('6', stroke('6', "corner arc") {
            ramp(GlyphZone.TOP_RIGHT, from = 0.2f, to = 1f, durationMs = 120)
            ramp(GlyphZone.CAMERA_RING, from = 1f, to = 0.2f, durationMs = 120)
        })

        // 7 | long fade | full breathe up then down | 320 ms
        put('7', stroke('7', "long fade") {
            ramp(GlyphZone.ALL, from = 0f, to = 1f, durationMs = 160)
            ramp(GlyphZone.ALL, from = 1f, to = 0f, durationMs = 160)
        })

        // 8 | infinity flicker | alternate two zones | 2×80 ms
        put('8', stroke('8', "infinity flicker") {
            hold(GlyphZone.TOP_LEFT, intensity = 1f, durationMs = 80)
            hold(GlyphZone.BOTTOM_RIGHT, intensity = 1f, durationMs = 80)
        })

        // 9 | spiral | sequential zone chase | 280 ms
        put('9', stroke('9', "spiral") {
            // Six-step chase; durations sum to 280 (47+47+47+47+46+46).
            val durations = intArrayOf(47, 47, 47, 47, 46, 46)
            CHASE.forEachIndexed { i, zone ->
                hold(zone, intensity = 1f, durationMs = durations[i])
            }
        })

        // 0 | full flash | all zones, brief | 100 ms
        put('0', stroke('0', "full flash") {
            hold(GlyphZone.ALL, intensity = 1f, durationMs = 100)
        })

        // * | sparkle | random zones twinkle | 180 ms
        //   Deterministic pseudo-"random" twinkle so it is reproducible/testable.
        put('*', stroke('*', "sparkle") {
            hold(GlyphZone.TOP_RIGHT, GlyphZone.BOTTOM_LEFT, intensity = 0.9f, durationMs = 45)
            hold(GlyphZone.TOP_LEFT, GlyphZone.BOTTOM_RIGHT, intensity = 0.7f, durationMs = 45)
            hold(GlyphZone.CAMERA_RING, GlyphZone.BOTTOM_CENTER, intensity = 1f, durationMs = 45)
            hold(GlyphZone.CENTER, GlyphZone.TOP_RIGHT, intensity = 0.6f, durationMs = 45)
        })

        // # | hash blink | grid-like alternation | 150 ms
        put('#', stroke('#', "hash blink") {
            hold(GlyphZone.TOP_LEFT, GlyphZone.BOTTOM_RIGHT, intensity = 1f, durationMs = 50)
            hold(GlyphZone.TOP_RIGHT, GlyphZone.BOTTOM_LEFT, intensity = 1f, durationMs = 50)
            hold(GlyphZone.TOP_LEFT, GlyphZone.BOTTOM_RIGHT, intensity = 1f, durationMs = 50)
        })
    }

    /** Every key the library defines, in dialpad order. */
    val keys: Set<Char> get() = strokes.keys

    /** The stroke for [digit], or `null` if [digit] is not a dialpad key. */
    fun strokeFor(digit: Char): GlyphStroke? = strokes[digit]

    /** Immutable snapshot of all strokes (handy for tests / diagnostics). */
    fun all(): Map<Char, GlyphStroke> = strokes
}
