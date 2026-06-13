// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.ui.component.DotMatrixAvatar
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EngineeredCard
import com.glyphdialer.feature.voicemail.PlaybackState
import com.glyphdialer.feature.voicemail.VoicemailItem
import java.util.concurrent.TimeUnit

/**
 * A single visual-voicemail message rendered as an engineered card with an inline
 * mini-player (BUILD_SPEC §8 — play/pause/scrub, transcription reuse, call back,
 * delete; §18 "now-playing mini-player with dot-matrix waveform scrubbing").
 *
 * Stateless: all state is hoisted. The card expands its transcription panel and
 * scrubber only when this row is the one currently loaded in the player (matched by
 * [PlaybackState.voicemailId]).
 *
 * HONESTY (§9): the transcription line is shown only when real text exists; a
 * "MY SIDE ONLY" tag is rendered when the transcript is local-side-only (§2.4). The
 * scrubber waveform is decorative (see [com.glyphdialer.feature.voicemail.player]).
 */
@Composable
fun VoicemailRow(
    item: VoicemailItem,
    playback: PlaybackState,
    isTranscribing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onTranscribe: () -> Unit,
    onCallBack: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = item.voicemail
    val isCurrent = playback.voicemailId == item.id
    val isPlaying = isCurrent && playback.isPlaying
    val isBuffering = isCurrent && playback.isBuffering
    val accent = LocalAccentColor.current

    val title = vm.displayName?.takeIf { it.isNotBlank() } ?: vm.number.formatted
    val durationLabel = formatDuration(
        if (isCurrent && playback.durationMillis > 0L) playback.durationMillis / 1000L
        else vm.durationSeconds,
    )

    EngineeredCard(
        modifier = modifier.fillMaxWidth(),
        indexLabel = if (vm.isRead) null else "NEW",
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.spaceMd),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            // --- Header row: avatar, name/number, time, primary play/pause ---------
            Row(verticalAlignment = Alignment.CenterVertically) {
                DotMatrixAvatar(seed = vm.number.dialValue, size = Dimens.avatarSm)
                Spacer(Modifier.width(Dimens.spaceMd))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (vm.isRead) FontWeight.Normal else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${relativeTime(vm.timestampMillis)} · $durationLabel",
                        style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                PlayPauseButton(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onPlay = onPlay,
                    onPause = onPause,
                    accentColor = accent,
                )
            }

            // --- Scrubber (only for the loaded message) ----------------------------
            if (isCurrent) {
                VoicemailScrubber(
                    playback = playback,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // --- Transcription (reuse of §13) --------------------------------------
            TranscriptionPanel(
                item = item,
                isTranscribing = isTranscribing,
                onTranscribe = onTranscribe,
            )

            DottedDivider(modifier = Modifier.fillMaxWidth())

            // --- Quick actions: call back / read toggle / delete -------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCallBack) {
                    Icon(Icons.Filled.Phone, contentDescription = "Call back", tint = accent)
                }
                IconButton(onClick = onToggleRead) {
                    Icon(
                        imageVector = if (vm.isRead) Icons.Filled.MarkEmailUnread else Icons.Filled.MarkEmailRead,
                        contentDescription = if (vm.isRead) "Mark unread" else "Mark read",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    IconButton(
        onClick = { if (isPlaying) onPause() else onPlay() },
        modifier = Modifier.size(Dimens.iconButton),
    ) {
        when {
            isBuffering -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = accentColor,
            )
            isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = accentColor)
            else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = accentColor)
        }
    }
}

@Composable
private fun TranscriptionPanel(
    item: VoicemailItem,
    isTranscribing: Boolean,
    onTranscribe: () -> Unit,
) {
    val caption = item.displayTranscription
    when {
        isTranscribing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(Dimens.spaceSm))
            Text(
                "TRANSCRIBING…",
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        caption != null -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs)) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.transcriptionIsLocalSideOnly) {
                // HONESTY (§2.4): be explicit about partial coverage.
                Text(
                    text = "MY SIDE ONLY · §2.4",
                    style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        else -> Row(
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { onTranscribe() }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Notes,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LocalContentColor.current.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(Dimens.spaceSm))
            Text(
                "TRANSCRIBE",
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Time formatting (pure, unit-testable) -----------------------------------------

/** Format a clip length in seconds as `M:SS`. */
internal fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val m = s / 60L
    val rem = s % 60L
    return "%d:%02d".format(m, rem)
}

/** A coarse relative-time label ("now", "5m", "3h", "2d", else date-ish). */
internal fun relativeTime(timestampMillis: Long, now: Long = System.currentTimeMillis()): String {
    val delta = (now - timestampMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 7L -> "${days}d"
        else -> "${days / 7L}w"
    }
}
