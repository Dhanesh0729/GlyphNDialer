// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner system (BUILD_SPEC §15.3).
 *
 * The Nothing-OS-inspired look favours flat surfaces with hairline borders over
 * heavy shadows, and restrained, slightly-rounded rectangles ("engineered"
 * framing). Corners stay small so the geometry reads as precise rather than soft.
 */
object GlyphShapes {

    /** Sharp, fully-square corners — used for index labels and corner ticks. */
    val Square = RoundedCornerShape(0.dp)

    /** Default card / surface corner. */
    val Card = RoundedCornerShape(12.dp)

    /** Small chips, badges, and inline controls. */
    val Chip = RoundedCornerShape(8.dp)

    /** Large containers (bottom sheets, dialogs). */
    val Sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    /** Pill — full-capsule for primary call buttons and toggles. */
    val Pill = RoundedCornerShape(percent = 50)
}

/**
 * Material 3 [Shapes] mapping for [androidx.compose.material3.MaterialTheme].
 * Kept tight to suit the engineered aesthetic.
 */
val GlyphMaterialShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
