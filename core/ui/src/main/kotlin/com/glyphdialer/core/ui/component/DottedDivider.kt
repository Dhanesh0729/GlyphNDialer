// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.domain.model.ThemeMode

/** Orientation of a [DottedDivider]. */
enum class DividerOrientation { HORIZONTAL, VERTICAL }

/**
 * A hairline dotted separator (BUILD_SPEC §15.1, §15.5 — "dotted separators").
 *
 * Draws evenly-spaced dots rather than a solid rule, matching the engineered
 * dot-matrix aesthetic. The default [color] is the theme's `outlineVariant`, which
 * the design system maps to the brand divider grey (`#2A2A2A` dark / `#D9D9D9`
 * light). Stateless and themed.
 *
 * @param modifier layout modifier (controls length via width/height).
 * @param orientation horizontal (default) or vertical run.
 * @param color dot color; defaults to the themed divider grey.
 * @param dotSize dot diameter.
 * @param dotGap gap between dot centers.
 */
@Composable
fun DottedDivider(
    modifier: Modifier = Modifier,
    orientation: DividerOrientation = DividerOrientation.HORIZONTAL,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    dotSize: Dp = Dimens.dotSize,
    dotGap: Dp = Dimens.dotGap,
) {
    val sizedModifier = when (orientation) {
        DividerOrientation.HORIZONTAL -> modifier.fillMaxWidth().height(dotSize)
        DividerOrientation.VERTICAL -> modifier.fillMaxHeight().width(dotSize)
    }

    Canvas(modifier = sizedModifier) {
        val radius = dotSize.toPx() / 2f
        val pitch = (dotSize + dotGap).toPx().coerceAtLeast(1f)
        when (orientation) {
            DividerOrientation.HORIZONTAL -> {
                val cy = size.height / 2f
                var x = radius
                while (x <= size.width - radius + pitch) {
                    drawCircle(color = color, radius = radius, center = Offset(x, cy))
                    x += pitch
                }
            }
            DividerOrientation.VERTICAL -> {
                val cx = size.width / 2f
                var y = radius
                while (y <= size.height - radius + pitch) {
                    drawCircle(color = color, radius = radius, center = Offset(cx, y))
                    y += pitch
                }
            }
        }
    }
}

@Preview(name = "DottedDivider", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDottedDivider() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            DottedDivider()
        }
    }
}
