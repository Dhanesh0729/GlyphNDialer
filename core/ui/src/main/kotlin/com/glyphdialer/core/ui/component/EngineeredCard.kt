// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphShapes
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * A flat, "engineered" card (BUILD_SPEC §15.3 — "flat cards with hairline borders,
 * not heavy shadows ... exposed engineered framing: thin rules, corner ticks, index
 * labels").
 *
 * Renders [content] inside a hairline-bordered surface with NO elevation/shadow.
 * Optional decorations reinforce the technical-drawing look:
 *  - [cornerTicks]: short marks drawn just inside each corner.
 *  - [indexLabel]: a small monospaced tag (e.g. `01`, `A·2`) pinned top-start, like a
 *    schematic reference designator.
 *
 * Stateless and themed: border uses `outlineVariant`, background uses
 * `surfaceVariant`, label uses the monospaced numeral style.
 *
 * @param modifier layout modifier.
 * @param indexLabel optional schematic-style reference tag, or null.
 * @param cornerTicks whether to draw the engineered corner ticks.
 * @param borderColor hairline border color.
 * @param backgroundColor card fill.
 * @param contentPadding inner padding around [content].
 * @param content the card body.
 */
@Composable
fun EngineeredCard(
    modifier: Modifier = Modifier,
    indexLabel: String? = null,
    cornerTicks: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Dimens.spaceLg),
    content: @Composable () -> Unit,
) {
    val shape: RoundedCornerShape = GlyphShapes.Card

    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(Dimens.hairline, borderColor, shape)
            .then(if (cornerTicks) Modifier.cornerTicks(borderColor) else Modifier),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }

        if (indexLabel != null) {
            Text(
                text = indexLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Dimens.spaceMd, top = Dimens.spaceXs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Draws four short "engineered" corner ticks just inside the card bounds, after the
 * content (so they sit atop the fill). Uses [Dimens.cornerTick] for length.
 */
private fun Modifier.cornerTicks(color: Color): Modifier = drawWithContent {
    drawContent()
    val tick = Dimens.cornerTick.toPx()
    val stroke = Stroke(width = Dimens.hairline.toPx(), cap = StrokeCap.Square)
    val inset = stroke.width
    val w = size.width
    val h = size.height

    fun line(from: Offset, to: Offset) =
        drawLine(color = color, start = from, end = to, strokeWidth = stroke.width, cap = StrokeCap.Square)

    // Top-left
    line(Offset(inset, inset), Offset(inset + tick, inset))
    line(Offset(inset, inset), Offset(inset, inset + tick))
    // Top-right
    line(Offset(w - inset, inset), Offset(w - inset - tick, inset))
    line(Offset(w - inset, inset), Offset(w - inset, inset + tick))
    // Bottom-left
    line(Offset(inset, h - inset), Offset(inset + tick, h - inset))
    line(Offset(inset, h - inset), Offset(inset, h - inset - tick))
    // Bottom-right
    line(Offset(w - inset, h - inset), Offset(w - inset - tick, h - inset))
    line(Offset(w - inset, h - inset), Offset(w - inset, h - inset - tick))
}

@Preview(name = "EngineeredCard", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewEngineeredCard() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            EngineeredCard(indexLabel = "01", modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Engineered framing",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
