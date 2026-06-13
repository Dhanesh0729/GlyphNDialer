// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.ui.component.GlyphWaveform
import com.glyphdialer.feature.voicemail.PlaybackState

/**
 * The dot-matrix waveform scrubber for the voicemail mini-player (BUILD_SPEC §18 —
 * "now-playing recording mini-player with dot-matrix waveform scrubbing").
 *
 * Reuses [GlyphWaveform] from :core:ui for the visual, overlaying drag/tap gesture
 * handling that translates an x-position into a 0f..1f seek fraction. While the user
 * is dragging, a local fraction overrides the incoming [PlaybackState.progress] so
 * the thumb tracks the finger without fighting position updates; on release the seek
 * is committed via [onSeek].
 *
 * Stateless w.r.t. playback; the only local state is the in-progress drag fraction.
 */
@Composable
fun VoicemailScrubber(
    playback: PlaybackState,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    val shownFraction = if (dragFraction >= 0f) dragFraction else playback.progress

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeek(f)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragFraction >= 0f) onSeek(dragFraction)
                            dragFraction = -1f
                        },
                        onDragCancel = { dragFraction = -1f },
                    )
                },
        ) {
            // Trim the amplitude envelope to the played fraction so the leading dots
            // read as "played" — purely visual, mirrors the playhead.
            val played = (shownFraction * playback.amplitudes.size).toInt().coerceIn(0, playback.amplitudes.size)
            val envelope = if (playback.amplitudes.isEmpty()) {
                emptyList()
            } else {
                playback.amplitudes.mapIndexed { i, a -> if (i <= played) a else a * 0.2f }
            }
            GlyphWaveform(amplitudes = envelope)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatMillis(if (dragFraction >= 0f) (dragFraction * playback.durationMillis).toLong() else playback.positionMillis),
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMillis(playback.durationMillis),
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Format [millis] as `M:SS` for the scrubber readout. */
internal fun formatMillis(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
    val m = totalSeconds / 60L
    val s = totalSeconds % 60L
    return "%d:%02d".format(m, s)
}
