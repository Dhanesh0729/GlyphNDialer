// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.usecase.ObservePreferencesUseCase
import com.glyphdialer.core.domain.usecase.UpdatePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Settings screen (BUILD_SPEC §21; CONVENTIONS.md §5).
 *
 * Responsibilities:
 *  - Observe [UserPreferences] via [ObservePreferencesUseCase] and the live
 *    [com.glyphdialer.core.domain.model.CapabilityFlags] via [CapabilityRepository],
 *    merging both into a single immutable [SettingsUiState].
 *  - Translate each [SettingsEvent] into an atomic preference transform via
 *    [UpdatePreferencesUseCase] (no read-modify-write races).
 *  - Honor the §9 honesty principle: capability gating lives in the *state* (which
 *    groups/controls are offered) and the ViewModel never persists a setting that
 *    would imply an unsupported capability beyond the user's stored preference.
 *  - Delegate navigation that leaves the feature (speed-dial, storage/export, OSS
 *    licenses) to the host via [SettingsEffect] — staying inside the dependency
 *    graph (§3).
 *
 * Dispatchers are injected; repository calls return [AppResult] and are folded into
 * state rather than thrown.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observePreferences: ObservePreferencesUseCase,
    private val updatePreferences: UpdatePreferencesUseCase,
    private val capabilityRepository: CapabilityRepository,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observe()
        refreshCapabilities()
    }

    private fun observe() {
        observePreferences()
            .flowOn(ioDispatcher)
            .catch { t ->
                Timber.e(t, "Failed observing preferences")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Couldn't load settings")
                }
            }
            .onEach { prefs ->
                _uiState.update { it.copy(preferences = prefs, isLoading = false) }
            }
            .launchIn(viewModelScope)

        capabilityRepository.capabilities
            .flowOn(ioDispatcher)
            .catch { t -> Timber.w(t, "Failed observing capabilities") }
            .onEach { caps ->
                _uiState.update { it.copy(capabilities = caps) }
            }
            .launchIn(viewModelScope)
    }

    /** Force a one-shot capability re-evaluation (e.g. after returning from a role grant). */
    private fun refreshCapabilities() {
        viewModelScope.launch {
            when (val result = capabilityRepository.refresh()) {
                is AppResult.Success ->
                    _uiState.update { it.copy(capabilities = result.data) }

                is AppResult.Failure ->
                    Timber.w(result.error, "Capability refresh failed; using last snapshot")
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            // ---- Appearance ----------------------------------------------------------
            is SettingsEvent.SetThemeMode -> mutate { it.copy(themeMode = event.mode) }
            is SettingsEvent.SetAppFont -> mutate { it.copy(appFont = event.font) }
            is SettingsEvent.SetAccent -> mutate { it.copy(accentColor = event.accent) }

            // ---- Glyph ---------------------------------------------------------------
            is SettingsEvent.SetGlyphMasterEnabled -> mutate { it.copy(glyphMasterEnabled = event.enabled) }
            is SettingsEvent.SetGlyphDialpadStrokes -> mutate { it.copy(glyphDialpadStrokes = event.enabled) }
            is SettingsEvent.SetGlyphIntensity ->
                mutate { it.copy(glyphIntensity = event.intensity.coerceIn(0f, 1f)) }
            is SettingsEvent.SetGlyphIncomingShow -> mutate { it.copy(glyphIncomingShow = event.enabled) }
            is SettingsEvent.SetGlyphRecordingIndicator ->
                mutate { it.copy(glyphRecordingIndicator = event.enabled) }

            // ---- Calls ---------------------------------------------------------------
            is SettingsEvent.SetDefaultSim -> mutate { it.copy(defaultSimSubscriptionId = event.subscriptionId) }
            is SettingsEvent.SetCallerIdSpam -> mutate { it.copy(callerIdSpamEnabled = event.enabled) }

            // ---- Recording -----------------------------------------------------------
            is SettingsEvent.SetRecordingEnabled -> onSetRecordingEnabled(event.enabled)
            is SettingsEvent.SetNoAnnouncement -> mutate { it.copy(recordingNoAnnouncement = event.enabled) }
            is SettingsEvent.SetRetentionWindow -> mutate { it.copy(retentionWindow = event.window) }

            // ---- Transcription -------------------------------------------------------
            is SettingsEvent.SetTranscriptionEngine -> mutate { it.copy(transcriptionEngine = event.engine) }
            is SettingsEvent.SetLiveCaptions -> onSetLiveCaptions(event.enabled)
            is SettingsEvent.SetAutoTranscribe -> mutate { it.copy(autoTranscribe = event.enabled) }
            is SettingsEvent.SetTranscriptionLanguage ->
                mutate { it.copy(transcriptionLanguage = event.bcp47?.takeIf { tag -> tag.isNotBlank() }) }

            // ---- Contacts ------------------------------------------------------------
            is SettingsEvent.SetContactsAccountFilter ->
                mutate { it.copy(contactsAccountFilter = event.accountType?.takeIf { a -> a.isNotBlank() }) }

            // ---- Navigation requests -------------------------------------------------
            SettingsEvent.OpenBlockedNumbers -> emit(SettingsEffect.NavigateToBlockedNumbers)
            SettingsEvent.OpenAbout -> emit(SettingsEffect.NavigateToAbout)
            SettingsEvent.OpenSpeedDial -> emit(SettingsEffect.OpenSpeedDial)
            SettingsEvent.OpenStorageExport -> emit(SettingsEffect.OpenStorageExport)
            SettingsEvent.OpenOpenSourceLicenses -> emit(SettingsEffect.OpenOpenSourceLicenses)

            // ---- Misc ----------------------------------------------------------------
            SettingsEvent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * Enabling recording is a no-op preference if the device can't honestly record at
     * all (§9/§12): we still store the user's *intent*, but warn them that no tier is
     * available so the UI/Glyph never imply recording is happening.
     */
    private fun onSetRecordingEnabled(enabled: Boolean) {
        if (enabled && !_uiState.value.recordingPossible) {
            emit(
                SettingsEffect.ShowMessage(
                    "Recording isn't supported on this device. The setting is saved but " +
                        "no audio can be captured until a supported tier is available.",
                ),
            )
        }
        mutate { it.copy(recordingEnabled = enabled) }
    }

    /**
     * Live captions need on-device speech recognition; honor the honesty principle by
     * telling the user when it isn't available rather than silently failing (§9/§13).
     */
    private fun onSetLiveCaptions(enabled: Boolean) {
        if (enabled && !_uiState.value.liveCaptionsOfferable) {
            emit(
                SettingsEffect.ShowMessage(
                    "On-device speech recognition isn't available on this device, so live " +
                        "captions can't run. The preference is saved for when it is.",
                ),
            )
        }
        mutate { it.copy(liveCaptionsEnabled = enabled) }
    }

    /** Apply an atomic preference [transform]; surface a failure as a transient error. */
    private fun mutate(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            val result = updatePreferences(transform)
            if (result is AppResult.Failure) {
                Timber.e(result.error, "Failed to update preferences")
                _uiState.update {
                    it.copy(errorMessage = result.message ?: "Couldn't save that setting")
                }
            }
        }
    }

    private fun emit(effect: SettingsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }
}
