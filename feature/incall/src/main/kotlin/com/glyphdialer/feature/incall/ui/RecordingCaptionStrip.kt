// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.TickerCaption
import com.glyphdialer.feature.incall.InCallUiState

/**
 * The honest recording + live-caption status strip (BUILD_SPEC §12/§13, §2.1/§2.4).
 *
 * Shows the *active recording tier* in plain language ("Recording · two-way" /
 * "Recording · my side only" / "Recording unavailable") so the UI never implies a
 * one-sided capture is a full call (§2.1). When captions are enabled it renders the
 * scrolling [TickerCaption] with an honest local-side-only note where applicable
 * (§2.4).
 */
@Composable
fun RecordingCaptionStrip(
    state: InCallUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        // Recording status — only shown while recording (the toggle lives in controls).
        AnimatedVisibility(visible = state.isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                // Accent record dot.
                Spacer(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Text(
                    text = recordingLabel(state.activeRecordingTier),
                    style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Live captions overlay (honest gate handled by the ViewModel).
        AnimatedVisibility(visible = state.captionsEnabled) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TickerCaption(
                    text = state.captionsText,
                    placeholder = "LISTENING…",
                )
                if (state.captionsLocalSideOnly) {
                    // §2.4: cellular captions cover only the local side — say so.
                    Text(
                        text = "CAPTIONS · MY SIDE ONLY (CELLULAR LIMIT)",
                        style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Plain-language label for the active recording [tier] — never overclaims (§2.1). */
internal fun recordingLabel(tier: RecordingTier): String = when (tier) {
    RecordingTier.SYSTEM_TWO_WAY -> "REC · TWO-WAY (SYSTEM)"
    RecordingTier.VOIP_TWO_WAY -> "REC · TWO-WAY (VOIP)"
    RecordingTier.SPEAKER_TWO_WAY -> "REC · TWO-WAY (SPEAKER)"
    RecordingTier.LOCAL_ONE_SIDED -> "REC · MY SIDE ONLY"
    RecordingTier.UNAVAILABLE -> "REC · UNAVAILABLE"
}

@Preview(name = "RecordingCaptionStrip", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewStrip() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        RecordingCaptionStrip(
            state = InCallUiState(
                isRecording = true,
                activeRecordingTier = RecordingTier.LOCAL_ONE_SIDED,
                captionsEnabled = true,
                captionsText = "HELLO THIS IS A LIVE CAPTION",
                captionsLocalSideOnly = true,
                isLoading = false,
            ),
        )
    }
}
