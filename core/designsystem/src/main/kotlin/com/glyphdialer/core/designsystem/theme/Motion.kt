// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens (BUILD_SPEC §15.4 / CONVENTIONS.md §6).
 *
 * The aesthetic is spring physics for anything tactile (key presses, sheet
 * reveals, the dot-matrix "fill") plus quick, mechanical easing for crossfades
 * and micro-transitions.
 */
object GlyphSprings {

    /**
     * The signature bouncy spring (stiffness Medium, dampingRatio LowBouncy).
     * Generic so it can type-infer for any animated value (`Float`, `Dp`, `Offset`…).
     *
     * Usage: `animateFloatAsState(target, animationSpec = GlyphSprings.bouncy())`.
     */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** A snappier, non-overshooting spring for controls that should feel precise. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** A soft settle for large surfaces (sheets, dialogs). */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )
}

/**
 * Easing curves. [Quick] is the default mechanical curve for crossfades and small
 * state changes; [Standard] is the M3-style emphasized curve.
 */
object GlyphEasing {
    /** Quick mechanical easing — fast out, gentle in. */
    val Quick: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Standard emphasized easing. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** Linear — used for continuous loops (waveform, dot-matrix spinner). */
    val Linear: Easing = LinearEasing
}

/**
 * Canonical durations in milliseconds. Kept short to feel "fast and tactile".
 */
object GlyphDurations {
    const val Instant: Int = 80
    const val Quick: Int = 150
    const val Medium: Int = 250
    const val Slow: Int = 400

    /** Theme cross-fade duration (BUILD_SPEC §15.2). */
    const val ThemeCrossfade: Int = 300
}

/** Convenience tween specs built from [GlyphDurations] + [GlyphEasing]. */
object GlyphTweens {
    fun <T> quick(): FiniteAnimationSpec<T> =
        tween(durationMillis = GlyphDurations.Quick, easing = GlyphEasing.Quick)

    fun <T> medium(): FiniteAnimationSpec<T> =
        tween(durationMillis = GlyphDurations.Medium, easing = GlyphEasing.Standard)

    fun <T> themeCrossfade(): FiniteAnimationSpec<T> =
        tween(durationMillis = GlyphDurations.ThemeCrossfade, easing = GlyphEasing.Standard)
}
