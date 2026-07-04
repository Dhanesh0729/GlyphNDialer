// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

import java.util.UUID

/**
 * A user-created custom Glyph sequence.
 *
 * [id] Unique identifier for the custom pattern.
 * [name] The user-provided name for this pattern.
 * [frames] The sequence of frames that make up this pattern.
 */
data class CustomGlyphPattern(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val frames: List<CustomGlyphFrame>,
    val soundStyle: GlyphSoundStyle = GlyphSoundStyle.SOFT_TICK,
    val repeatCount: Int = 1,
) {
    val durationMs: Int get() = frames.sumOf { it.durationMs } * repeatCount.coerceAtLeast(1)

    val isPlayable: Boolean get() = frames.isNotEmpty() && frames.all { it.durationMs > 0 }
}

/**
 * A single frame in a custom Glyph sequence.
 *
 * @param zones The LED zones that should light up during this frame.
 * @param intensity The brightness level (0f..1f).
 * @param durationMs How long this frame lasts in milliseconds.
 * @param soundCue Optional per-frame sound cue layered over the pattern's [GlyphSoundStyle].
 */
data class CustomGlyphFrame(
    val zones: List<CustomGlyphZone>,
    val intensity: Float,
    val durationMs: Int,
    val soundCue: GlyphFrameSound = GlyphFrameSound.FOLLOW_PATTERN,
) {
    val safeIntensity: Float get() = intensity.coerceIn(0f, 1f)

    val safeDurationMs: Int get() = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

    companion object {
        const val MIN_DURATION_MS = 40
        const val MAX_DURATION_MS = 2_000
    }
}

/**
 * Abstract, device-agnostic Glyph zones. Peripheral controllers map these zones to
 * the actual channel layout for each Nothing phone model.
 */
enum class CustomGlyphZone {
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
 * Sound layer for a custom pattern preview. The call-ringtone itself remains user-
 * chosen; this controls the short composer preview ticks and optional synced cues.
 */
enum class GlyphSoundStyle {
    SILENT,
    SOFT_TICK,
    MECHANICAL,
    RING_PULSE,
    GLITCH,
}

/** Optional per-frame cue for making one frame sound different from another. */
enum class GlyphFrameSound {
    FOLLOW_PATTERN,
    MUTED,
    TICK,
    PULSE,
    CHIME,
}
