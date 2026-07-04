// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssignPatternUiState(
    val isLoading: Boolean = true,
    val contacts: List<Contact> = emptyList(),
    val selectedContactKeys: Set<String> = emptySet()
)

@HiltViewModel
class AssignPatternViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val glyphRepository: CustomGlyphPatternRepository
) : ViewModel() {

    private val patternId: String = savedStateHandle.get<String>("patternId") ?: ""

    private val _uiState = MutableStateFlow(AssignPatternUiState())
    val uiState: StateFlow<AssignPatternUiState> = _uiState.asStateFlow()

    init {
        loadContactsAndAssignments()
    }

    private fun loadContactsAndAssignments() {
        viewModelScope.launch {
            val contactsList = contactsRepository.observeContacts().first()
            val selectedKeys = if (patternId.isNotBlank()) {
                glyphRepository.getContactKeysForPattern(patternId)
            } else {
                emptySet()
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    contacts = contactsList,
                    selectedContactKeys = selectedKeys,
                )
            }
        }
    }

    fun toggleSelection(lookupKey: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedContactKeys.contains(lookupKey)) {
                state.selectedContactKeys - lookupKey
            } else {
                state.selectedContactKeys + lookupKey
            }
            state.copy(selectedContactKeys = newSelection)
        }
    }

    fun saveAssignments() {
        if (patternId.isEmpty()) return
        val selectedKeys = _uiState.value.selectedContactKeys
        viewModelScope.launch {
            glyphRepository.replaceAssignments(patternId, selectedKeys)
        }
    }
}
