// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.model.Voicemail
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EmptyState
import com.glyphdialer.feature.voicemail.component.VoicemailRow
import com.glyphdialer.feature.voicemail.component.VvmUnsupportedFallback

/**
 * Visual Voicemail screen entry point (BUILD_SPEC §8).
 *
 * Stateful wrapper: collects [VoicemailUiState], consumes one-shot [VoicemailEffect]s
 * (delegating dialing up to the host via [onDial]), and forwards events to the
 * ViewModel. The actual layout is the stateless [VoicemailScreen] for previewability.
 *
 * @param onNavigateUp pop the voicemail destination.
 * @param onDial host-provided dialer (TelecomManager / ACTION_CALL). The feature does
 *   not depend on :telecom, so call-back / carrier-voicemail dialing is delegated up.
 */
@Composable
fun VoicemailRoute(
    onNavigateUp: () -> Unit,
    onDial: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoicemailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VoicemailEffect.Dial -> onDial(effect.number)
                is VoicemailEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    VoicemailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

/**
 * Stateless voicemail layout. Renders one of three bodies depending on
 * [VoicemailUiState.support]:
 *  - UNKNOWN  → a dot-matrix loading state.
 *  - UNSUPPORTED → the honest [VvmUnsupportedFallback] with the carrier-dial CTA (§9).
 *  - SUPPORTED → the message list (empty state when there are none).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreen(
    uiState: VoicemailUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (VoicemailEvent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Surface errors as snackbars and clear them so they don't re-fire on recomposition.
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(VoicemailEvent.DismissError)
        }
    }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("VOICEMAIL", style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
                        if (uiState.unreadCount > 0) {
                            Text(
                                text = "  ${uiState.unreadCount} NEW",
                                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState.support) {
                VvmSupportState.UNKNOWN -> if (uiState.isLoading) {
                    VoicemailLoading(modifier = Modifier.align(Alignment.Center))
                }

                VvmSupportState.UNSUPPORTED -> VvmUnsupportedFallback(
                    carrierVoicemailNumber = uiState.carrierVoicemailNumber,
                    onDialCarrierVoicemail = { onEvent(VoicemailEvent.DialCarrierVoicemail) },
                    modifier = Modifier.align(Alignment.Center),
                )

                VvmSupportState.SUPPORTED -> SupportedBody(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun SupportedBody(
    uiState: VoicemailUiState,
    onEvent: (VoicemailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VoicemailLoading()
        }
        return
    }

    if (uiState.isEmpty) {
        EmptyState(
            title = "No voicemail",
            message = "New voicemails on this line will appear here.",
            icon = Icons.Filled.Voicemail,
            modifier = modifier.fillMaxSize().padding(top = Dimens.spaceXxl),
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(TestTags.VoicemailList),
        contentPadding = PaddingValues(
            horizontal = Dimens.spaceLg,
            vertical = Dimens.spaceMd,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        items(uiState.items, key = { it.id }) { item ->
            VoicemailRow(
                item = item,
                playback = uiState.playback,
                isTranscribing = uiState.transcribingId == item.id,
                onPlay = { onEvent(VoicemailEvent.Play(item.id)) },
                onPause = { onEvent(VoicemailEvent.Pause) },
                onSeek = { fraction -> onEvent(VoicemailEvent.SeekTo(fraction)) },
                onTranscribe = { onEvent(VoicemailEvent.Transcribe(item.id)) },
                onCallBack = { onEvent(VoicemailEvent.CallBack(item.id)) },
                onDelete = { onEvent(VoicemailEvent.Delete(item.id)) },
                onToggleRead = { onEvent(VoicemailEvent.SetRead(item.id, !item.voicemail.isRead)) },
            )
        }
    }
}

@Composable
private fun VoicemailLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        com.glyphdialer.core.ui.component.DotMatrixSpinner(size = 40.dp)
        DottedDivider(modifier = Modifier.fillMaxWidth(0.4f))
        Text(
            text = "CHECKING VOICEMAIL",
            style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Test tags for instrumented Compose tests. */
internal object TestTags {
    const val VoicemailList = "voicemail_list"
    const val CarrierDialButton = "carrier_dial_button"
}

// --- Previews ----------------------------------------------------------------------

private fun previewItem(
    id: Long,
    name: String,
    read: Boolean,
    transcription: String?,
) = VoicemailItem(
    voicemail = Voicemail(
        id = id,
        number = PhoneNumber(raw = "+14155550$id", formatted = "(415) 555-0$id"),
        displayName = name,
        timestampMillis = 1_700_000_000_000L - id * 3_600_000L,
        durationSeconds = 27 + id,
        isRead = read,
        hasContent = true,
        audioUri = "content://vvm/$id",
        transcriptionText = transcription,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Voicemail · list", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewVoicemailList() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        VoicemailScreen(
            uiState = VoicemailUiState(
                support = VvmSupportState.SUPPORTED,
                isLoading = false,
                items = listOf(
                    previewItem(1, "Ada Lovelace", read = false, "Hey, calling about the analytical engine demo tomorrow."),
                    previewItem(2, null, read = true, null),
                ),
                playback = PlaybackState(
                    voicemailId = 1,
                    isPlaying = true,
                    positionMillis = 8_000,
                    durationMillis = 27_000,
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Voicemail · unsupported", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewVoicemailUnsupported() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        VoicemailScreen(
            uiState = VoicemailUiState(
                support = VvmSupportState.UNSUPPORTED,
                isLoading = false,
                carrierVoicemailNumber = "+18056377243",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Voicemail · empty", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewVoicemailEmpty() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        VoicemailScreen(
            uiState = VoicemailUiState(support = VvmSupportState.SUPPORTED, isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
