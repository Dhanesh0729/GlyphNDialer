// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CustomGlyphPattern
import com.glyphdialer.core.domain.model.GlyphHardwareProfile
import com.glyphdialer.core.ui.component.EngineeredCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPatternsRoute(
    onNavigateUp: () -> Unit,
    onNavigateToComposer: () -> Unit,
    onNavigateToAssign: (String) -> Unit,
    viewModel: SavedPatternsViewModel = hiltViewModel(),
) {
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()
    val hardwareProfile = viewModel.hardwareProfile

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GLYPH PRESETS",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToComposer) {
                        Icon(Icons.Filled.Add, contentDescription = "Create pattern")
                    }
                },
            )
        },
    ) { padding ->
        if (patterns.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.screenPadding),
                contentAlignment = Alignment.Center,
            ) {
                EngineeredCard(indexLabel = "00") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "NO SAVED PRESETS",
                            style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Create a short light-and-sound identity for callers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onNavigateToComposer) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(Dimens.spaceSm))
                            Text("CREATE")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    horizontal = Dimens.screenPadding,
                    vertical = Dimens.spaceLg,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            ) {
                items(patterns, key = { it.id }) { pattern ->
                    PatternItem(
                        pattern = pattern,
                        hardwareProfile = hardwareProfile,
                        onPreview = { viewModel.preview(pattern) },
                        onAssign = { onNavigateToAssign(pattern.id) },
                        onDelete = { viewModel.delete(pattern.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternItem(
    pattern: CustomGlyphPattern,
    hardwareProfile: GlyphHardwareProfile,
    onPreview: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit,
) {
    EngineeredCard(indexLabel = pattern.frames.size.toString().padStart(2, '0')) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphHardwarePreview(
                    profile = hardwareProfile,
                    zones = pattern.frames.firstOrNull()?.zones.orEmpty().toSet(),
                    intensity = pattern.frames.firstOrNull()?.safeIntensity ?: 0.6f,
                    modifier = Modifier.width(58.dp).height(86.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pattern.name.ifBlank { "Unnamed pattern" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${pattern.frames.size} frames / ${pattern.durationMs} ms / ${pattern.soundStyle.name}",
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = hardwareProfile.displayName,
                        style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(Dimens.spaceSm))
                    Text("PREVIEW")
                }
                Button(onClick = onAssign, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.size(Dimens.spaceSm))
                    Text("ASSIGN")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete preset")
                }
            }
        }
    }
}
