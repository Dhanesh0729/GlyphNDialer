// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CustomGlyphFrame
import com.glyphdialer.core.domain.model.CustomGlyphZone
import com.glyphdialer.core.domain.model.GlyphFrameSound
import com.glyphdialer.core.domain.model.GlyphHardwareProfile
import com.glyphdialer.core.domain.model.GlyphSoundStyle
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EngineeredCard

@Composable
fun GlyphComposerRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlyphComposerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    GlyphComposerScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNameChange = viewModel::updatePatternName,
        onToggleZone = viewModel::toggleZone,
        onIntensityChange = viewModel::updateIntensity,
        onDurationChange = viewModel::updateDuration,
        onSoundStyleChange = viewModel::updateSoundStyle,
        onFrameSoundChange = viewModel::updateFrameSound,
        onRepeatChange = viewModel::updateRepeatCount,
        onAddFrame = viewModel::addFrame,
        onRemoveFrame = viewModel::removeFrame,
        onDuplicateFrame = viewModel::duplicateFrame,
        onMoveFrame = viewModel::moveFrame,
        onPreview = viewModel::previewPattern,
        onSave = { viewModel.savePattern(onNavigateUp) },
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphComposerScreen(
    uiState: GlyphComposerUiState,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onToggleZone: (CustomGlyphZone) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
    onSoundStyleChange: (GlyphSoundStyle) -> Unit,
    onFrameSoundChange: (GlyphFrameSound) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onRemoveFrame: (Int) -> Unit,
    onDuplicateFrame: (Int) -> Unit,
    onMoveFrame: (Int, Int) -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GLYPH COMPOSER",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onPreview, enabled = uiState.frames.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Preview")
                    }
                    IconButton(onClick = onSave, enabled = uiState.canSave) {
                        Icon(Icons.Filled.Save, contentDescription = "Save pattern")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = Dimens.screenPadding,
                vertical = Dimens.spaceLg,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            item(key = "overview") {
                ComposerOverview(uiState = uiState)
            }

            item(key = "name") {
                OutlinedTextField(
                    value = uiState.patternName,
                    onValueChange = onNameChange,
                    label = { Text("Pattern name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "zones") {
                FrameBuilder(
                    uiState = uiState,
                    onToggleZone = onToggleZone,
                    onIntensityChange = onIntensityChange,
                    onDurationChange = onDurationChange,
                    onSoundStyleChange = onSoundStyleChange,
                    onFrameSoundChange = onFrameSoundChange,
                    onRepeatChange = onRepeatChange,
                    onAddFrame = onAddFrame,
                )
            }

            item(key = "timeline_header") {
                TimelineHeader(
                    frameCount = uiState.frames.size,
                    durationMs = uiState.totalDurationMs,
                    isPreviewing = uiState.isPreviewing,
                    onPreview = onPreview,
                )
            }

            if (uiState.frames.isEmpty()) {
                item(key = "empty") {
                    EngineeredCard(indexLabel = "00") {
                        Text(
                            text = "Add frames to build a light-and-sound pattern.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(uiState.frames, key = { index, frame -> "${index}_${frame.hashCode()}" }) { index, frame ->
                    FrameRow(
                        index = index,
                        frame = frame,
                        hardwareProfile = uiState.hardwareProfile,
                        isFirst = index == 0,
                        isLast = index == uiState.frames.lastIndex,
                        onMoveUp = { onMoveFrame(index, -1) },
                        onMoveDown = { onMoveFrame(index, 1) },
                        onDuplicate = { onDuplicateFrame(index) },
                        onRemove = { onRemoveFrame(index) },
                    )
                }
            }

            item(key = "save") {
                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.size(Dimens.spaceSm))
                    Text(if (uiState.isSaving) "SAVING" else "SAVE PATTERN")
                }
            }
        }
    }
}

@Composable
private fun ComposerOverview(uiState: GlyphComposerUiState) {
    EngineeredCard(indexLabel = "LIVE") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphHardwarePreview(
                    profile = uiState.hardwareProfile,
                    zones = uiState.activePreviewZones,
                    intensity = uiState.activePreviewIntensity,
                    modifier = Modifier.weight(0.9f),
                )
                Column(modifier = Modifier.weight(1.1f)) {
                    Text(
                        text = uiState.hardwareProfile.displayName.uppercase(),
                        style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (uiState.glyphAvailable) {
                            "Screen and hardware preview use the detected Glyph layout."
                        } else {
                            "Screen preview stays editable until Glyph hardware is available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Dimens.spaceSm))
                    Text(
                        text = "${uiState.frames.size} frames / ${uiState.totalDurationMs} ms",
                        style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            DottedDivider()
            Text(
                text = if (uiState.isPreviewing) {
                    "Preview is playing on screen with matching haptic and sound cues."
                } else {
                    "Keep patterns short and recognizable. Incoming calls loop this sequence until answered or dismissed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrameBuilder(
    uiState: GlyphComposerUiState,
    onToggleZone: (CustomGlyphZone) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
    onSoundStyleChange: (GlyphSoundStyle) -> Unit,
    onFrameSoundChange: (GlyphFrameSound) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onAddFrame: () -> Unit,
) {
    EngineeredCard(indexLabel = "ADD") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
            Label("Zones")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                CustomGlyphZone.entries.forEach { zone ->
                    ComposerChip(
                        label = zone.label(),
                        selected = zone in uiState.selectedZones,
                        onClick = { onToggleZone(zone) },
                    )
                }
            }
            val unsupportedZones = CustomGlyphZone.entries
                .filterNot { it == CustomGlyphZone.ALL || uiState.hardwareProfile.supports(it) }
            if (unsupportedZones.isNotEmpty()) {
                Text(
                    text = "Unavailable zones remap to the nearest LEDs on ${uiState.hardwareProfile.displayName}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SliderRow(
                title = "Intensity",
                value = uiState.intensity,
                label = "${(uiState.intensity * 100).toInt()}%",
                valueRange = 0.05f..1f,
                onChange = onIntensityChange,
            )
            SliderRow(
                title = "Duration",
                value = uiState.durationMs.toFloat(),
                label = "${uiState.durationMs} ms",
                valueRange = CustomGlyphFrame.MIN_DURATION_MS.toFloat()..1_000f,
                onChange = { onDurationChange(it.toInt()) },
            )

            Label("Pattern sound")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                GlyphSoundStyle.entries.forEach { style ->
                    ComposerChip(
                        label = style.label(),
                        selected = uiState.soundStyle == style,
                        onClick = { onSoundStyleChange(style) },
                    )
                }
            }

            Label("Frame cue")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                GlyphFrameSound.entries.forEach { cue ->
                    ComposerChip(
                        label = cue.label(),
                        selected = uiState.frameSound == cue,
                        onClick = { onFrameSoundChange(cue) },
                    )
                }
            }

            RepeatControl(repeatCount = uiState.repeatCount, onRepeatChange = onRepeatChange)

            Button(
                onClick = onAddFrame,
                enabled = uiState.canAddFrame,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(Dimens.spaceSm))
                Text("ADD FRAME")
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    frameCount: Int,
    durationMs: Int,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "TIMELINE",
                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "$frameCount frames / $durationMs ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onPreview, enabled = frameCount > 0 && !isPreviewing) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(Dimens.spaceSm))
            Text(if (isPreviewing) "PLAYING" else "PREVIEW")
        }
    }
}

@Composable
private fun FrameRow(
    index: Int,
    frame: CustomGlyphFrame,
    hardwareProfile: GlyphHardwareProfile,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    EngineeredCard(indexLabel = (index + 1).toString().padStart(2, '0')) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphHardwarePreview(
                profile = hardwareProfile,
                zones = frame.zones.toSet(),
                intensity = frame.safeIntensity,
                modifier = Modifier.size(width = 68.dp, height = 100.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = frame.zones.joinToString { it.label() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${(frame.safeIntensity * 100).toInt()}% / ${frame.safeDurationMs} ms / ${frame.soundCue.label()}",
                    style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onDuplicate, contentPadding = PaddingValues(0.dp)) {
                    Text("DUPLICATE")
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete frame")
                }
            }
        }
    }
}

@Composable
private fun RepeatControl(
    repeatCount: Int,
    onRepeatChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Repeats", style = MaterialTheme.typography.bodyLarge)
            Text(
                "How many times the preview plays the timeline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("-", enabled = repeatCount > 1) { onRepeatChange(repeatCount - 1) }
            Text(
                text = repeatCount.toString(),
                style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                color = MaterialTheme.colorScheme.primary,
            )
            StepButton("+", enabled = repeatCount < 8) { onRepeatChange(repeatCount + 1) }
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Dimens.spaceXs))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium.merge(NumberStyle)) },
        shape = RoundedCornerShape(Dimens.spaceSm),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun CustomGlyphZone.label(): String = when (this) {
    CustomGlyphZone.TOP_LEFT -> "Top L"
    CustomGlyphZone.TOP_RIGHT -> "Top R"
    CustomGlyphZone.CAMERA_RING -> "Camera"
    CustomGlyphZone.CENTER -> "Center"
    CustomGlyphZone.BOTTOM_LEFT -> "Bottom L"
    CustomGlyphZone.BOTTOM_CENTER -> "Bottom C"
    CustomGlyphZone.BOTTOM_RIGHT -> "Bottom R"
    CustomGlyphZone.ALL -> "All"
}

private fun GlyphSoundStyle.label(): String = when (this) {
    GlyphSoundStyle.SILENT -> "Silent"
    GlyphSoundStyle.SOFT_TICK -> "Soft"
    GlyphSoundStyle.MECHANICAL -> "Mechanical"
    GlyphSoundStyle.RING_PULSE -> "Ring"
    GlyphSoundStyle.GLITCH -> "Glitch"
}

private fun GlyphFrameSound.label(): String = when (this) {
    GlyphFrameSound.FOLLOW_PATTERN -> "Follow"
    GlyphFrameSound.MUTED -> "Muted"
    GlyphFrameSound.TICK -> "Tick"
    GlyphFrameSound.PULSE -> "Pulse"
    GlyphFrameSound.CHIME -> "Chime"
}
