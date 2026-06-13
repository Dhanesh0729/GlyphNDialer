// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.CallCapability
import com.glyphdialer.core.domain.model.CallDirection
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixAvatar
import com.glyphdialer.core.ui.component.GlyphWaveform
import com.glyphdialer.core.ui.component.MonoTimer
import com.glyphdialer.feature.incall.InCallEvent
import com.glyphdialer.feature.incall.InCallUiState

/**
 * Stateless body of the in-call screen — "AOD-minimal: mostly black, monospaced
 * timer, single breathing accent, engineered corner ticks, live dot-matrix waveform"
 * (BUILD_SPEC §18 ★). Reads only [state] and raises [onEvent] (CONVENTIONS.md §5).
 */
@Composable
fun InCallContent(
    state: InCallUiState,
    onEvent: (InCallEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // AOD-minimal: true-black surface (§15.1).
            .background(MaterialTheme.colorScheme.background)
            .engineeredCornerTicks(),
    ) {
        if (!state.hasCall) {
            // Honest empty frame while the last call tears down (host will dismiss).
            Text(
                text = "NO ACTIVE CALL",
                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallHeader(state)

            Spacer(Modifier.height(Dimens.spaceXl))

            // Live dot-matrix waveform of call audio (honest "no signal" when empty).
            GlyphWaveform(amplitudes = state.amplitudes)

            // Recording / caption status + the scrolling caption ticker.
            RecordingCaptionStrip(state = state)

            Spacer(Modifier.weight(1f))

            // Conference participant list (per-participant hold/disconnect — §10).
            AnimatedVisibility(visible = state.isConference) {
                ConferenceList(
                    participants = state.conferenceParticipants,
                    onHold = { id, hold -> onEvent(InCallEvent.HoldParticipant(id, hold)) },
                    onDisconnect = { onEvent(InCallEvent.DisconnectParticipant(it)) },
                    onSplit = { onEvent(InCallEvent.SplitParticipant(it)) },
                )
            }

            Spacer(Modifier.height(Dimens.spaceLg))

            // Either the answer/reject row (ringing) or the full control surface.
            if (state.isIncomingRinging) {
                IncomingCallActions(
                    onAnswer = { onEvent(InCallEvent.Answer) },
                    onReject = { onEvent(InCallEvent.Reject()) },
                )
            } else {
                InCallControlSurface(state = state, onEvent = onEvent)
            }
        }
    }
}

/** Name/number + monospaced [MonoTimer] + a single breathing accent dot (§18). */
@Composable
private fun CallHeader(state: InCallUiState) {
    val call = state.primaryCall ?: return

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(Dimens.spaceXl))

        // Caller photo (Coil) with a deterministic dot-matrix fallback avatar.
        val seed = call.number.dialValue.ifBlank { call.displayName.orEmpty() }
        if (call.photoUri != null) {
            AsyncImage(
                model = call.photoUri,
                contentDescription = null,
                modifier = Modifier
                    .height(Dimens.avatarLg)
                    .clip(CircleShape),
            )
        } else {
            DotMatrixAvatar(seed = seed, size = Dimens.avatarLg)
        }

        Spacer(Modifier.height(Dimens.spaceLg))

        Text(
            text = call.displayName ?: call.number.formatted,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (call.displayName != null) {
            Text(
                text = call.number.formatted,
                style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        call.spamLabel?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(Dimens.spaceMd))

        // Status line: breathing accent dot + state label + monospaced timer.
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            BreathingDot(active = call.state == CallState.ACTIVE)
            Text(
                text = call.statusLabel(state),
                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Dimens.spaceSm))

        // The monospaced AOD timer; only meaningful once connected.
        if (call.connectTimeMillis != null) {
            MonoTimer(
                elapsedMillis = state.primaryDurationMillis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            MonoTimer(elapsedMillis = 0L, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A single accent dot that "breathes" (scale + alpha) while [active] (§18). */
@Composable
private fun BreathingDot(active: Boolean) {
    val accent = LocalAccentColor.current
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = if (active) 0.6f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    Box(
        modifier = Modifier
            .scale(if (active) scale else 1f)
            .size(8.dp)
            .clip(CircleShape)
            .background(accent),
    )
}

/** Four engineered corner ticks framing the AOD surface (§15.3 / §18). */
private fun Modifier.engineeredCornerTicks(): Modifier = drawBehind {
    val tick = 16.dp.toPx()
    val inset = 12.dp.toPx()
    val color = androidx.compose.ui.graphics.Color(0xFF2A2A2A)
    val stroke = 1.dp.toPx()
    fun line(a: Offset, b: Offset) =
        drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Square)
    val w = size.width
    val h = size.height
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

/** Human-readable status label for the call, honest about hold/conference/dialing. */
private fun CallModel.statusLabel(state: InCallUiState): String = when {
    isConference -> "CONFERENCE · ${state.conferenceParticipants.size}"
    this.state == CallState.RINGING && direction == CallDirection.INCOMING -> "INCOMING"
    this.state == CallState.DIALING -> "DIALING"
    this.state == CallState.CONNECTING -> "CONNECTING"
    this.state == CallState.HOLDING || isOnHold -> "ON HOLD"
    this.state == CallState.ACTIVE && isVideo -> "VIDEO"
    this.state == CallState.ACTIVE -> "ACTIVE"
    this.state.isTerminal -> "ENDED"
    else -> this.state.name
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewCall(
    state: CallState = CallState.ACTIVE,
    conference: Boolean = false,
    voip: Boolean = false,
) = CallModel(
    id = "c1",
    number = PhoneNumber(raw = "+14155550142", formatted = "+1 415-555-0142"),
    displayName = "Ada Lovelace",
    state = state,
    direction = CallDirection.INCOMING,
    connectTimeMillis = if (state == CallState.ACTIVE) 0L else null,
    isConference = conference,
    isVoip = voip,
    capability = CallCapability(canHold = true, canAddCall = true, canUpgradeToVideo = voip),
)

@Preview(name = "InCall · active", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewActive() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        InCallContent(
            state = InCallUiState(
                primaryCall = previewCall(),
                audioState = AudioState(
                    route = AudioRoute.EARPIECE,
                    supportedRoutes = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
                ),
                recordingTier = RecordingTier.LOCAL_ONE_SIDED,
                nowMillis = 73_000L,
                isLoading = false,
            ),
            onEvent = {},
        )
    }
}

@Preview(name = "InCall · ringing", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewRinging() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        InCallContent(
            state = InCallUiState(
                primaryCall = previewCall(state = CallState.RINGING),
                isLoading = false,
            ),
            onEvent = {},
        )
    }
}
