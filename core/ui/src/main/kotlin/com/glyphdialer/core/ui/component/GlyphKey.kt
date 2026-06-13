// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphSprings
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import kotlinx.coroutines.launch

/**
 * A single dialpad key (BUILD_SPEC §15.5 / §17.4).
 *
 * Stateless and themed: shows a large monospaced [digit] with its small [letters]
 * row beneath (e.g. `2 → ABC`). On press it springs down ([GlyphSprings.bouncy]),
 * shows a Material ripple, and fires a subtle haptic via [LocalHapticFeedback].
 *
 * Glyph choreography seam (CONVENTIONS.md §6, §17.4): the key does NOT depend on
 * `:peripheral:glyph`. Instead it surfaces [onDigitStroke], invoked on each press
 * with the key's [digit]. The dialpad wires this to
 * `GlyphController.playDigitStroke(digit)` so the per-key light stroke fires only
 * when Glyph is available — keeping this module dependency-light and honest about
 * Glyph being an optional peripheral.
 *
 * @param digit the primary character (`0`–`9`, `*`, `#`).
 * @param letters the ITU letters under the digit, or empty (e.g. `1`, `*`, `#`).
 * @param onPress invoked on a normal tap (append the digit, send DTMF, etc.).
 * @param modifier layout modifier.
 * @param onLongPress invoked on long-press (e.g. `0`→`+`, voicemail on `1`), or null.
 * @param onDigitStroke invoked on every press so the dialpad can trigger the Glyph
 *   stroke for [digit]; defaults to a no-op (Glyph absent or disabled).
 * @param enabled whether the key accepts input.
 * @param keySize the key diameter.
 */
@Composable
fun GlyphKey(
    digit: Char,
    letters: String,
    onPress: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((Char) -> Unit)? = null,
    onDigitStroke: (Char) -> Unit = {},
    enabled: Boolean = true,
    keySize: Dp = Dimens.dialKeySize,
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = GlyphSprings.bouncy(),
        label = "glyphKeyPressScale",
    )
    // Brief accent flash on the digit while pressed (§17.4 "key press" accent).
    val flash by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = GlyphSprings.snappy(),
        label = "glyphKeyFlash",
    )
    val baseColor = LocalContentColor.current
    val digitColor = lerpColor(baseColor, accent, flash)

    val description = buildString {
        append(digit)
        if (letters.isNotEmpty()) append(", ").append(letters)
    }

    Box(
        modifier = modifier
            .size(keySize)
            .scale(pressScale)
            .clip(CircleShape)
            .indication(interactionSource, ripple(bounded = true))
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .pointerInput(enabled, digit, onPress, onLongPress, onDigitStroke) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        pressed = true
                        val press = PressInteraction.Press(offset)
                        scope.launch { interactionSource.emit(press) }
                        // Tactile feedback + Glyph stroke at the moment of contact.
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDigitStroke(digit)
                        val released = tryAwaitRelease()
                        pressed = false
                        scope.launch {
                            interactionSource.emit(
                                if (released) PressInteraction.Release(press)
                                else PressInteraction.Cancel(press),
                            )
                        }
                    },
                    onTap = { onPress(digit) },
                    onLongPress = onLongPress?.let { handler ->
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            handler(digit)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = digit.toString(),
                color = digitColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium.merge(NumberStyle),
                fontWeight = FontWeight.Medium,
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    // Defensive: if the composable is removed mid-press, clear local state.
    LaunchedEffect(enabled) {
        if (!enabled) pressed = false
    }
}

/** Linear color interpolation used for the press accent flash. */
private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}

@Preview(name = "GlyphKey · grid", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewGlyphKeyGrid() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val rows = listOf(
                listOf('1' to "", '2' to "ABC", '3' to "DEF"),
                listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
                listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
                listOf('*' to "", '0' to "+", '#' to ""),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (d, l) ->
                        GlyphKey(
                            digit = d,
                            letters = l,
                            onPress = {},
                            onLongPress = if (d == '0') { _ -> } else null,
                        )
                    }
                }
            }
        }
    }
}
