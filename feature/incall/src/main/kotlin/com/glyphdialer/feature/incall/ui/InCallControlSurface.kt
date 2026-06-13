// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.feature.incall.InCallEvent
import com.glyphdialer.feature.incall.InCallUiState

/**
 * The full in-call control surface for a connected call (BUILD_SPEC §9): mute, route
 * picker, hold/resume, DTMF keypad, add-call, merge/swap (§10), record (§12), live
 * captions (§13), switch-to-video (§11, VoIP only), and end call.
 *
 * Capability gating (§9): each control's `enabled`/visibility derives from the call's
 * [com.glyphdialer.core.domain.model.CallCapability] + [InCallUiState] availability
 * flags so unsupported actions are honestly disabled, not hidden-then-faked.
 */
@Composable
fun InCallControlSurface(
    state: InCallUiState,
    onEvent: (InCallEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val call = state.primaryCall ?: return
    val cap = call.capability
    var routePickerOpen by remember { mutableStateOf(false) }
    var dtmfOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        // DTMF keypad overlay (§9 in-call DTMF).
        AnimatedVisibility(visible = dtmfOpen) {
            DtmfKeypad(
                onDigit = { onEvent(InCallEvent.Dtmf(it)) },
                onClose = { dtmfOpen = false },
                enabled = cap.supportsDtmf,
            )
        }

        // Route picker overlay (earpiece/speaker/BT/wired).
        AnimatedVisibility(visible = routePickerOpen) {
            AudioRoutePicker(
                audioState = state.audioState,
                onSelect = {
                    onEvent(InCallEvent.SelectAudioRoute(it))
                    routePickerOpen = false
                },
                onDismiss = { routePickerOpen = false },
            )
        }

        // Row 1: mute · route · keypad
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ControlButton(
                icon = if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (state.isMuted) "UNMUTE" else "MUTE",
                active = state.isMuted,
                enabled = state.controlsEnabled && cap.canMute,
                onClick = { onEvent(InCallEvent.ToggleMute) },
            )
            ControlButton(
                icon = state.audioState.route.icon(),
                label = state.audioState.route.label(),
                active = routePickerOpen,
                enabled = state.controlsEnabled,
                onClick = { routePickerOpen = !routePickerOpen },
            )
            ControlButton(
                icon = Icons.Filled.Dialpad,
                label = "KEYPAD",
                active = dtmfOpen,
                enabled = state.controlsEnabled && cap.supportsDtmf,
                onClick = { dtmfOpen = !dtmfOpen },
            )
        }

        // Row 2: hold · add-call · merge/swap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ControlButton(
                icon = if (call.isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = if (call.isOnHold) "RESUME" else "HOLD",
                active = call.isOnHold,
                enabled = state.controlsEnabled && cap.canHold,
                onClick = { onEvent(InCallEvent.ToggleHold) },
            )
            ControlButton(
                icon = Icons.Filled.PersonAdd,
                label = "ADD",
                enabled = cap.canAddCall,
                onClick = { onEvent(InCallEvent.AddCall) },
            )
            // Merge appears once a second (held) call exists; otherwise Swap when capable.
            if (state.heldCall != null && cap.canMerge) {
                ControlButton(
                    icon = Icons.Filled.Merge,
                    label = "MERGE",
                    onClick = { onEvent(InCallEvent.Merge) },
                )
            } else {
                ControlButton(
                    icon = Icons.Filled.SwapCalls,
                    label = "SWAP",
                    enabled = cap.canSwap && state.heldCall != null,
                    onClick = { onEvent(InCallEvent.Swap) },
                )
            }
        }

        // Row 3: record · captions · video (VoIP only)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ControlButton(
                icon = Icons.Filled.FiberManualRecord,
                label = recordButtonLabel(state),
                active = state.isRecording,
                // Honest: only enabled when a real tier is achievable (§2.1/§12).
                enabled = state.recordingTier != RecordingTier.UNAVAILABLE,
                onClick = { onEvent(InCallEvent.ToggleRecording) },
            )
            ControlButton(
                icon = if (state.captionsEnabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                label = "CAPTIONS",
                active = state.captionsEnabled,
                // Honest: gated on engine availability + preference (§2.4/§13).
                enabled = state.captionsAvailable,
                onClick = { onEvent(InCallEvent.ToggleCaptions) },
            )
            // Switch-to-video is visible ONLY for in-app VoIP calls (§2.3/§11).
            if (state.videoUpgradeAvailable || state.isVideo) {
                ControlButton(
                    icon = if (state.isVideo) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    label = if (state.isVideo) "STOP VIDEO" else "VIDEO",
                    active = state.isVideo,
                    enabled = state.videoUpgradeAvailable || state.isVideo,
                    onClick = { onEvent(InCallEvent.ToggleVideo) },
                )
            } else {
                // Keep the grid balanced with an empty slot.
                Spacer(Modifier.height(Dimens.callButtonSize))
            }
        }

        Spacer(Modifier.height(Dimens.spaceSm))

        // End call — the single solid accent action.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PrimaryCallButton(
                icon = Icons.Filled.CallEnd,
                contentDescription = "End call",
                onClick = { onEvent(InCallEvent.EndCall) },
            )
        }
    }
}

/** Record button caption reflects the honest tier (§2.1). */
private fun recordButtonLabel(state: InCallUiState): String = when {
    state.isRecording -> "STOP REC"
    state.recordingTier == RecordingTier.UNAVAILABLE -> "NO REC"
    state.recordingTier.isTwoWay -> "REC 2-WAY"
    else -> "REC 1-SIDE"
}
