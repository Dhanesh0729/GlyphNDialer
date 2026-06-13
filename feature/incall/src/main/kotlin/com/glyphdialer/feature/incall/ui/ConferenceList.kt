// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixAvatar
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EngineeredCard

/**
 * Conference participant list (BUILD_SPEC §10). Renders each child call with a
 * deterministic dot-matrix avatar + name/number, and — when the parent advertises
 * `CAPABILITY_MANAGE_CONFERENCE` ([CallModel.capability.canManageConference]) —
 * per-participant hold/resume, split, and disconnect actions.
 *
 * HONESTY: management controls are shown only where the participant's capability
 * allows; otherwise the row is informational. Cellular conferences are
 * carrier-mediated and may not expose per-party management (§10).
 */
@Composable
fun ConferenceList(
    participants: List<CallModel>,
    onHold: (callId: String, hold: Boolean) -> Unit,
    onDisconnect: (callId: String) -> Unit,
    onSplit: (callId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EngineeredCard(
        modifier = modifier.fillMaxWidth(),
        indexLabel = "PARTIES · ${participants.size}",
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            participants.forEachIndexed { index, participant ->
                if (index > 0) {
                    DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceXs))
                }
                ParticipantRow(
                    participant = participant,
                    onHold = onHold,
                    onDisconnect = onDisconnect,
                    onSplit = onSplit,
                )
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: CallModel,
    onHold: (callId: String, hold: Boolean) -> Unit,
    onDisconnect: (callId: String) -> Unit,
    onSplit: (callId: String) -> Unit,
) {
    val canManage = participant.capability.canManageConference
    val onHoldNow = participant.isOnHold || participant.state == CallState.HOLDING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        DotMatrixAvatar(
            seed = participant.number.dialValue.ifBlank { participant.displayName.orEmpty() },
            size = Dimens.avatarSm,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.displayName ?: participant.number.formatted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (onHoldNow) "ON HOLD" else "IN CONFERENCE",
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            IconButton(onClick = { onHold(participant.id, !onHoldNow) }) {
                Icon(
                    imageVector = if (onHoldNow) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (onHoldNow) "Resume participant" else "Hold participant",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onSplit(participant.id) }) {
                Icon(
                    imageVector = Icons.Filled.CallSplit,
                    contentDescription = "Split from conference",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDisconnect(participant.id) }) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Disconnect participant",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(name = "ConferenceList", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewConferenceList() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        ConferenceList(
            participants = listOf(
                CallModel(
                    id = "p1",
                    number = PhoneNumber(raw = "+14155550142", formatted = "+1 415-555-0142"),
                    displayName = "Ada Lovelace",
                    state = CallState.ACTIVE,
                    capability = com.glyphdialer.core.domain.model.CallCapability(canManageConference = true),
                ),
                CallModel(
                    id = "p2",
                    number = PhoneNumber(raw = "+14155550188", formatted = "+1 415-555-0188"),
                    displayName = "Alan Turing",
                    state = CallState.HOLDING,
                    isOnHold = true,
                    capability = com.glyphdialer.core.domain.model.CallCapability(canManageConference = true),
                ),
            ),
            onHold = { _, _ -> },
            onDisconnect = {},
            onSplit = {},
        )
    }
}
