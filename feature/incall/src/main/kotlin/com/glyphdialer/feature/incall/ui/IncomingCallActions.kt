// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * Answer / reject actions for an incoming ringing call (BUILD_SPEC §9). A green-toned
 * answer and the accent end-call button, each with an underline label.
 */
@Composable
fun IncomingCallActions(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryCallButton(
                icon = Icons.Filled.CallEnd,
                contentDescription = "Reject call",
                onClick = onReject,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
            Text(
                text = "DECLINE",
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryCallButton(
                icon = Icons.Filled.Call,
                contentDescription = "Answer call",
                onClick = onAnswer,
                // Answer uses an "on" surface tone; brand stays monochrome + single accent.
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            )
            Text(
                text = "ANSWER",
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "IncomingCallActions", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewIncoming() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        IncomingCallActions(onAnswer = {}, onReject = {})
    }
}
