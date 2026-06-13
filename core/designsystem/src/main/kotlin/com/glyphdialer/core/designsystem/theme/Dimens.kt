// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing grid and sizing tokens (BUILD_SPEC §15.3 — grid alignment, generous
 * negative space). A single 4dp base unit keeps everything on a consistent rhythm.
 *
 * Exposed as a plain object (rather than a CompositionLocal) because spacing is a
 * fixed design constant, not a runtime-themed value.
 */
object Dimens {

    /** Base grid unit. All spacing is a multiple of this. */
    val grid: Dp = 4.dp

    // ---- Spacing scale (multiples of [grid]) ---------------------------------
    val spaceXxs: Dp = 2.dp
    val spaceXs: Dp = 4.dp
    val spaceSm: Dp = 8.dp
    val spaceMd: Dp = 12.dp
    val spaceLg: Dp = 16.dp
    val spaceXl: Dp = 24.dp
    val spaceXxl: Dp = 32.dp
    val spaceXxxl: Dp = 48.dp

    /** Standard screen edge padding. */
    val screenPadding: Dp = 20.dp

    // ---- Borders / rules (engineered framing) --------------------------------
    /** Hairline border / rule width used for cards and dividers. */
    val hairline: Dp = 1.dp

    /** Diameter of a single dotted-divider dot. */
    val dotSize: Dp = 2.dp

    /** Gap between dots in a dotted divider. */
    val dotGap: Dp = 4.dp

    /** Length of an "engineered" corner tick. */
    val cornerTick: Dp = 10.dp

    // ---- Component sizing -----------------------------------------------------
    /** Minimum touch target (accessibility). */
    val minTouchTarget: Dp = 48.dp

    /** Dialpad key diameter. */
    val dialKeySize: Dp = 72.dp

    /** Primary call button diameter. */
    val callButtonSize: Dp = 64.dp

    /** Avatar size in list rows. */
    val avatarSm: Dp = 40.dp

    /** Avatar size on detail/in-call screens. */
    val avatarLg: Dp = 96.dp

    /** Icon button size. */
    val iconButton: Dp = 40.dp

    /** Standard list row height. */
    val rowHeight: Dp = 56.dp

    /** Height of the live-caption / waveform ticker overlay. */
    val tickerHeight: Dp = 32.dp
}
