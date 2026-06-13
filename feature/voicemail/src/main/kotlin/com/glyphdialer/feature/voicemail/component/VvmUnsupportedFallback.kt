// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixText
import com.glyphdialer.core.ui.component.DottedDivider

/**
 * The honest fallback shown when visual voicemail is not supported on the active
 * line (BUILD_SPEC §8 — "else carrier VM dial shortcut"; HONESTY PRINCIPLE §9).
 *
 * We do NOT fabricate a message list. Instead we explain that VVM is carrier/line
 * dependent and offer the one thing that genuinely works everywhere: dialing the
 * carrier voicemail box. The dial button is disabled (with an explanation) when even
 * the carrier number couldn't be resolved.
 *
 * @param carrierVoicemailNumber the dialable carrier VM number, or null if unknown.
 * @param onDialCarrierVoicemail invoked when the user taps "CALL VOICEMAIL".
 */
@Composable
fun VvmUnsupportedFallback(
    carrierVoicemailNumber: String?,
    onDialCarrierVoicemail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.Voicemail,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DotMatrixText(
            text = "NO VISUAL VOICEMAIL",
            dotColor = MaterialTheme.colorScheme.onSurface,
            dotSize = 3.dp,
            dotSpacing = 4.dp,
        )

        Text(
            text = "Visual voicemail isn't available on this line. Whether it works " +
                "depends on your carrier and SIM. You can still listen to messages by " +
                "calling your carrier's voicemail box.",
            modifier = Modifier.widthIn(max = 340.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        DottedDivider(modifier = Modifier.fillMaxWidth(0.5f))

        if (carrierVoicemailNumber != null) {
            Button(
                onClick = onDialCarrierVoicemail,
                modifier = Modifier.testTag("carrier_dial_button"),
            ) {
                Icon(Icons.Filled.Dialpad, contentDescription = null)
                Text(
                    text = "  CALL VOICEMAIL",
                    style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                )
            }
            Text(
                text = carrierVoicemailNumber,
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Honest: we can't even resolve the carrier number — say so, don't guess.
            Text(
                text = "Couldn't find your carrier voicemail number. Dial it manually " +
                    "from the dialpad (often 1, or your carrier's code).",
                modifier = Modifier.widthIn(max = 340.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "VvmUnsupported · with number", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewWithNumber() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        VvmUnsupportedFallback(carrierVoicemailNumber = "+18056377243", onDialCarrierVoicemail = {})
    }
}

@Preview(name = "VvmUnsupported · no number", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewNoNumber() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        VvmUnsupportedFallback(carrierVoicemailNumber = null, onDialCarrierVoicemail = {})
    }
}
