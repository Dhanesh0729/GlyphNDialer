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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.ThemeMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A dot-matrix "connecting" indicator (BUILD_SPEC §18 — "Dot-matrix everything:
 * connecting spinner").
 *
 * Draws [dotCount] dots arranged on a ring; a lit "head" rotates around it with a
 * fading comet tail, evoking a dialer's connecting state. Stateless and looping.
 *
 * @param modifier layout modifier.
 * @param size overall diameter.
 * @param dotCount number of dots on the ring.
 * @param color lit color; defaults to the active accent.
 * @param trackColor dim color of unlit dots.
 * @param periodMillis time for one full revolution.
 * @param contentDescription accessibility label (e.g. "Connecting").
 */
@Composable
fun DotMatrixSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    dotCount: Int = 12,
    color: Color = LocalAccentColor.current,
    trackColor: Color = LocalContentColor.current.copy(alpha = 0.15f),
    periodMillis: Int = 900,
    contentDescription: String = "Connecting",
) {
    val n = dotCount.coerceAtLeast(3)
    val transition = rememberInfiniteTransition(label = "spinner")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = n.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinnerHead",
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val ringRadius = this.size.minDimension / 2f * 0.8f
            val dotRadius = this.size.minDimension / 2f * 0.12f
            val headIndex = head

            for (i in 0 until n) {
                val angle = (i.toFloat() / n) * 2f * PI.toFloat() - (PI.toFloat() / 2f)
                val dx = cx + cos(angle) * ringRadius
                val dy = cy + sin(angle) * ringRadius

                // Distance (in dots) behind the rotating head, wrapped to [0, n).
                var behind = (headIndex - i) % n
                if (behind < 0) behind += n
                // Comet tail: brightest at the head, fading over ~half the ring.
                val tail = (1f - behind / (n / 2f)).coerceIn(0f, 1f)
                val dotColor = lerpColor(trackColor, color, tail)
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(dx, dy))
            }
        }
    }
}

/** Linear color interpolation (shared local helper, mirrors GlyphKey's). */
private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}

@Preview(name = "DotMatrixSpinner", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDotMatrixSpinner() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(24.dp)) {
            DotMatrixSpinner(size = 48.dp)
        }
    }
}
