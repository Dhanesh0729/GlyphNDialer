// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.flatMap
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import com.glyphdialer.feature.contacts.navigation.ContactsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Contact Create/Edit screen (BUILD_SPEC §8 — write via
 * ContactsContract; CONVENTIONS.md §5).
 *
 * In edit mode (an optional [ContactsRoutes.ARG_LOOKUP_KEY] nav arg is present) it
 * loads the existing contact's fields; in create mode it starts blank. Saving routes
 * through [ContactsRepository] — which performs the actual ContactsContract writes —
 * and returns the new/updated [Contact.lookupKey] so the host can land on the detail.
 *
 * Numbers are normalized to E.164 via the injected [PhoneNumberFormatter] when
 * possible (falling back to the raw input) before persisting. All fallible work
 * returns [AppResult]; failures are surfaced as errors, never thrown across layers.
 */
@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val formatter: PhoneNumberFormatter,
    // IO dispatcher injected for parity with the §5 convention; the repository owns
    // its own dispatching for the ContactsContract writes, so it is reserved here.
    @Suppress("unused") @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val editingLookupKey: String? =
        savedStateHandle.get<String>(ContactsRoutes.ARG_LOOKUP_KEY)?.let(Uri::decode)

    private val _uiState = MutableStateFlow(ContactEditUiState(isEditing = editingLookupKey != null))
    val uiState: StateFlow<ContactEditUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ContactEditEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        if (editingLookupKey != null) loadExisting(editingLookupKey)
    }

    fun onEvent(event: ContactEditEvent) {
        when (event) {
            is ContactEditEvent.NameChanged -> _uiState.update { it.copy(displayName = event.name) }
            is ContactEditEvent.NumberChanged -> updateNumber(event.id) { it.copy(value = event.value) }
            is ContactEditEvent.LabelChanged -> updateNumber(event.id) { it.copy(label = event.label) }
            ContactEditEvent.AddNumber -> _uiState.update { it.copy(numbers = it.numbers + NumberDraft()) }
            is ContactEditEvent.RemoveNumber -> removeNumber(event.id)
            ContactEditEvent.Save -> save()
            ContactEditEvent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    // ---- Load (edit mode) ---------------------------------------------------

    private fun loadExisting(lookupKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // observeContact emits the current value first; take it once for the form.
            val contact = runCatching { contactsRepository.observeContact(lookupKey).first() }
                .onFailure { Timber.w(it, "Failed loading contact %s for edit", lookupKey) }
                .getOrNull()

            if (contact == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't load this contact.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    displayName = contact.displayName,
                    numbers = contact.numbers
                        .map { n -> NumberDraft(value = n.formatted.ifBlank { n.raw }, label = n.label) }
                        .ifEmpty { listOf(NumberDraft()) },
                )
            }
        }
    }

    // ---- Edits --------------------------------------------------------------

    private inline fun updateNumber(id: Long, transform: (NumberDraft) -> NumberDraft) {
        _uiState.update { state ->
            state.copy(numbers = state.numbers.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun removeNumber(id: Long) {
        _uiState.update { state ->
            val remaining = state.numbers.filterNot { it.id == id }
            // Always keep at least one (blank) row so the form is never empty.
            state.copy(numbers = remaining.ifEmpty { listOf(NumberDraft()) })
        }
    }

    // ---- Save ---------------------------------------------------------------

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(errorMessage = "Enter a name and at least one number.") }
            return
        }

        val name = state.displayName.trim()
        val drafts = state.numbers.filter { it.value.isNotBlank() }
        val phoneNumbers = drafts.map { draft ->
            val raw = draft.value.trim()
            PhoneNumber(
                raw = raw,
                normalized = formatter.toE164(raw),
                formatted = formatter.format(raw),
                label = draft.label,
                isPrimary = draft === drafts.first(),
            )
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result =
                if (editingLookupKey != null) updateExisting(editingLookupKey, name, phoneNumbers)
                else createNew(name, phoneNumbers)

            when (result) {
                is AppResult.Success -> {
                    _effects.send(ContactEditEffect.ShowMessage(if (state.isEditing) "Contact updated" else "Contact created"))
                    _effects.send(ContactEditEffect.Saved(result.data))
                }
                is AppResult.Failure -> {
                    Timber.w(result.error, "Save contact failed: ${result.message}")
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = result.message ?: "Couldn't save this contact.")
                    }
                }
            }
        }
    }

    /**
     * Create via [ContactsRepository.createContact] (whose contract takes the primary
     * number), then, when there are additional numbers/labels, follow up with
     * [ContactsRepository.updateContact] to persist the full set. The returned lookup
     * key from create identifies the new contact for the second write.
     */
    private suspend fun createNew(name: String, numbers: List<PhoneNumber>): AppResult<String> {
        val primary = numbers.first()
        return contactsRepository.createContact(name, primary.dialValue).flatMap { lookupKey ->
            if (numbers.size == 1) {
                AppResult.Success(lookupKey)
            } else {
                val contact = Contact(
                    id = 0L,
                    lookupKey = lookupKey,
                    displayName = name,
                    numbers = numbers,
                )
                contactsRepository.updateContact(contact).flatMap { AppResult.Success(lookupKey) }
            }
        }
    }

    private suspend fun updateExisting(
        lookupKey: String,
        name: String,
        numbers: List<PhoneNumber>,
    ): AppResult<String> {
        // Preserve provider identity fields (id/photo/account) from the live contact.
        val existing = runCatching { contactsRepository.observeContact(lookupKey).first() }.getOrNull()
        val contact = Contact(
            id = existing?.id ?: 0L,
            lookupKey = lookupKey,
            displayName = name,
            numbers = numbers,
            photoUri = existing?.photoUri,
            thumbnailUri = existing?.thumbnailUri,
            isFavorite = existing?.isFavorite ?: false,
            accountType = existing?.accountType,
            sendToVoicemail = existing?.sendToVoicemail ?: false,
        )
        return contactsRepository.updateContact(contact).flatMap { AppResult.Success(lookupKey) }
    }
}
