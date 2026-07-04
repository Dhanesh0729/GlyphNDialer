// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.model.CustomGlyphFrame
import com.glyphdialer.core.domain.model.CustomGlyphPattern
import com.glyphdialer.core.domain.model.CustomGlyphZone
import com.glyphdialer.core.domain.model.GlyphFrameSound
import com.glyphdialer.core.domain.model.GlyphSoundStyle
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class GlyphComposerUiState(
    val patternName: String = "New Glyph Pattern",
    val frames: List<CustomGlyphFrame> = emptyList(),
    val selectedZones: Set<CustomGlyphZone> = setOf(CustomGlyphZone.ALL),
    val intensity: Float = 0.8f,
    val durationMs: Int = 220,
    val soundStyle: GlyphSoundStyle = GlyphSoundStyle.SOFT_TICK,
    val frameSound: GlyphFrameSound = GlyphFrameSound.FOLLOW_PATTERN,
    val repeatCount: Int = 1,
    val glyphAvailable: Boolean = false,
    val isSaving: Boolean = false,
    val isPreviewing: Boolean = false,
    val message: String? = null,
) {
    val totalDurationMs: Int get() = frames.sumOf { it.safeDurationMs } * repeatCount.coerceAtLeast(1)

    val canSave: Boolean get() = frames.isNotEmpty() && patternName.isNotBlank() && !isSaving

    val canAddFrame: Boolean get() = selectedZones.isNotEmpty() && durationMs > 0
}

@HiltViewModel
class GlyphComposerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val patternRepository: CustomGlyphPatternRepository,
    private val capabilityRepository: CapabilityRepository,
    private val glyphController: GlyphController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlyphComposerUiState())
    val uiState: StateFlow<GlyphComposerUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        capabilityRepository.capabilities
            .onEach { capabilities ->
                _uiState.update { it.copy(glyphAvailable = capabilities.glyphAvailable) }
            }
            .catch { t -> Timber.w(t, "Failed observing Glyph composer capabilities") }
            .launchIn(viewModelScope)
    }

    fun updatePatternName(name: String) {
        _uiState.update { it.copy(patternName = name.take(MAX_NAME_LENGTH), message = null) }
    }

    fun toggleZone(zone: CustomGlyphZone) {
        _uiState.update { state ->
            val next = when {
                zone == CustomGlyphZone.ALL && CustomGlyphZone.ALL !in state.selectedZones -> setOf(CustomGlyphZone.ALL)
                zone == CustomGlyphZone.ALL -> emptySet()
                zone in state.selectedZones -> state.selectedZones - zone
                else -> (state.selectedZones - CustomGlyphZone.ALL) + zone
            }
            state.copy(selectedZones = next, message = null)
        }
    }

    fun updateIntensity(value: Float) {
        _uiState.update { it.copy(intensity = value.coerceIn(0.05f, 1f), message = null) }
    }

    fun updateDuration(valueMs: Int) {
        _uiState.update {
            it.copy(
                durationMs = valueMs.coerceIn(
                    CustomGlyphFrame.MIN_DURATION_MS,
                    CustomGlyphFrame.MAX_DURATION_MS,
                ),
                message = null,
            )
        }
    }

    fun updateSoundStyle(style: GlyphSoundStyle) {
        _uiState.update { it.copy(soundStyle = style, message = null) }
    }

    fun updateFrameSound(sound: GlyphFrameSound) {
        _uiState.update { it.copy(frameSound = sound, message = null) }
    }

    fun updateRepeatCount(count: Int) {
        _uiState.update { it.copy(repeatCount = count.coerceIn(1, 8), message = null) }
    }

    fun addFrame() {
        _uiState.update { state ->
            if (!state.canAddFrame) return@update state.copy(message = "Select at least one Glyph zone.")
            val frame = CustomGlyphFrame(
                zones = state.selectedZones.normalizedZones(),
                intensity = state.intensity,
                durationMs = state.durationMs,
                soundCue = state.frameSound,
            )
            state.copy(frames = state.frames + frame, message = null)
        }
    }

    fun removeFrame(index: Int) {
        _uiState.update { state ->
            if (index !in state.frames.indices) return@update state
            state.copy(frames = state.frames.filterIndexed { i, _ -> i != index }, message = null)
        }
    }

    fun duplicateFrame(index: Int) {
        _uiState.update { state ->
            val frame = state.frames.getOrNull(index) ?: return@update state
            state.copy(
                frames = state.frames.toMutableList().apply { add(index + 1, frame) },
                message = null,
            )
        }
    }

    fun moveFrame(index: Int, direction: Int) {
        _uiState.update { state ->
            val target = index + direction
            if (index !in state.frames.indices || target !in state.frames.indices) return@update state
            val next = state.frames.toMutableList()
            val frame = next.removeAt(index)
            next.add(target, frame)
            state.copy(frames = next, message = null)
        }
    }

    fun previewPattern() {
        val pattern = currentPatternOrNull() ?: run {
            _uiState.update { it.copy(message = "Add at least one frame before preview.") }
            return
        }
        runCatching { glyphController.previewCustomPattern(pattern) }
            .onFailure { Timber.w(it, "Glyph custom preview failed") }

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isPreviewing = true, message = null) }
            try {
                playLocalFeedback(pattern)
            } finally {
                _uiState.update { it.copy(isPreviewing = false) }
            }
        }
    }

    fun savePattern(onComplete: () -> Unit) {
        val pattern = currentPatternOrNull() ?: run {
            _uiState.update { it.copy(message = "Add at least one frame before saving.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching { patternRepository.savePattern(pattern) }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                    onComplete()
                }
                .onFailure { t ->
                    Timber.w(t, "Failed saving custom Glyph pattern")
                    _uiState.update {
                        it.copy(isSaving = false, message = "Couldn't save this pattern.")
                    }
                }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun currentPatternOrNull(): CustomGlyphPattern? {
        val state = _uiState.value
        if (!state.canSave && state.frames.isEmpty()) return null
        return CustomGlyphPattern(
            name = state.patternName.trim().ifBlank { "Glyph Pattern" },
            frames = state.frames,
            soundStyle = state.soundStyle,
            repeatCount = state.repeatCount,
        )
    }

    private suspend fun playLocalFeedback(pattern: CustomGlyphPattern) {
        if (pattern.soundStyle == GlyphSoundStyle.SILENT) {
            delay(pattern.durationMs.toLong())
            return
        }
        val tone = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, previewVolume(pattern.soundStyle))
        }.getOrNull()
        try {
            repeat(pattern.repeatCount.coerceIn(1, 8)) {
                for (frame in pattern.frames) {
                    playFrameCue(tone, pattern.soundStyle, frame)
                    vibrate(frame, pattern.soundStyle)
                    delay(frame.safeDurationMs.toLong())
                }
                delay(80)
            }
        } finally {
            tone?.release()
        }
    }

    private fun playFrameCue(
        tone: ToneGenerator?,
        style: GlyphSoundStyle,
        frame: CustomGlyphFrame,
    ) {
        if (tone == null || frame.soundCue == GlyphFrameSound.MUTED) return
        val cue = when (frame.soundCue) {
            GlyphFrameSound.FOLLOW_PATTERN -> when (style) {
                GlyphSoundStyle.SILENT -> return
                GlyphSoundStyle.SOFT_TICK -> ToneGenerator.TONE_PROP_ACK
                GlyphSoundStyle.MECHANICAL -> ToneGenerator.TONE_DTMF_5
                GlyphSoundStyle.RING_PULSE -> ToneGenerator.TONE_DTMF_0
                GlyphSoundStyle.GLITCH -> ToneGenerator.TONE_PROP_NACK
            }
            GlyphFrameSound.MUTED -> return
            GlyphFrameSound.TICK -> ToneGenerator.TONE_PROP_ACK
            GlyphFrameSound.PULSE -> ToneGenerator.TONE_DTMF_9
            GlyphFrameSound.CHIME -> ToneGenerator.TONE_PROP_BEEP
        }
        runCatching { tone.startTone(cue, frame.safeDurationMs.coerceAtMost(220)) }
    }

    private fun vibrate(frame: CustomGlyphFrame, style: GlyphSoundStyle) {
        val vibrator = defaultVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val duration = when (style) {
            GlyphSoundStyle.SILENT -> 0L
            GlyphSoundStyle.SOFT_TICK -> 16L
            GlyphSoundStyle.MECHANICAL -> 28L
            GlyphSoundStyle.RING_PULSE -> 40L
            GlyphSoundStyle.GLITCH -> 12L
        }
        if (duration <= 0L) return
        val amplitude = (frame.safeIntensity * 190f).toInt().coerceIn(24, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun defaultVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun previewVolume(style: GlyphSoundStyle): Int = when (style) {
        GlyphSoundStyle.SILENT -> 0
        GlyphSoundStyle.SOFT_TICK -> 35
        GlyphSoundStyle.MECHANICAL -> 55
        GlyphSoundStyle.RING_PULSE -> 65
        GlyphSoundStyle.GLITCH -> 45
    }

    private fun Set<CustomGlyphZone>.normalizedZones(): List<CustomGlyphZone> {
        if (isEmpty()) return emptyList()
        if (CustomGlyphZone.ALL in this) return listOf(CustomGlyphZone.ALL)
        return toList().sortedBy { it.ordinal }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
