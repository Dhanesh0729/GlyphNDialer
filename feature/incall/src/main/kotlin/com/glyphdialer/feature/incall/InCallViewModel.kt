// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.glyph.CallVisual
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.CapabilityFlags
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.repository.RecordingRepository
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.core.domain.repository.TelecomRepository
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import com.glyphdialer.core.domain.usecase.AnswerCallUseCase
import com.glyphdialer.core.domain.usecase.EndCallUseCase
import com.glyphdialer.core.domain.usecase.MergeConferenceUseCase
import com.glyphdialer.core.domain.usecase.RecordCallUseCase
import com.glyphdialer.core.domain.usecase.SendDtmfUseCase
import com.glyphdialer.core.domain.usecase.SetAudioRouteUseCase
import com.glyphdialer.core.domain.usecase.SplitFromConferenceUseCase
import com.glyphdialer.core.domain.usecase.SwapCallUseCase
import com.glyphdialer.core.domain.usecase.ToggleHoldUseCase
import com.glyphdialer.core.domain.usecase.ToggleMuteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the AOD-minimal in-call screen (BUILD_SPEC §9/§10/§11/§18).
 *
 * Observes [TelecomRepository] (calls + audio), [RecordingRepository] (tier + active),
 * [CapabilityRepository] (honest availability — §9) and [SettingsRepository] (captions
 * preference), composing them into a single [InCallUiState]. Controls are reduced via
 * the §6 use cases. Glyph choreography is mirrored opportunistically through the
 * injected [GlyphController] (a no-op on non-Nothing hardware — §17.6).
 *
 * HONESTY PRINCIPLE: the recording tier and caption/video availability surfaced here
 * come from the repositories/capability flags — the ViewModel never fabricates an
 * unsupported capability (§2.1/§2.3/§2.4).
 */
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val telecom: TelecomRepository,
    private val recordings: RecordingRepository,
    private val capabilities: CapabilityRepository,
    private val settings: SettingsRepository,
    private val transcription: TranscriptionEngine,
    private val glyph: GlyphController,
    private val toggleMute: ToggleMuteUseCase,
    private val setAudioRoute: SetAudioRouteUseCase,
    private val toggleHold: ToggleHoldUseCase,
    private val sendDtmf: SendDtmfUseCase,
    private val mergeConference: MergeConferenceUseCase,
    private val swapCall: SwapCallUseCase,
    private val splitFromConference: SplitFromConferenceUseCase,
    private val endCall: EndCallUseCase,
    private val answerCall: AnswerCallUseCase,
    private val recordCall: RecordCallUseCase,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** UI-owned slice: caption toggle, dtmf overlay, error, and the ticking clock. */
    private val uiControl = MutableStateFlow(UiControl())

    private val _effects = Channel<InCallEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Caption collection job; started/stopped when the user toggles captions. */
    private var captionsJob: Job? = null

    /** Last Glyph visual we pushed, to avoid redundant choreography calls. */
    private var lastGlyphVisual: CallVisual? = null

    val uiState: StateFlow<InCallUiState> =
        combine(
            listOf(
                telecom.calls,
                telecom.audioState,
                recordings.isRecording,
                recordings.activeTier,
                capabilities.capabilities,
                settings.preferences,
                uiControl,
            ),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            buildState(
                calls = values[0] as List<CallModel>,
                audio = values[1] as AudioState,
                isRecording = values[2] as Boolean,
                activeTier = values[3] as RecordingTier,
                caps = values[4] as CapabilityFlags,
                liveCaptionsPref = (values[5] as UserPreferences).liveCaptionsEnabled,
                control = values[6] as UiControl,
            )
        }
            .distinctUntilChanged()
            .onEach { reactToState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = InCallUiState(isLoading = true),
            )

    init {
        // Tick the wall-clock so the MonoTimer updates without re-querying telecom.
        viewModelScope.launch(defaultDispatcher) {
            while (isActive) {
                uiControl.update { it.copy(nowMillis = System.currentTimeMillis()) }
                kotlinx.coroutines.delay(TIMER_TICK_MS)
            }
        }
        // Resolve the honest recording tier for the foregrounded call as it changes.
        telecom.primaryCall
            .map { it?.id }
            .distinctUntilChanged()
            .onEach { resolveTierForPrimary() }
            .launchIn(viewModelScope)
    }

    // ------------------------------------------------------------------------
    // Event reduction
    // ------------------------------------------------------------------------

    fun onEvent(event: InCallEvent) {
        val state = uiState.value
        when (event) {
            InCallEvent.Answer -> primaryId(state)?.let { runAction { answerCall(it) } }
            InCallEvent.EndCall -> primaryId(state)?.let { runAction { endCall(it) } }
            is InCallEvent.Reject -> primaryId(state)?.let {
                runAction { telecom.rejectCall(it, event.message) }
            }

            InCallEvent.ToggleMute -> runAction { toggleMute(!state.isMuted) }
            is InCallEvent.SelectAudioRoute -> runAction { setAudioRoute(event.route) }

            InCallEvent.ToggleHold -> primaryId(state)?.let {
                val target = !(state.primaryCall?.isOnHold ?: false)
                runAction { toggleHold(it, target) }
            }

            is InCallEvent.ShowDtmf -> uiControl.update { it.copy(dtmfVisible = event.show) }
            is InCallEvent.Dtmf -> primaryId(state)?.let { id ->
                // Mirror the keystroke onto the Glyph (no-op off-Nothing).
                runCatching { glyph.playDigitStroke(event.digit) }
                runAction { sendDtmf(id, event.digit) }
            }

            InCallEvent.AddCall -> emitEffect(InCallEffect.NavigateToDialpad)

            InCallEvent.Merge -> {
                val a = state.primaryCall?.id
                val b = state.heldCall?.id
                if (a != null && b != null) runAction { mergeConference(a, b) }
                else fail("Need two calls to merge.")
            }

            InCallEvent.Swap -> primaryId(state)?.let { runAction { swapCall(it) } }

            is InCallEvent.SplitParticipant -> runAction { splitFromConference(event.callId) }
            is InCallEvent.HoldParticipant -> runAction { toggleHold(event.callId, event.hold) }
            is InCallEvent.DisconnectParticipant -> runAction { endCall(event.callId) }

            InCallEvent.ToggleRecording -> onToggleRecording(state)
            InCallEvent.ToggleCaptions -> onToggleCaptions(state)
            InCallEvent.ToggleVideo -> onToggleVideo(state)

            InCallEvent.ClearError -> uiControl.update { it.copy(errorMessage = null) }
        }
    }

    private fun onToggleRecording(state: InCallUiState) {
        val call = state.primaryCall ?: return fail("No active call to record.")
        viewModelScope.launch {
            val result: AppResult<*> = recordCall(call, start = !state.isRecording)
            if (result is AppResult.Failure) {
                fail(result.message ?: "Recording action failed.")
            } else {
                // Mirror the persistent recording indicator on the Glyph (§17.5).
                runCatching { glyph.showRecording(!state.isRecording) }
            }
        }
    }

    private fun onToggleCaptions(state: InCallUiState) {
        if (!state.captionsAvailable) {
            return fail("Live captions aren't available for this call.")
        }
        val turningOn = !state.captionsEnabled
        uiControl.update { it.copy(captionsEnabled = turningOn) }
        if (turningOn) startCaptions() else stopCaptions()
    }

    private fun onToggleVideo(state: InCallUiState) {
        val call = state.primaryCall ?: return
        if (!state.videoUpgradeAvailable) {
            // §2.3: carrier video is never available; only in-app VoIP can upgrade.
            return fail("Video is only available on in-app calls.")
        }
        runAction {
            if (state.isVideo) telecom.downgradeToAudio(call.id)
            else telecom.requestVideoUpgrade(call.id)
        }
    }

    // ------------------------------------------------------------------------
    // Live captions (§13) — honest, gated on engine availability + preference
    // ------------------------------------------------------------------------

    private fun startCaptions() {
        if (captionsJob?.isActive == true) return
        captionsJob = transcription.liveCaptions()
            .onEach { partial -> uiControl.update { it.copy(captionsText = partial) } }
            .launchIn(viewModelScope)
        Timber.d("Live captions started via %s", transcription.type)
    }

    private fun stopCaptions() {
        captionsJob?.cancel()
        captionsJob = null
        uiControl.update { it.copy(captionsText = "") }
    }

    // ------------------------------------------------------------------------
    // Glyph choreography + tier resolution side effects
    // ------------------------------------------------------------------------

    /** React to a freshly composed state: push Glyph choreography + auto-dismiss. */
    private fun reactToState(state: InCallUiState) {
        val visual = state.primaryCall?.toGlyphVisual()
        if (visual != null && visual != lastGlyphVisual) {
            lastGlyphVisual = visual
            runCatching { glyph.showOnCall(visual) }
        }
        // Mirror the latest amplitude (idle 0f keeps the Glyph dark honestly).
        runCatching { glyph.renderWaveform(state.amplitudes.lastOrNull() ?: 0f) }

        // When the last call has gone, dismiss the full-screen surface.
        if (!state.isLoading && !state.hasCall) {
            runCatching { glyph.showOnCall(CallVisual.ENDED) }
            emitEffect(InCallEffect.Dismiss)
        }
    }

    private fun resolveTierForPrimary() {
        val call = telecom.primaryCall.value ?: run {
            uiControl.update { it.copy(resolvedTier = RecordingTier.UNAVAILABLE) }
            return
        }
        viewModelScope.launch {
            val tier = runCatching { recordings.resolveTier(call) }
                .getOrDefault(RecordingTier.UNAVAILABLE)
            uiControl.update { it.copy(resolvedTier = tier) }
        }
    }

    // ------------------------------------------------------------------------
    // State composition
    // ------------------------------------------------------------------------

    private fun buildState(
        calls: List<CallModel>,
        audio: AudioState,
        isRecording: Boolean,
        activeTier: RecordingTier,
        caps: CapabilityFlags,
        liveCaptionsPref: Boolean,
        control: UiControl,
    ): InCallUiState {
        val byId = calls.associateBy { it.id }
        val primary = selectPrimary(calls)
        val held = calls.firstOrNull { it.id != primary?.id && it.state == CallState.HOLDING }
        val participants = primary
            ?.takeIf { it.isConference }
            ?.childCallIds
            ?.mapNotNull { byId[it] }
            .orEmpty()

        // Captions: honest gate — engine ready AND on-device speech capability AND a
        // user preference enabling them. VoIP gets full coverage; cellular is local-side
        // only (§2.4).
        val captionsAvailable = transcription.isAvailable &&
            caps.onDeviceSpeechAvailable &&
            liveCaptionsPref
        val captionsLocalSideOnly = primary?.isVoip != true

        // Video upgrade: in-app VoIP only, with a camera and the per-call capability (§2.3).
        val videoUpgradeAvailable = primary?.isVoip == true &&
            primary.capability.canUpgradeToVideo &&
            caps.cameraAvailable &&
            caps.voipAvailable

        return InCallUiState(
            primaryCall = primary,
            heldCall = held,
            conferenceParticipants = participants,
            audioState = audio,
            isMuted = audio.isMuted,
            isRecording = isRecording,
            recordingTier = if (control.resolvedTier.isAvailable) control.resolvedTier
            else caps.maxRecordingTier,
            activeRecordingTier = if (isRecording) activeTier else RecordingTier.UNAVAILABLE,
            captionsAvailable = captionsAvailable,
            captionsEnabled = control.captionsEnabled && captionsAvailable,
            captionsText = control.captionsText,
            captionsLocalSideOnly = captionsLocalSideOnly,
            videoUpgradeAvailable = videoUpgradeAvailable,
            isVideo = primary?.isVideo == true,
            amplitudes = control.amplitudes,
            nowMillis = control.nowMillis,
            isLoading = false,
            errorMessage = control.errorMessage,
        )
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun primaryId(state: InCallUiState): String? =
        state.primaryCall?.id ?: run { fail("No active call."); null }

    /** Run a fallible telecom action; surface a failure message without crashing. */
    private fun runAction(block: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Failure -> fail(result.message ?: "That action didn't work.")
                is AppResult.Success -> Unit
            }
        }
    }

    private fun fail(message: String) {
        Timber.w("In-call action failed: %s", message)
        uiControl.update { it.copy(errorMessage = message) }
        emitEffect(InCallEffect.ShowMessage(message))
    }

    private fun emitEffect(effect: InCallEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        super.onCleared()
        stopCaptions()
        // Clear any Glyph state we may have set; the controller no-ops off-Nothing.
        runCatching {
            glyph.showRecording(false)
            glyph.showOnCall(CallVisual.ENDED)
        }
    }

    /**
     * Mutable UI-owned slice combined into [InCallUiState]. Kept separate from the
     * telecom-derived state so user toggles don't get clobbered by repository emissions.
     */
    private data class UiControl(
        val captionsEnabled: Boolean = false,
        val captionsText: String = "",
        val dtmfVisible: Boolean = false,
        val resolvedTier: RecordingTier = RecordingTier.UNAVAILABLE,
        val amplitudes: List<Float> = emptyList(),
        val nowMillis: Long = System.currentTimeMillis(),
        val errorMessage: String? = null,
    )

    internal companion object {
        const val TIMER_TICK_MS: Long = 500L
        const val STOP_TIMEOUT_MS: Long = 5_000L

        /**
         * Choose the call the in-call UI should foreground. Pure for unit-testing:
         * incoming-ringing wins (so the user can answer), then dialing/connecting,
         * then a conference parent, then any active, then the first live call.
         */
        fun selectPrimary(calls: List<CallModel>): CallModel? {
            val live = calls.filter { !it.state.isTerminal }
            if (live.isEmpty()) return null
            return live.firstOrNull { it.isIncomingRinging }
                ?: live.firstOrNull { it.state == CallState.DIALING || it.state == CallState.CONNECTING }
                ?: live.firstOrNull { it.isConference }
                ?: live.firstOrNull { it.state == CallState.ACTIVE }
                ?: live.first()
        }
    }
}

/** Map a [CallModel] state to the matching Glyph [CallVisual] choreography (§17.5). */
private fun CallModel.toGlyphVisual(): CallVisual = when {
    isConference -> CallVisual.CONFERENCE
    state == CallState.RINGING || state == CallState.DIALING || state == CallState.CONNECTING ->
        CallVisual.RINGING
    state == CallState.HOLDING || isOnHold -> CallVisual.HOLD
    state == CallState.ACTIVE -> CallVisual.ACTIVE
    state.isTerminal -> CallVisual.ENDED
    else -> CallVisual.ACTIVE
}
