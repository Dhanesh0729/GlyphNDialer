// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * Renders [text] as a dot-matrix grid (BUILD_SPEC §15.5 — "DotMatrixText").
 *
 * Each character is drawn from the built-in [DotMatrixFont] 5×7 cell as a grid of
 * dots on a [Canvas]. This is the signature flourish for numbers and short captions.
 *
 * Honesty / graceful fallback (CONVENTIONS.md §9): a real OFL dot-matrix `.ttf` is
 * not bundled, so when [asDots] is `false` — or for scripts the built-in 5×7 table
 * cannot represent — the component degrades to a plain themed [Text] in the active
 * face rather than rendering hollow "unknown" boxes. Callers that select the
 * grotesque face but still want digits to look mechanical can keep [asDots] = true;
 * callers rendering arbitrary user text (names, CJK) should pass [asDots] = false.
 *
 * Stateless and themed: the dot color defaults to [LocalContentColor]. Numerals stay
 * monospaced via [NumberStyle] in the fallback path.
 *
 * @param text the string to render.
 * @param modifier layout modifier.
 * @param asDots when true, draw the dot grid; when false, fall back to plain [Text].
 * @param dotColor the lit-dot color; defaults to the current content color.
 * @param dotSize diameter of one dot.
 * @param dotSpacing gap between dot centers within and across cells.
 * @param fallbackStyle text style used when [asDots] is false.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    asDots: Boolean = true,
    dotColor: Color = LocalContentColor.current,
    dotSize: Dp = 3.dp,
    dotSpacing: Dp = 4.dp,
    fallbackStyle: TextStyle = MaterialTheme.typography.headlineMedium.merge(NumberStyle),
) {
    if (!asDots || text.isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            color = dotColor,
            style = fallbackStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    // Grid geometry: per char COLS dots + a gap column, ROWS dots tall.
    val charCount = text.length
    val colsPerChar = DotMatrixFont.COLS + DotMatrixFont.CHAR_GAP_COLS
    val totalCols = (charCount * colsPerChar - DotMatrixFont.CHAR_GAP_COLS).coerceAtLeast(1)
    val totalRows = DotMatrixFont.ROWS

    Canvas(
        modifier = modifier
            .padding(horizontal = dotSpacing / 2)
            // Reserve intrinsic space so the Canvas doesn't collapse to zero.
            .size(
                width = dotSpacing * totalCols,
                height = dotSpacing * totalRows,
            ),
    ) {
        val pitchPx = dotSpacing.toPx()
        val radiusPx = dotSize.toPx() / 2f

        text.forEachIndexed { charIndex, c ->
            val charColOffset = charIndex * colsPerChar
            for (row in 0 until DotMatrixFont.ROWS) {
                for (col in 0 until DotMatrixFont.COLS) {
                    if (!DotMatrixFont.isDotOn(c, row, col)) continue
                    val cx = (charColOffset + col) * pitchPx + radiusPx
                    val cy = row * pitchPx + radiusPx
                    drawCircle(
                        color = dotColor,
                        radius = radiusPx,
                        center = Offset(cx, cy),
                    )
                }
            }
        }
    }
}

@Preview(name = "DotMatrixText · digits", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDotMatrixDigits() {
    GlyphTheme(themeMode = ThemeMode.DARK, appFont = AppFont.OG_DOT_MATRIX) {
        Box(Modifier.padding(16.dp)) {
            DotMatrixText(text = "0123456789")
        }
    }
}

@Preview(name = "DotMatrixText · phone", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDotMatrixPhone() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            DotMatrixText(text = "+1 415-555", dotSize = 4.dp, dotSpacing = 5.dp)
        }
    }
}

@Preview(name = "DotMatrixText · fallback", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun PreviewDotMatrixFallback() {
    GlyphTheme(themeMode = ThemeMode.LIGHT, appFont = AppFont.NEW_GROTESQUE) {
        Box(Modifier.padding(16.dp)) {
            DotMatrixText(text = "Ada Lovelace", asDots = false)
        }
    }
}
