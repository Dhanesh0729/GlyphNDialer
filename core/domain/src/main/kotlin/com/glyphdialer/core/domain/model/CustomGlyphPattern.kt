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
    val frames: List<CustomGlyphFrame>
)

/**
 * A single frame in a custom Glyph sequence.
 *
 * @param zones The LED zones that should light up during this frame.
 * @param intensity The brightness level (0f..1f).
 * @param durationMs How long this frame lasts in milliseconds.
 */
data class CustomGlyphFrame(
    val zones: List<CustomGlyphZone>,
    val intensity: Float,
    val durationMs: Int
)

/**
 * The physical LED zones on the device.
 * Corresponds to the regions in the Nothing Glyph interface.
 */
enum class CustomGlyphZone {
    TOP_LEFT,
    TOP_RIGHT,
    CAMERA_RING,
    CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    ALL
}
