// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.domain.model.CustomGlyphFrame
import com.glyphdialer.core.domain.model.CustomGlyphPattern
import com.glyphdialer.core.domain.model.CustomGlyphZone
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GlyphComposerUiState(
    val patternName: String = "My Custom Pattern",
    val frames: List<CustomGlyphFrame> = emptyList(),
    val isSaving: Boolean = false
)

@HiltViewModel
class GlyphComposerViewModel @Inject constructor(
    private val patternRepository: CustomGlyphPatternRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlyphComposerUiState())
    val uiState: StateFlow<GlyphComposerUiState> = _uiState.asStateFlow()

    fun updatePatternName(name: String) {
        _uiState.update { it.copy(patternName = name) }
    }

    fun addFrame(zones: List<CustomGlyphZone>, intensity: Float, durationMs: Int) {
        _uiState.update { state ->
            state.copy(
                frames = state.frames + CustomGlyphFrame(zones, intensity, durationMs)
            )
        }
    }

    fun removeFrame(index: Int) {
        _uiState.update { state ->
            val newFrames = state.frames.toMutableList()
            if (index in newFrames.indices) {
                newFrames.removeAt(index)
            }
            state.copy(frames = newFrames)
        }
    }

    fun savePattern(onComplete: () -> Unit) {
        val currentState = _uiState.value
        if (currentState.frames.isEmpty()) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val pattern = CustomGlyphPattern(
                name = currentState.patternName,
                frames = currentState.frames
            )
            patternRepository.savePattern(pattern)
            _uiState.update { it.copy(isSaving = false) }
            onComplete()
        }
    }
}
