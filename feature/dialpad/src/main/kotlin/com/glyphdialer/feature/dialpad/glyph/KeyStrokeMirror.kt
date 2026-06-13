// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad.glyph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.designsystem.theme.LocalGlyphIntensity
import com.glyphdialer.feature.dialpad.StrokeTrigger
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sin

/**
 * The ★ on-screen mirror of the per-digit Glyph light stroke (BUILD_SPEC §17.4 / §18).
 *
 * Renders a single row of dots and animates them per the [KeyStrokeSpec] for the most
 * recently pressed key ([trigger]), echoing the physical Glyph pattern on screen so the
 * differentiator is visible on any device — honest by design: this plays regardless of
 * Glyph hardware, while the physical stroke is fired separately and only when available.
 *
 * Stateless aside from the internal animation clock. [trigger] carries a unique id so
 * pressing the same digit twice restarts the animation.
 *
 * @param trigger the key + unique id to mirror, or null to show the idle row.
 * @param modifier layout modifier.
 * @param dotCount number of dots in the strip.
 * @param activeColor lit-dot color; defaults to the active accent.
 * @param height strip height.
 */
@Composable
fun KeyStrokeMirror(
    trigger: StrokeTrigger?,
    modifier: Modifier = Modifier,
    dotCount: Int = 16,
    activeColor: Color = LocalAccentColor.current,
    height: Dp = 18.dp,
) {
    val intensity = LocalGlyphIntensity.current.coerceIn(0f, 1f)
    val idleColor = LocalContentColor.current.copy(alpha = 0.12f)
    val progress = remember { Animatable(1f) }

    val spec = remember(trigger?.digit) {
        trigger?.let { KeyStrokeSpec.forDigit(it.digit) }
    }

    // Restart the 0f→1f sweep whenever a new (digit,id) arrives.
    LaunchedEffect(trigger?.id) {
        if (trigger == null || spec == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = spec.periodMillis, easing = LinearEasing),
        )
    }

    val p by progress

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val n = dotCount.coerceAtLeast(3)
        val pitch = size.width / n
        val radius = (pitch * 0.22f).coerceAtMost(size.height / 2f)
        val cy = size.height / 2f

        for (i in 0 until n) {
            val cx = pitch * i + pitch / 2f
            val lit = if (spec == null) 0f else dotIntensity(spec, i, n, p)
            val alpha = (lit * intensity).coerceIn(0f, 1f)
            val color = if (alpha <= 0.01f) idleColor else activeColor.copy(alpha = alpha)
            drawCircle(color = color, radius = radius, center = Offset(cx, cy))
        }
    }
}

/**
 * Brightness (0f..1f) of dot [index] of [count] at sweep [progress] for [spec]. Pure —
 * factored out so the shape→light mapping can be unit-tested without Compose.
 */
internal fun dotIntensity(spec: KeyStrokeSpec, index: Int, count: Int, progress: Float): Float {
    val pos = if (count <= 1) 0f else index.toFloat() / (count - 1) // 0f..1f across the strip
    val p = progress.coerceIn(0f, 1f)
    return when (spec.shape) {
        StrokeShape.TICK -> {
            // A brief flash on the right third, sharp on/off.
            if (pos > 0.66f) bell(p, center = 0.5f, width = 0.5f) else 0f
        }
        StrokeShape.SWEEP -> {
            val headPos = when (spec.direction) {
                StrokeDirection.RIGHT_TO_LEFT -> 1f - p
                else -> p
            }
            (1f - (pos - headPos).absoluteValue * 4f).coerceIn(0f, 1f)
        }
        StrokeShape.DOUBLE_PULSE -> beats(p, beats = 2) * centerWeight(pos)
        StrokeShape.BLOOM -> {
            // Lights from the center outwards as p grows.
            val fromCenter = abs(pos - 0.5f) * 2f // 0 at center, 1 at edges
            if (fromCenter <= p) (1f - fromCenter) else 0f
        }
        StrokeShape.ARC -> {
            // A lit band that travels along the strip with a soft falloff.
            (1f - (pos - p).absoluteValue * 3f).coerceIn(0f, 1f) * 0.9f
        }
        StrokeShape.FADE -> {
            // Whole strip breathes up then down.
            sin(p * Math.PI).toFloat().coerceIn(0f, 1f)
        }
        StrokeShape.FLICKER -> {
            // Alternate the two halves on each beat.
            val on = (p * spec.beats * 2).toInt() % 2 == 0
            val leftHalf = pos < 0.5f
            if (on == leftHalf) beats(p, spec.beats) else 0f
        }
        StrokeShape.SPIRAL -> {
            // Sequential chase: each dot lights as the head passes it.
            (1f - (pos - p).absoluteValue * 6f).coerceIn(0f, 1f)
        }
        StrokeShape.FLASH -> {
            // All dots, brief.
            bell(p, center = 0.5f, width = 0.6f)
        }
        StrokeShape.SPARKLE -> {
            // Pseudo-random twinkle seeded by index, modulated by p.
            val phase = ((index * 2654435761u.toInt()) ushr 24) and 0xFF
            val twinkle = sin((p * 6f + phase / 40f) * Math.PI).toFloat()
            (twinkle.absoluteValue).coerceIn(0f, 1f) * windowFade(p)
        }
        StrokeShape.HASH -> {
            // Grid-like alternation: even dots vs odd dots toggle.
            val even = index % 2 == 0
            val on = (p * 4).toInt() % 2 == 0
            if (even == on) windowFade(p) else 0f
        }
    }
}

/** A bell/peak curve centered at [center] with half-[width], evaluated at [p]. */
private fun bell(p: Float, center: Float, width: Float): Float =
    (1f - (p - center).absoluteValue / width).coerceIn(0f, 1f)

/** Discrete beat envelope: bright at the start of each of [beats] equal windows. */
private fun beats(p: Float, beats: Int): Float {
    if (beats <= 0) return 0f
    val window = 1f / beats
    val local = (p % window) / window // 0f..1f within the current beat
    return (1f - local).coerceIn(0f, 1f)
}

/** Emphasis toward the center of the strip (used by center-anchored shapes). */
private fun centerWeight(pos: Float): Float = (1f - abs(pos - 0.5f) * 2f).coerceIn(0f, 1f)

/** A soft global fade-in/out across the whole sweep so strokes don't pop. */
private fun windowFade(p: Float): Float = sin(p * Math.PI).toFloat().coerceIn(0f, 1f)
