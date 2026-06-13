// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.ui.graphics.vector.ImageVector
import com.glyphdialer.core.domain.model.CallType

/**
 * Maps a [CallType] to its icon + whether it takes the brand accent, for the recent-
 * interactions timeline (mirrors the call-log feature's mapping; kept local since
 * features don't depend on each other, §3). Missed-like types get the accent; the
 * rest stay monochrome per the §15 design language.
 */
internal data class CallTypeIcon(
    val icon: ImageVector,
    val accented: Boolean,
    val contentDescription: String,
)

internal fun callTypeIcon(type: CallType): CallTypeIcon = when (type) {
    CallType.INCOMING -> CallTypeIcon(Icons.AutoMirrored.Filled.CallReceived, false, "Incoming call")
    CallType.OUTGOING -> CallTypeIcon(Icons.AutoMirrored.Filled.CallMade, false, "Outgoing call")
    CallType.MISSED -> CallTypeIcon(Icons.AutoMirrored.Filled.CallMissed, true, "Missed call")
    CallType.REJECTED -> CallTypeIcon(Icons.AutoMirrored.Filled.CallMissed, true, "Declined call")
    CallType.BLOCKED -> CallTypeIcon(Icons.Filled.Block, true, "Blocked call")
    CallType.VOICEMAIL -> CallTypeIcon(Icons.Filled.Voicemail, false, "Voicemail")
}
