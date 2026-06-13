// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * A monospaced elapsed-time readout (BUILD_SPEC §15.5 — "MonoTimer"; §18 in-call
 * AOD timer).
 *
 * Formats [elapsedMillis] as `MM:SS`, or `H:MM:SS` once it crosses an hour, and
 * renders it with the design system's tabular monospaced numeral style so digits
 * don't jitter as they tick. Stateless: the caller drives [elapsedMillis] (e.g. from
 * the active call's connect time). Negative values clamp to zero.
 *
 * @param elapsedMillis elapsed duration in milliseconds.
 * @param modifier layout modifier.
 * @param color text color; defaults to the current content color.
 * @param style base text style; [NumberStyle] is merged on top for monospaced figures.
 * @param alwaysShowHours force the `H:MM:SS` form even under one hour.
 */
@Composable
fun MonoTimer(
    elapsedMillis: Long,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    alwaysShowHours: Boolean = false,
) {
    val text = formatElapsed(elapsedMillis, alwaysShowHours)
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.merge(NumberStyle),
        maxLines = 1,
    )
}

/**
 * Format [millis] as `MM:SS` or `H:MM:SS`. Pure and locale-independent (timers use
 * fixed ASCII digits, not localized numerals) so it is unit-testable.
 */
internal fun formatElapsed(millis: Long, alwaysShowHours: Boolean = false): String {
    val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L || alwaysShowHours) {
        // H:MM:SS — hours are not zero-padded (call timers rarely exceed 9h).
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Preview(name = "MonoTimer", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewMonoTimer() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            MonoTimer(elapsedMillis = (1L * 3600 + 23 * 60 + 7) * 1000)
        }
    }
}
