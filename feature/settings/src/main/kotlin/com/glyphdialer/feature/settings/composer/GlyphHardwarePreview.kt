// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.domain.model.CustomGlyphZone
import com.glyphdialer.core.domain.model.GlyphHardwareKind
import com.glyphdialer.core.domain.model.GlyphHardwareProfile
import kotlin.math.min

@Composable
internal fun GlyphHardwarePreview(
    profile: GlyphHardwareProfile,
    zones: Set<CustomGlyphZone>,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val activeZones = profile.adapt(zones)
    val active = MaterialTheme.colorScheme.onSurface.copy(alpha = intensity.coerceIn(0.25f, 1f))
    val inactive = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = (0.15f + intensity * 0.24f).coerceIn(0.15f, 0.42f))
    val phoneFill = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val phoneStroke = MaterialTheme.colorScheme.outlineVariant
    val cameraFill = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(18.dp))
            .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (profile.kind) {
                GlyphHardwareKind.MATRIX -> drawMatrixPhone(
                    activeZones = activeZones,
                    active = active,
                    inactive = inactive,
                    glow = glow,
                    phoneFill = phoneFill,
                    phoneStroke = phoneStroke,
                    cameraFill = cameraFill,
                    accent = accent,
                )

                GlyphHardwareKind.LIGHT_STRIP,
                GlyphHardwareKind.NONE -> drawLightStripPhone(
                    supportedZones = profile.supportedZones,
                    activeZones = activeZones,
                    active = active,
                    inactive = inactive,
                    glow = glow,
                    phoneFill = phoneFill,
                    phoneStroke = phoneStroke,
                    cameraFill = cameraFill,
                    accent = accent,
                )
            }
        }
    }
}

private fun DrawScope.drawLightStripPhone(
    supportedZones: Set<CustomGlyphZone>,
    activeZones: Set<CustomGlyphZone>,
    active: Color,
    inactive: Color,
    glow: Color,
    phoneFill: Color,
    phoneStroke: Color,
    cameraFill: Color,
    accent: Color,
) {
    val rect = phoneRect()
    val corner = CornerRadius(rect.width * 0.075f, rect.width * 0.075f)
    drawRoundRect(
        color = phoneFill,
        topLeft = Offset(rect.left, rect.top),
        size = rect.size,
        cornerRadius = corner,
    )
    drawRoundRect(
        color = phoneStroke,
        topLeft = Offset(rect.left, rect.top),
        size = rect.size,
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )

    val lensR = rect.width * 0.065f
    val lensA = Offset(rect.left + rect.width * 0.22f, rect.top + rect.height * 0.12f)
    val lensB = Offset(rect.left + rect.width * 0.22f, rect.top + rect.height * 0.22f)
    drawCircle(cameraFill, lensR * 1.35f, lensA)
    drawCircle(Color.Black.copy(alpha = 0.86f), lensR, lensA)
    drawCircle(cameraFill, lensR * 1.2f, lensB)
    drawCircle(Color.Black.copy(alpha = 0.86f), lensR * 0.86f, lensB)
    drawCircle(accent, lensR * 0.36f, Offset(rect.right - rect.width * 0.15f, rect.top + rect.height * 0.27f))

    fun drawIfSupported(zone: CustomGlyphZone, draw: (Boolean) -> Unit) {
        if (zone in supportedZones) draw(zone in activeZones)
    }

    drawIfSupported(CustomGlyphZone.TOP_LEFT) { isActive ->
        ledLine(
            from = rect.point(0.13f, 0.14f),
            to = rect.point(0.13f, 0.30f),
            isActive = isActive,
            active = active,
            inactive = inactive,
            glow = glow,
        )
    }
    drawIfSupported(CustomGlyphZone.TOP_RIGHT) { isActive ->
        ledLine(
            from = rect.point(0.70f, 0.16f),
            to = rect.point(0.86f, 0.06f),
            isActive = isActive,
            active = active,
            inactive = inactive,
            glow = glow,
        )
    }
    drawIfSupported(CustomGlyphZone.CAMERA_RING) { isActive ->
        val stroke = ledStroke(isActive)
        val color = if (isActive) active else inactive
        if (isActive) {
            drawArc(
                color = glow,
                startAngle = 114f,
                sweepAngle = 294f,
                useCenter = false,
                topLeft = Offset(lensA.x - lensR * 2.0f, lensA.y - lensR * 2.0f),
                size = Size(lensR * 4.0f, lensR * 4.0f),
                style = Stroke(width = stroke.width * 2.1f, cap = StrokeCap.Round),
            )
        }
        drawArc(
            color = color,
            startAngle = 114f,
            sweepAngle = 294f,
            useCenter = false,
            topLeft = Offset(lensA.x - lensR * 2.0f, lensA.y - lensR * 2.0f),
            size = Size(lensR * 4.0f, lensR * 4.0f),
            style = stroke,
        )
    }
    drawIfSupported(CustomGlyphZone.CENTER) { isActive ->
        val ring = Rect(
            left = rect.left + rect.width * 0.16f,
            top = rect.top + rect.height * 0.29f,
            right = rect.right - rect.width * 0.16f,
            bottom = rect.top + rect.height * 0.63f,
        )
        val stroke = ledStroke(isActive)
        if (isActive) {
            drawArc(
                color = glow,
                startAngle = 198f,
                sweepAngle = 282f,
                useCenter = false,
                topLeft = ring.topLeft,
                size = ring.size,
                style = Stroke(width = stroke.width * 2.0f, cap = StrokeCap.Round),
            )
        }
        drawArc(
            color = if (isActive) active else inactive,
            startAngle = 198f,
            sweepAngle = 282f,
            useCenter = false,
            topLeft = ring.topLeft,
            size = ring.size,
            style = stroke,
        )
    }
    drawIfSupported(CustomGlyphZone.BOTTOM_LEFT) { isActive ->
        ledLine(
            from = rect.point(0.32f, 0.78f),
            to = rect.point(0.32f, 0.88f),
            isActive = isActive,
            active = active,
            inactive = inactive,
            glow = glow,
        )
    }
    drawIfSupported(CustomGlyphZone.BOTTOM_CENTER) { isActive ->
        ledLine(
            from = rect.point(0.50f, 0.76f),
            to = rect.point(0.50f, 0.91f),
            isActive = isActive,
            active = active,
            inactive = inactive,
            glow = glow,
        )
    }
    drawIfSupported(CustomGlyphZone.BOTTOM_RIGHT) { isActive ->
        ledLine(
            from = rect.point(0.71f, 0.77f),
            to = rect.point(0.71f, 0.88f),
            isActive = isActive,
            active = active,
            inactive = inactive,
            glow = glow,
        )
    }
}

private fun DrawScope.drawMatrixPhone(
    activeZones: Set<CustomGlyphZone>,
    active: Color,
    inactive: Color,
    glow: Color,
    phoneFill: Color,
    phoneStroke: Color,
    cameraFill: Color,
    accent: Color,
) {
    val rect = phoneRect()
    drawRoundRect(
        color = phoneFill,
        topLeft = Offset(rect.left, rect.top),
        size = rect.size,
        cornerRadius = CornerRadius(rect.width * 0.075f, rect.width * 0.075f),
    )
    drawRoundRect(
        color = phoneStroke,
        topLeft = Offset(rect.left, rect.top),
        size = rect.size,
        cornerRadius = CornerRadius(rect.width * 0.075f, rect.width * 0.075f),
        style = Stroke(width = 1.dp.toPx()),
    )

    val pill = Rect(
        left = rect.left + rect.width * 0.30f,
        top = rect.top + rect.height * 0.10f,
        right = rect.right - rect.width * 0.16f,
        bottom = rect.top + rect.height * 0.22f,
    )
    drawRoundRect(
        color = cameraFill,
        topLeft = pill.topLeft,
        size = pill.size,
        cornerRadius = CornerRadius(pill.height / 2f, pill.height / 2f),
    )
    drawCircle(Color.Black.copy(alpha = 0.82f), pill.height * 0.27f, Offset(pill.left + pill.width * 0.30f, pill.center.y))
    drawCircle(Color.Black.copy(alpha = 0.82f), pill.height * 0.27f, Offset(pill.left + pill.width * 0.68f, pill.center.y))
    drawCircle(accent, pill.height * 0.10f, Offset(rect.right - rect.width * 0.13f, rect.top + rect.height * 0.32f))

    val matrix = Rect(
        left = rect.left + rect.width * 0.18f,
        top = rect.top + rect.height * 0.30f,
        right = rect.right - rect.width * 0.18f,
        bottom = rect.top + rect.height * 0.68f,
    )
    val count = 25
    val cell = min(matrix.width, matrix.height) / count
    val radius = cell * 0.24f
    for (row in 0 until count) {
        for (col in 0 until count) {
            val zone = matrixZone(row, col, count)
            val lit = zone in activeZones
            val center = Offset(
                x = matrix.left + col * cell + cell / 2f,
                y = matrix.top + row * cell + cell / 2f,
            )
            if (lit) {
                drawCircle(glow, radius * 2.5f, center)
            }
            drawCircle(if (lit) active else inactive, radius, center)
        }
    }
}

private fun DrawScope.phoneRect(): Rect {
    val width = min(size.width * 0.72f, size.height * 0.54f)
    val height = width * 1.72f
    val left = (size.width - width) / 2f
    val top = (size.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun Rect.point(x: Float, y: Float): Offset =
    Offset(left + width * x, top + height * y)

private fun DrawScope.ledLine(
    from: Offset,
    to: Offset,
    isActive: Boolean,
    active: Color,
    inactive: Color,
    glow: Color,
) {
    val stroke = ledStroke(isActive)
    if (isActive) {
        drawLine(
            color = glow,
            start = from,
            end = to,
            strokeWidth = stroke.width * 2.35f,
            cap = StrokeCap.Round,
        )
    }
    drawLine(
        color = if (isActive) active else inactive,
        start = from,
        end = to,
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.ledStroke(isActive: Boolean): Stroke =
    Stroke(width = if (isActive) 4.5.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round)

private fun matrixZone(row: Int, col: Int, count: Int): CustomGlyphZone {
    val third = count / 3
    return when {
        row < third && col < third -> CustomGlyphZone.TOP_LEFT
        row < third && col >= third * 2 -> CustomGlyphZone.TOP_RIGHT
        row in third until third * 2 && col in third until third * 2 -> CustomGlyphZone.CENTER
        row >= third * 2 && col < third -> CustomGlyphZone.BOTTOM_LEFT
        row >= third * 2 && col in third until third * 2 -> CustomGlyphZone.BOTTOM_CENTER
        row >= third * 2 && col >= third * 2 -> CustomGlyphZone.BOTTOM_RIGHT
        else -> CustomGlyphZone.CAMERA_RING
    }
}
