// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CustomGlyphZone

@Composable
fun GlyphComposerRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlyphComposerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GlyphComposerScreen(
        uiState = uiState,
        onNameChange = viewModel::updatePatternName,
        onAddFrame = viewModel::addFrame,
        onRemoveFrame = viewModel::removeFrame,
        onSave = { viewModel.savePattern(onNavigateUp) },
        onNavigateUp = onNavigateUp,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphComposerScreen(
    uiState: GlyphComposerUiState,
    onNameChange: (String) -> Unit,
    onAddFrame: (List<CustomGlyphZone>, Float, Int) -> Unit,
    onRemoveFrame: (Int) -> Unit,
    onSave: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("COMPOSER", style = MaterialTheme.typography.titleMedium.merge(NumberStyle)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = uiState.frames.isNotEmpty() && !uiState.isSaving
                    ) {
                        Text("SAVE")
                    }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Frame")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.patternName,
                onValueChange = onNameChange,
                label = { Text("Pattern Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.screenPadding)
            )

            if (uiState.frames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No frames added yet. Click + to add a light sequence.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Dimens.screenPadding,
                        vertical = Dimens.spaceMd
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
                ) {
                    itemsIndexed(uiState.frames) { index, frame ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimens.spaceMd),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Frame ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                    Text("Zones: ${frame.zones.joinToString { it.name }}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Intensity: ${(frame.intensity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                    Text("Duration: ${frame.durationMs}ms", style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { onRemoveFrame(index) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Frame")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var intensity by remember { mutableFloatStateOf(0.8f) }
        var duration by remember { mutableIntStateOf(100) }
        var selectedZone by remember { mutableStateOf(CustomGlyphZone.ALL) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Frame") },
            text = {
                Column {
                    Text("Zone")
                    // Simple dropdown or list can be replaced, but using a slider for simplicity
                    androidx.compose.material3.DropdownMenu(
                        expanded = false, // Simplified UI for snippet
                        onDismissRequest = {}
                    ) {}
                    Text("Selected: ${selectedZone.name}")
                    
                    // Simple zone cycling for demo purposes
                    Button(onClick = { 
                        val next = CustomGlyphZone.entries[(selectedZone.ordinal + 1) % CustomGlyphZone.entries.size]
                        selectedZone = next
                    }) {
                        Text("Cycle Zone")
                    }

                    Text("Intensity: ${(intensity * 100).toInt()}%")
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0f..1f
                    )

                    Text("Duration: ${duration}ms")
                    Slider(
                        value = duration.toFloat(),
                        onValueChange = { duration = it.toInt() },
                        valueRange = 10f..1000f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddFrame(listOf(selectedZone), intensity, duration)
                    showAddDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
