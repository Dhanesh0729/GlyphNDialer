// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.AppPlan
import com.glyphdialer.feature.settings.component.GlyphGroup
import com.glyphdialer.feature.settings.component.LockedGlyphGroup
import com.glyphdialer.feature.settings.component.PremiumAppearanceGroup

@Composable
fun ProSettingsRoute(
    onNavigateUp: () -> Unit,
    onOpenGlyphComposer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                com.glyphdialer.feature.settings.SettingsEffect.NavigateToGlyphComposer -> onOpenGlyphComposer()
                else -> {}
            }
        }
    }

    ProSettingsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PRO FEATURES",
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
    ) { padding ->
        if (uiState.preferences.appPlan == AppPlan.FREE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Dimens.spaceXl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Unlock Basic or Pro to access premium features like Glyph customization, premium themes, fonts, and accents.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    horizontal = Dimens.screenPadding,
                    vertical = Dimens.spaceMd,
                ),
            ) {
                item(key = "appearance") { PremiumAppearanceGroup(uiState, onEvent) }
                
                    item(key = "composer_link") {
                        com.glyphdialer.feature.settings.component.SettingsFolder(title = "Custom Glyph Composer", modifier = Modifier) {
                            com.glyphdialer.feature.settings.component.SettingsNavigationRow(
                                title = "Glyph Composer",
                                subtitle = "Create custom Glyph sequences and assign them to specific contacts",
                                onClick = { onEvent(SettingsEvent.OpenGlyphComposer) }
                            )
                        }
                    }
            }
        }
    }
}
