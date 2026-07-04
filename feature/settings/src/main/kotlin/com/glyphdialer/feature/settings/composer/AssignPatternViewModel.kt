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
            
            // To simplify for now, we just load all contacts.
            // Ideally we should pre-select the contacts that already have this patternId.
            // Since our DAO doesn't have a getContactsForPattern method yet, we'll start with empty selection.
            
            _uiState.update { it.copy(isLoading = false, contacts = contactsList) }
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
            selectedKeys.forEach { lookupKey ->
                glyphRepository.assignPatternToContact(patternId, lookupKey)
            }
        }
    }
}
