// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.EngineeredCard
import com.glyphdialer.core.ui.component.GlyphKey

/**
 * The in-call DTMF keypad overlay (BUILD_SPEC §9). Reuses the design-system
 * [GlyphKey]; each tap raises [onDigit] which the ViewModel forwards to
 * [com.glyphdialer.core.domain.usecase.SendDtmfUseCase] (and mirrors the Glyph
 * stroke). The keypad disables itself honestly when the call can't accept DTMF.
 *
 * NOTE the Glyph per-key stroke is fired by the ViewModel on the resulting
 * [com.glyphdialer.feature.incall.InCallEvent.Dtmf] event, so [GlyphKey.onDigitStroke]
 * is intentionally left as its default no-op to avoid double-firing.
 */
@Composable
fun DtmfKeypad(
    onDigit: (Char) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    EngineeredCard(
        modifier = modifier.fillMaxWidth(),
        indexLabel = "DTMF",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            if (!enabled) {
                Text(
                    text = "DTMF NOT SUPPORTED ON THIS CALL",
                    style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            KEY_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg)) {
                    row.forEach { (digit, letters) ->
                        GlyphKey(
                            digit = digit,
                            letters = letters,
                            onPress = { onDigit(it) },
                            enabled = enabled,
                            keySize = Dimens.dialKeySize.times(0.8f),
                        )
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Close keypad",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ITU-T standard dialpad layout. */
private val KEY_ROWS: List<List<Pair<Char, String>>> = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

@Preview(name = "DtmfKeypad", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDtmfKeypad() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        DtmfKeypad(onDigit = {}, onClose = {})
    }
}
