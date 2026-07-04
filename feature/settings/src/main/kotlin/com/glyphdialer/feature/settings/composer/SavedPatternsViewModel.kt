// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.domain.model.CustomGlyphPattern
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SavedPatternsViewModel @Inject constructor(
    repository: CustomGlyphPatternRepository
) : ViewModel() {

    val patterns: StateFlow<List<CustomGlyphPattern>> = repository.observeAllPatterns()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
