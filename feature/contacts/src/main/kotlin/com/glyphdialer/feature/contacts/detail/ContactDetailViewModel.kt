// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.onFailure
import com.glyphdialer.core.common.onSuccess
import com.glyphdialer.core.domain.model.SpeedDialSlot
import com.glyphdialer.core.domain.repository.CallLogRepository
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.FavoritesRepository
import com.glyphdialer.core.domain.repository.SpeedDialRepository
import com.glyphdialer.core.domain.usecase.ToggleFavoriteUseCase
import com.glyphdialer.feature.contacts.navigation.ContactsRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Contact Detail screen (BUILD_SPEC §8; CONVENTIONS.md §5).
 *
 * Resolves the contact for the [ContactsRoutes.ARG_LOOKUP_KEY] nav argument, observes
 * it live (ContentObserver-backed), and joins it with the favorite flag, the recent
 * call history, and any speed-dial assignments into a single [ContactDetailUiState].
 * Mutations (favorite toggle, set-default-number, speed-dial assign/clear, delete)
 * return [AppResult] and surface failures as errors/effects rather than throwing.
 *
 * The feature does not depend on :telecom, so calling/messaging is delegated up to
 * :app via [ContactDetailEffect].
 */
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val speedDialRepository: SpeedDialRepository,
    private val callLogRepository: CallLogRepository,
    private val capabilityRepository: CapabilityRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val lookupKey: String =
        Uri.decode(savedStateHandle.get<String>(ContactsRoutes.ARG_LOOKUP_KEY).orEmpty()).orEmpty()

    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ContactDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        if (lookupKey.isBlank()) {
            Timber.w("Contact detail opened without a lookup key")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Couldn't open this contact.",
                )
            }
        } else {
            observeContact()
            observeRecentInteractions()
            observeSpeedDial()
            observeVideoAvailability()
        }
    }

    fun onEvent(event: ContactDetailEvent) {
        when (event) {
            is ContactDetailEvent.CallNumber -> emit(ContactDetailEffect.PlaceCall(event.number))
            is ContactDetailEvent.VideoCallNumber -> emit(ContactDetailEffect.PlaceVideoCall(event.number))
            is ContactDetailEvent.MessageNumber -> emit(ContactDetailEffect.ComposeMessage(event.number))
            ContactDetailEvent.ToggleFavorite -> onToggleFavorite()
            is ContactDetailEvent.SetDefaultNumber -> onSetDefaultNumber(event.number)
            is ContactDetailEvent.OpenSpeedDial -> _uiState.update { it.copy(speedDialSheet = SpeedDialSheetTarget(event.number)) }
            is ContactDetailEvent.AssignSpeedDial -> onAssignSpeedDial(event.slot)
            is ContactDetailEvent.ClearSpeedDial -> onClearSpeedDial(event.slot)
            ContactDetailEvent.DismissSpeedDial -> _uiState.update { it.copy(speedDialSheet = null) }
            ContactDetailEvent.Edit -> emit(ContactDetailEffect.NavigateToEdit(lookupKey))
            ContactDetailEvent.Delete -> onDelete()
            ContactDetailEvent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    // ---- Observation --------------------------------------------------------

    private fun observeContact() {
        combine(
            contactsRepository.observeContact(lookupKey),
            favoritesRepository.isFavorite(lookupKey),
        ) { contact, favorite -> contact to favorite }
            .flowOn(ioDispatcher)
            .catch { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.e(t, "Failed observing contact %s", lookupKey)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Couldn't load this contact.") }
            }
            .onEach { (contact, favorite) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        contact = contact,
                        isFavorite = favorite,
                        // Preserve a user-chosen default; otherwise leave null → primary.
                        defaultNumber = it.defaultNumber ?: contact?.primaryNumber?.dialValue,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecentInteractions() {
        // Resolve the best number to query history against, reacting to contact load.
        uiState
            .map { it.contact?.primaryNumber?.dialValue }
            .distinctUntilChanged()
            .flatMapLatest { number ->
                if (number.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    callLogRepository.observeForNumber(number)
                }
            }
            .map { entries ->
                val now = System.currentTimeMillis()
                entries
                    .sortedByDescending { it.timestampMillis }
                    .take(RECENT_LIMIT)
                    .map { RecentInteraction.from(it, RelativeTime.format(it.timestampMillis, now)) }
            }
            .flowOn(defaultDispatcher)
            .catch { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Recent history is best-effort (gated by READ_CALL_LOG / default-dialer).
                Timber.w(t, "Failed observing recent interactions")
            }
            .onEach { interactions -> _uiState.update { it.copy(recentInteractions = interactions) } }
            .launchIn(viewModelScope)
    }

    private fun observeSpeedDial() {
        speedDialRepository.observeSlots()
            .flowOn(ioDispatcher)
            .catch { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.w(t, "Failed observing speed-dial slots")
            }
            .onEach { slots -> applySpeedDial(slots) }
            .launchIn(viewModelScope)
    }

    private fun observeVideoAvailability() {
        capabilityRepository.capabilities
            .flowOn(ioDispatcher)
            .catch { t ->
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.w(t, "Failed observing contact video availability")
            }
            .onEach { caps ->
                _uiState.update {
                    it.copy(videoCallAvailable = caps.voipAvailable && caps.cameraAvailable)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun applySpeedDial(slots: List<SpeedDialSlot>) {
        val occupied = slots.filter { it.number.isNotBlank() }.map { it.slot }.toSet()
        val myNumbers = _uiState.value.contact?.numbers?.map { it.dialValue }?.toSet().orEmpty()
        val mine = slots
            .filter { slot -> slot.contactLookupKey == lookupKey || slot.number in myNumbers }
            .associate { it.slot to it.number }
        _uiState.update { it.copy(occupiedSlots = occupied, assignedSpeedDialSlots = mine) }
    }

    // ---- Mutations ----------------------------------------------------------

    private fun onToggleFavorite() {
        val makeFavorite = !_uiState.value.isFavorite
        val number = _uiState.value.effectiveDefaultNumber
        if (makeFavorite && number.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Add a number before favoriting.") }
            return
        }
        viewModelScope.launch {
            toggleFavorite(lookupKey, makeFavorite, number)
                .onSuccess {
                    emit(ContactDetailEffect.ShowMessage(if (makeFavorite) "Added to favorites" else "Removed from favorites"))
                }
                .onFailure { failure ->
                    Timber.w(failure.error, "Favorite toggle failed: ${failure.message}")
                    _uiState.update { it.copy(errorMessage = failure.message ?: "Couldn't update favorite.") }
                }
        }
    }

    private fun onSetDefaultNumber(number: String) {
        // Optimistically reflect the chosen default; persist to the contact provider.
        _uiState.update { it.copy(defaultNumber = number) }
        viewModelScope.launch {
            contactsRepository.setDefaultNumber(lookupKey, number)
                .onFailure { failure ->
                    Timber.w(failure.error, "setDefaultNumber failed: ${failure.message}")
                    _uiState.update { it.copy(errorMessage = failure.message ?: "Couldn't set default number.") }
                }
            // If favorited, keep the favorite's dialed number in sync with the default.
            if (_uiState.value.isFavorite) {
                favoritesRepository.setDefaultNumber(lookupKey, number).onFailure { failure ->
                    Timber.w(failure.error, "favorite setDefaultNumber failed: ${failure.message}")
                }
            }
        }
    }

    private fun onAssignSpeedDial(slot: Int) {
        val number = _uiState.value.speedDialSheet?.number?.dialValue ?: return
        if (slot !in SpeedDialSlot.MIN_SLOT..SpeedDialSlot.MAX_SLOT) {
            _uiState.update { it.copy(errorMessage = "Speed-dial keys 2–9 only.", speedDialSheet = null) }
            return
        }
        viewModelScope.launch {
            speedDialRepository.assign(slot, lookupKey, number)
                .onSuccess {
                    emit(ContactDetailEffect.ShowMessage("Assigned to speed-dial $slot"))
                    _uiState.update { it.copy(speedDialSheet = null) }
                }
                .onFailure { failure ->
                    Timber.w(failure.error, "Speed-dial assign failed: ${failure.message}")
                    _uiState.update {
                        it.copy(errorMessage = failure.message ?: "Couldn't assign speed-dial.", speedDialSheet = null)
                    }
                }
        }
    }

    private fun onClearSpeedDial(slot: Int) {
        viewModelScope.launch {
            speedDialRepository.clear(slot)
                .onSuccess { emit(ContactDetailEffect.ShowMessage("Cleared speed-dial $slot")) }
                .onFailure { failure ->
                    Timber.w(failure.error, "Speed-dial clear failed: ${failure.message}")
                    _uiState.update { it.copy(errorMessage = failure.message ?: "Couldn't clear speed-dial.") }
                }
        }
    }

    private fun onDelete() {
        viewModelScope.launch {
            contactsRepository.deleteContact(lookupKey)
                .onSuccess { emit(ContactDetailEffect.NavigateUpAfterDelete) }
                .onFailure { failure ->
                    Timber.w(failure.error, "Delete contact failed: ${failure.message}")
                    _uiState.update { it.copy(errorMessage = failure.message ?: "Couldn't delete this contact.") }
                }
        }
    }

    private fun emit(effect: ContactDetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        private const val RECENT_LIMIT = 10
    }
}
