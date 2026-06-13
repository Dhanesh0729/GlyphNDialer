// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.CallType

/**
 * Maps a [CallType] to its glyph + tint for the call-log row (BUILD_SPEC §8 —
 * "call-type icons: incoming/outgoing/missed/rejected/blocked/voicemail"). Missed-like
 * types take the single brand accent (the only place the call log uses color);
 * everything else stays monochrome per the §15 design language.
 */
internal data class CallTypeVisual(
    val icon: ImageVector,
    val accented: Boolean,
    val contentDescription: String,
)

internal fun callTypeVisual(type: CallType): CallTypeVisual = when (type) {
    CallType.INCOMING -> CallTypeVisual(
        icon = Icons.AutoMirrored.Filled.CallReceived,
        accented = false,
        contentDescription = "Incoming call",
    )
    CallType.OUTGOING -> CallTypeVisual(
        icon = Icons.AutoMirrored.Filled.CallMade,
        accented = false,
        contentDescription = "Outgoing call",
    )
    CallType.MISSED -> CallTypeVisual(
        icon = Icons.AutoMirrored.Filled.CallMissed,
        accented = true,
        contentDescription = "Missed call",
    )
    CallType.REJECTED -> CallTypeVisual(
        icon = Icons.AutoMirrored.Filled.CallMissed,
        accented = true,
        contentDescription = "Declined call",
    )
    CallType.BLOCKED -> CallTypeVisual(
        icon = Icons.Filled.Block,
        accented = true,
        contentDescription = "Blocked call",
    )
    CallType.VOICEMAIL -> CallTypeVisual(
        icon = Icons.Filled.Voicemail,
        accented = false,
        contentDescription = "Voicemail",
    )
}

/** Resolve the tint for a [CallTypeVisual] against the current theme. */
@Composable
internal fun CallTypeVisual.tint(monochrome: Color): Color =
    if (accented) LocalAccentColor.current else monochrome

/** Icon + label pairing for a [CallLogQuickAction] in the radial menu. */
internal data class QuickActionVisual(
    val icon: ImageVector,
    val label: String,
)

internal fun CallLogQuickAction.visual(): QuickActionVisual = when (this) {
    CallLogQuickAction.CALL -> QuickActionVisual(Icons.Filled.Call, "Call")
    CallLogQuickAction.MESSAGE -> QuickActionVisual(Icons.AutoMirrored.Filled.Message, "Message")
    CallLogQuickAction.RECORD_NEXT -> QuickActionVisual(Icons.Filled.FiberManualRecord, "Record next")
    CallLogQuickAction.COPY -> QuickActionVisual(Icons.Filled.ContentCopy, "Copy")
    CallLogQuickAction.BLOCK -> QuickActionVisual(Icons.Filled.Block, "Block")
}
