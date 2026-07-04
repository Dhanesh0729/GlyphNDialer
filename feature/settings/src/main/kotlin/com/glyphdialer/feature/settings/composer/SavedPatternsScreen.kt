// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CustomGlyphPattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPatternsRoute(
    onNavigateUp: () -> Unit,
    onNavigateToComposer: () -> Unit,
    onNavigateToAssign: (String) -> Unit,
    viewModel: SavedPatternsViewModel = hiltViewModel()
) {
    val patterns by viewModel.patterns.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SAVED PATTERNS",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToComposer) {
                Icon(Icons.Filled.Add, contentDescription = "Create new pattern")
            }
        }
    ) { padding ->
        if (patterns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved patterns yet. Create one!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd)
            ) {
                items(patterns) { pattern ->
                    PatternItem(
                        pattern = pattern,
                        onClick = { onNavigateToAssign(pattern.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternItem(pattern: CustomGlyphPattern, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXs)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceMd)) {
            Text(
                text = pattern.name.ifBlank { "Unnamed Pattern" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spaceXs))
            Text(
                text = "${pattern.frames.size} frames • Tap to assign to contacts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
