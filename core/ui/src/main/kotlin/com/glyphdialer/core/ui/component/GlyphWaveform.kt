// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphSprings
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.ThemeMode
import kotlin.math.roundToInt

/**
 * A dot-matrix audio visualizer (BUILD_SPEC §15.5 / §18 — in-call live waveform).
 *
 * Renders a row of columns, each a vertical stack of dots lit from the center
 * outward in proportion to that column's amplitude. Driven by [amplitudes], a list
 * of `0f..1f` levels (newest-last); the component samples it to [columns] bars and
 * animates each bar toward its target with [GlyphSprings] so the matrix "breathes"
 * smoothly as audio levels change.
 *
 * Stateless: the caller owns the amplitude stream (e.g. from a recorder's
 * `getMaxAmplitude()` or a transcription voice level, mirrored to the physical Glyph
 * via `GlyphController.renderWaveform`). When [amplitudes] is empty the matrix shows
 * a single idle center row — honest "no signal" rather than fake motion.
 *
 * @param amplitudes per-frame levels in `0f..1f`; only the most recent [columns] are shown.
 * @param modifier layout modifier.
 * @param columns number of bars across.
 * @param dotsPerColumn vertical dot resolution per bar (odd numbers center cleanly).
 * @param color lit-dot color; defaults to the active accent.
 * @param dimColor unlit-dot color (the matrix "off" grid).
 * @param dotSize diameter of one dot.
 */
@Composable
fun GlyphWaveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    columns: Int = 24,
    dotsPerColumn: Int = 9,
    color: Color = LocalAccentColor.current,
    dimColor: Color = color.copy(alpha = 0.12f),
    dotSize: Dp = 3.dp,
) {
    val safeColumns = columns.coerceAtLeast(1)
    val safeRows = dotsPerColumn.coerceAtLeast(1)

    // Sample the tail of the stream into exactly [safeColumns] levels.
    val sampled = remember(amplitudes, safeColumns) {
        sampleAmplitudes(amplitudes, safeColumns)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.tickerHeight),
    ) {
        // Animate each column toward its target so transitions feel physical.
        val animated = FloatArray(safeColumns)
        for (i in 0 until safeColumns) {
            // key() gives each column a stable animation slot across recompositions.
            animated[i] = key(i) {
                val a by animateFloatAsState(
                    targetValue = sampled[i].coerceIn(0f, 1f),
                    animationSpec = GlyphSprings.snappy(),
                    label = "waveColumn$i",
                )
                a
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(Dimens.tickerHeight)) {
            drawDotMatrixWaveform(
                levels = animated,
                rows = safeRows,
                litColor = color,
                dimColor = dimColor,
                dotRadiusPx = dotSize.toPx() / 2f,
                canvasSize = size,
            )
        }
    }
}

/** Resample [source] into [target] evenly-spaced columns (tail-weighted). */
internal fun sampleAmplitudes(source: List<Float>, target: Int): FloatArray {
    val out = FloatArray(target)
    if (source.isEmpty()) return out // all-zero ⇒ idle center row only
    // Take the most recent [target] samples, padding the head with the oldest value.
    val tail = if (source.size >= target) source.subList(source.size - target, source.size)
    else source
    val offset = target - tail.size
    for (i in tail.indices) {
        out[offset + i] = tail[i].coerceIn(0f, 1f)
    }
    // Left-pad with the first available value so the bar doesn't pop in from zero.
    for (i in 0 until offset) out[i] = tail.firstOrNull() ?: 0f
    return out
}

/** Pure drawing of the centered dot-matrix bars. Split out for testability/clarity. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDotMatrixWaveform(
    levels: FloatArray,
    rows: Int,
    litColor: Color,
    dimColor: Color,
    dotRadiusPx: Float,
    canvasSize: Size,
) {
    val cols = levels.size
    if (cols == 0 || rows == 0) return
    val colPitch = canvasSize.width / cols
    val rowPitch = canvasSize.height / rows
    val centerRow = (rows - 1) / 2f

    for (c in 0 until cols) {
        val cx = colPitch * c + colPitch / 2f
        // How many rows out from center are lit (at least the center dot).
        val litReach = (levels[c] * centerRow).roundToInt()
        for (r in 0 until rows) {
            val cy = rowPitch * r + rowPitch / 2f
            val distanceFromCenter = kotlin.math.abs(r - centerRow)
            val isLit = distanceFromCenter <= litReach
            drawCircle(
                color = if (isLit) litColor else dimColor,
                radius = dotRadiusPx,
                center = Offset(cx, cy),
            )
        }
    }
}

@Preview(name = "GlyphWaveform · active", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewWaveformActive() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        val demo = List(24) { i ->
            (kotlin.math.sin(i / 3f) * 0.5f + 0.5f).toFloat()
        }
        Box(Modifier.padding(16.dp)) {
            GlyphWaveform(amplitudes = demo)
        }
    }
}

@Preview(name = "GlyphWaveform · idle", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewWaveformIdle() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            GlyphWaveform(amplitudes = emptyList())
        }
    }
}
