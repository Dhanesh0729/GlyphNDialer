// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.repository.BlockedNumberRepository
import com.glyphdialer.core.domain.repository.CapabilityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the blocked-numbers sub-screen (BUILD_SPEC §8/§21; CONVENTIONS.md §5).
 *
 * Observes the block list from [BlockedNumberRepository] and the default-dialer role
 * from [CapabilityRepository] (writes to the platform blocklist require it — §9).
 * All writes return [AppResult] and are folded into state rather than thrown.
 */
@HiltViewModel
class BlockedNumbersViewModel @Inject constructor(
    private val blockedNumberRepository: BlockedNumberRepository,
    private val capabilityRepository: CapabilityRepository,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedNumbersUiState())
    val uiState: StateFlow<BlockedNumbersUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    private fun observe() {
        blockedNumberRepository.observeBlocked()
            .flowOn(ioDispatcher)
            .catch { t ->
                Timber.e(t, "Failed observing blocked numbers")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Couldn't load blocked numbers")
                }
            }
            .onEach { list ->
                _uiState.update { it.copy(blocked = list, isLoading = false) }
            }
            .launchIn(viewModelScope)

        capabilityRepository.capabilities
            .map { it.isDefaultDialer }
            .flowOn(ioDispatcher)
            .catch { t -> Timber.w(t, "Failed observing default-dialer capability") }
            .onEach { isDefault -> _uiState.update { it.copy(isDefaultDialer = isDefault) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: BlockedNumbersEvent) {
        when (event) {
            is BlockedNumbersEvent.DraftChanged ->
                _uiState.update { it.copy(draftNumber = event.value) }

            is BlockedNumbersEvent.Add -> onAdd(event.reportAsSpam)
            is BlockedNumbersEvent.Unblock -> onUnblock(event.number)
            BlockedNumbersEvent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun onAdd(reportAsSpam: Boolean) {
        val number = _uiState.value.draftNumber.trim()
        if (number.isBlank() || _uiState.value.isAdding) return

        if (!_uiState.value.isDefaultDialer) {
            _uiState.update {
                it.copy(errorMessage = "Glyph Dialer must be your default phone app to block numbers.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true) }
            when (val result = blockedNumberRepository.block(number, reportAsSpam)) {
                is AppResult.Success ->
                    // The observeBlocked() stream re-emits the updated list.
                    _uiState.update { it.copy(isAdding = false, draftNumber = "") }

                is AppResult.Failure -> {
                    Timber.e(result.error, "Failed to block %s", number)
                    _uiState.update {
                        it.copy(
                            isAdding = false,
                            errorMessage = result.message ?: "Couldn't block that number",
                        )
                    }
                }
            }
        }
    }

    private fun onUnblock(number: String) {
        viewModelScope.launch {
            val result = blockedNumberRepository.unblock(number)
            if (result is AppResult.Failure) {
                Timber.e(result.error, "Failed to unblock %s", number)
                _uiState.update {
                    it.copy(errorMessage = result.message ?: "Couldn't unblock that number")
                }
            }
        }
    }

    /** One-shot re-resolve of the role (e.g. on first open) so the UI gates correctly. */
    fun refreshRole() {
        viewModelScope.launch {
            val isDefault = capabilityRepository.current()
                .let { if (it is AppResult.Success) it.data.isDefaultDialer else null }
            if (isDefault != null) {
                _uiState.update { it.copy(isDefaultDialer = isDefault) }
            }
        }
    }
}
