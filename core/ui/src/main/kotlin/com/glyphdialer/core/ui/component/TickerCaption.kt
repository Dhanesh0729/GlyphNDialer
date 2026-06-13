// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * A scrolling dot-matrix caption "ticker tape" (BUILD_SPEC §18 — "transcription
 * ticker tape"; §13 live captions).
 *
 * Renders [text] as a single row of [DotMatrixFont] glyphs that scrolls right→left
 * when it overflows the available width, like a station departure board. Used for
 * live transcription captions and the recording/transcription status strip.
 *
 * Honesty (CONVENTIONS.md §9): this is a pure presenter — it scrolls whatever
 * caption text the transcription layer produces. When [text] is blank it shows the
 * idle [placeholder] (e.g. "LISTENING…" or "TRANSCRIPTION UNAVAILABLE") so the strip
 * never implies captions exist when they don't. If [text] fits, it is centered and
 * static (no fake scrolling).
 *
 * @param text the caption to display (newest transcription).
 * @param modifier layout modifier.
 * @param color lit-dot color.
 * @param placeholder shown when [text] is blank.
 * @param dotSize diameter of one dot.
 * @param dotSpacing pitch between dot centers.
 * @param speedDpPerSecond scroll speed when overflowing.
 */
@Composable
fun TickerCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    placeholder: String = "",
    dotSize: Dp = 2.5.dp,
    dotSpacing: Dp = 3.dp,
    speedDpPerSecond: Float = 48f,
) {
    val shown = text.ifBlank { placeholder }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.tickerHeight),
    ) {
        if (shown.isEmpty()) return@Box

        val colsPerChar = DotMatrixFont.COLS + DotMatrixFont.CHAR_GAP_COLS
        val totalCols = shown.length * colsPerChar - DotMatrixFont.CHAR_GAP_COLS

        // Approximate the on-screen glyph width in dp (cols × dot pitch) to pick a
        // scroll duration that keeps the perceived speed ~constant across lengths.
        // The exact pixel travel (which also includes the viewport width) is applied
        // at draw time; this just sets the cadence.
        val glyphWidthDp = totalCols * dotSpacing.value
        val durationMillis = (glyphWidthDp / speedDpPerSecond * 1000f)
            .toInt()
            .coerceIn(2_000, 30_000)

        val transition = rememberInfiniteTransition(label = "ticker")
        // 0f..1f phase; mapped to a pixel offset at draw time once we know widths.
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "tickerPhase",
        )

        Canvas(modifier = Modifier.fillMaxWidth().height(Dimens.tickerHeight)) {
            val pitch = dotSpacing.toPx()
            val radius = dotSize.toPx() / 2f
            val glyphPixelWidth = totalCols * pitch
            val verticalPad = (size.height - DotMatrixFont.ROWS * pitch) / 2f

            val overflow = glyphPixelWidth > size.width
            val startX: Float = if (overflow) {
                // Scroll from just off the right edge to fully off the left edge.
                val travel = size.width + glyphPixelWidth
                size.width - phase * travel
            } else {
                // Center the text statically.
                (size.width - glyphPixelWidth) / 2f
            }

            shown.forEachIndexed { charIndex, c ->
                val charColOffset = charIndex * colsPerChar
                for (row in 0 until DotMatrixFont.ROWS) {
                    for (col in 0 until DotMatrixFont.COLS) {
                        if (!DotMatrixFont.isDotOn(c, row, col)) continue
                        val cx = startX + (charColOffset + col) * pitch + radius
                        // Skip dots outside the visible band (cheap cull).
                        if (cx < -radius || cx > size.width + radius) continue
                        val cy = verticalPad + row * pitch + radius
                        drawCircle(color = color, radius = radius, center = Offset(cx, cy))
                    }
                }
            }
        }
    }
}

@Preview(name = "TickerCaption · scroll", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewTickerScroll() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            TickerCaption(text = "LIVE TRANSCRIPTION RUNNING ON DEVICE")
        }
    }
}

@Preview(name = "TickerCaption · idle", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewTickerIdle() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            TickerCaption(text = "", placeholder = "LISTENING")
        }
    }
}
