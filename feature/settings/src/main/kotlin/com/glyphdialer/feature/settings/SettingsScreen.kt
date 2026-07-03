// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CapabilityFlags
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixSpinner
import com.glyphdialer.feature.settings.component.AboutGroup
import com.glyphdialer.feature.settings.component.AppearanceGroup
import com.glyphdialer.feature.settings.component.CallsGroup
import com.glyphdialer.feature.settings.component.ContactsGroup
import com.glyphdialer.feature.settings.component.GlyphGroup
import com.glyphdialer.feature.settings.component.LockedGlyphGroup
import com.glyphdialer.feature.settings.component.PlanGroup
import com.glyphdialer.feature.settings.component.RecordingGroup
import com.glyphdialer.feature.settings.component.TranscriptionGroup

/**
 * Settings root screen entry point (BUILD_SPEC §21).
 *
 * Stateful wrapper: collects [SettingsUiState] (so the live theme/font/accent preview
 * reacts immediately as the user picks), consumes one-shot [SettingsEffect]s
 * (delegating cross-feature navigation up to the host), and forwards intents to the
 * ViewModel. The layout itself is the stateless [SettingsScreen] for previewability.
 *
 * The screen is rendered INSIDE the app's [GlyphTheme] (the host wraps the NavHost),
 * so changing the theme/font/accent re-tints this very screen live — the
 * "live theme/font/accent preview" called out for this module.
 *
 * @param onNavigateUp pop the settings destination.
 * @param onOpenBlockedNumbers navigate to the in-feature blocked-numbers sub-screen.
 * @param onOpenAbout navigate to the in-feature about/legal sub-screen.
 * @param onOpenSpeedDial host-provided speed-dial assignment surface (other feature).
 * @param onOpenOpenSourceLicenses navigate to the in-feature open-source-licenses screen.
 */
@Composable
fun SettingsRoute(
    onNavigateUp: () -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSpeedDial: () -> Unit,
    onOpenOpenSourceLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateToBlockedNumbers -> onOpenBlockedNumbers()
                SettingsEffect.NavigateToAbout -> onOpenAbout()
                SettingsEffect.OpenSpeedDial -> onOpenSpeedDial()
                SettingsEffect.OpenOpenSourceLicenses -> onOpenOpenSourceLicenses()
                is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

/**
 * Stateless settings layout. Renders the seven groups (BUILD_SPEC §21) in order,
 * applying the §9 honesty gating:
 *  - the Glyph group is OMITTED entirely when [SettingsUiState.showGlyphGroup] is false;
 *  - the Recording group surfaces the active tier read-only;
 *  - the no-announcement toggle always carries the §2.2 disclaimer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Surface errors as snackbars, then clear so they don't re-fire on recomposition.
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(SettingsEvent.DismissError)
        }
    }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    )
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
            if (uiState.isLoading) {
                DotMatrixSpinner(
                    size = 40.dp,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(SettingsTestTags.SettingsList),
                    contentPadding = PaddingValues(
                        horizontal = Dimens.screenPadding,
                        vertical = Dimens.spaceMd,
                    ),
                ) {
                    item(key = "appearance") { AppearanceGroup(uiState, onEvent) }
                    item(key = "plan") { PlanGroup(uiState) }

                    // §9/§17: Glyph group is hidden entirely on non-capable devices.
                    when {
                        uiState.showGlyphGroup -> item(key = "glyph") { GlyphGroup(uiState, onEvent) }
                        uiState.showLockedGlyphGroup -> item(key = "glyph_locked") { LockedGlyphGroup() }
                    }

                    item(key = "calls") { CallsGroup(uiState, onEvent) }
                    item(key = "recording") { RecordingGroup(uiState, onEvent) }
                    item(key = "transcription") { TranscriptionGroup(uiState, onEvent) }
                    item(key = "contacts") { ContactsGroup(uiState, onEvent) }
                    item(key = "about") { AboutGroup(onEvent) }
                }
            }
        }
    }
}

/** Test tags for instrumented Compose tests. */
object SettingsTestTags {
    const val SettingsList = "settings_list"
}

// --- Previews ----------------------------------------------------------------------

private fun previewState(
    glyphAvailable: Boolean,
    tier: RecordingTier,
) = SettingsUiState(
    isLoading = false,
    capabilities = CapabilityFlags(
        glyphAvailable = glyphAvailable,
        microphoneAvailable = tier.isAvailable,
        onDeviceSpeechAvailable = true,
        maxRecordingTier = tier,
    ),
    contactsSyncStatus = ContactsSyncStatus.Synced(lastSyncMillis = 1_700_000_000_000L),
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Settings · Nothing (Glyph on)", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSettingsGlyph() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        SettingsScreen(
            uiState = previewState(glyphAvailable = true, tier = RecordingTier.LOCAL_ONE_SIDED),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Settings · non-Nothing (Glyph hidden)", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSettingsNoGlyph() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        SettingsScreen(
            uiState = previewState(glyphAvailable = false, tier = RecordingTier.SYSTEM_TWO_WAY),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
