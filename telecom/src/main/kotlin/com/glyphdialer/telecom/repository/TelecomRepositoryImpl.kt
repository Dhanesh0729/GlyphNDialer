// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.repository

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.repository.TelecomRepository
import com.glyphdialer.core.domain.repository.WebRtcClient
import com.glyphdialer.telecom.CallRegistry
import com.glyphdialer.telecom.service.GlyphConnectionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.glyphdialer.telecom.Constants as TelecomConstants

/**
 * `:telecom`-side implementation of the domain [TelecomRepository] (CONVENTIONS.md
 * §6, BUILD_SPEC §7). It is the bridge between the framework-bound services
 * (InCallService / ConnectionService / CallScreeningService, which feed
 * [CallRegistry]) and the rest of the app:
 *  - observable reads ([calls], [primaryCall], [audioState]) come straight from the
 *    registry's [StateFlow]s, and
 *  - actions are `suspend` and marshalled onto the MAIN dispatcher before touching a
 *    framework [android.telecom.Call] (the Telecom API must be invoked on the main
 *    thread), wrapped in [AppResult].
 *
 * Outgoing calls use [TelecomManager.placeCall] for cellular and, for [isVoip], the
 * self-managed VoIP path via our registered [GlyphConnectionService] PhoneAccount
 * (§7.4). The actual media is owned by the injected [WebRtcClient] (impl in
 * `:peripheral:webrtc`); we never import WebRTC here (CONVENTIONS.md §3).
 *
 * HONESTY PRINCIPLE (§2.3): [requestVideoUpgrade]/[downgradeToAudio] succeed ONLY for
 * an in-app VoIP call; requesting them on a cellular call returns a failure rather
 * than pretending carrier video exists.
 */
@Singleton
class TelecomRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webRtcClient: WebRtcClient,
    @Dispatcher(GlyphDispatcher.MAIN) private val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TelecomRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val telecomManager: TelecomManager? =
        context.getSystemService(TelecomManager::class.java)

    init {
        // Register the self-managed VoIP PhoneAccount up front so VoIP placeCall works
        // immediately (idempotent — safe to call repeatedly). No-op on unsupported OS.
        GlyphConnectionService.registerPhoneAccount(context)
    }

    override val calls: StateFlow<List<CallModel>> = CallRegistry.calls

    override val primaryCall: StateFlow<CallModel?> =
        CallRegistry.calls
            .map(::derivePrimary)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), derivePrimary(CallRegistry.snapshot()))

    override val audioState: StateFlow<AudioState> = CallRegistry.audioState

    // --- Place ----------------------------------------------------------------

    override suspend fun placeCall(number: String, isVoip: Boolean): AppResult<Unit> =
        appResultOfSuspend {
            val tm = telecomManager ?: error("TelecomManager unavailable on this device")
            val uri = Uri.fromParts(Constants.TEL_SCHEME, number, null)
            val extras = Bundle().apply {
                if (isVoip) {
                    // Route the call through our self-managed VoIP PhoneAccount (§7.4).
                    putParcelable(
                        TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                        GlyphConnectionService.handle(context),
                    )
                    putBoolean(TelecomConstants.EXTRA_IS_VOIP, true)
                }
            }
            // placeCall requires CALL_PHONE / the default-dialer role; if missing the
            // platform throws SecurityException, which appResultOfSuspend captures —
            // we never crash the caller (§9).
            try {
                tm.placeCall(uri, extras)
            } catch (se: SecurityException) {
                Timber.tag(TelecomConstants.TAG).e(se, "placeCall denied (missing CALL_PHONE/role)")
                throw se
            }
        }

    // --- Per-call actions (marshalled onto main; AppResult-wrapped) ------------

    override suspend fun answerCall(callId: String): AppResult<Unit> =
        dispatch("answer", callId) { CallRegistry.answer(callId) }

    override suspend fun rejectCall(callId: String, withMessage: String?): AppResult<Unit> =
        dispatch("reject", callId) { CallRegistry.reject(callId, withMessage) }

    override suspend fun endCall(callId: String): AppResult<Unit> =
        dispatch("end", callId) { CallRegistry.disconnect(callId) }

    override suspend fun setHold(callId: String, hold: Boolean): AppResult<Unit> =
        dispatch(if (hold) "hold" else "unhold", callId) {
            if (hold) CallRegistry.hold(callId) else CallRegistry.unhold(callId)
        }

    override suspend fun setMuted(muted: Boolean): AppResult<Unit> =
        dispatch("setMuted", null) { CallRegistry.setMuted(muted) }

    override suspend fun setAudioRoute(route: AudioRoute): AppResult<Unit> =
        dispatch("setAudioRoute", null) { CallRegistry.setAudioRoute(route) }

    override suspend fun playDtmf(callId: String, digit: Char): AppResult<Unit> =
        dispatch("playDtmf", callId) { CallRegistry.playDtmf(callId, digit) }

    override suspend fun stopDtmf(callId: String): AppResult<Unit> =
        dispatch("stopDtmf", callId) { CallRegistry.stopDtmf(callId) }

    override suspend fun merge(callId: String, otherCallId: String): AppResult<Unit> =
        dispatch("merge", callId) { CallRegistry.conference(callId, otherCallId) }

    override suspend fun swap(callId: String): AppResult<Unit> =
        dispatch("swap", callId) { CallRegistry.swapConference(callId) }

    override suspend fun splitFromConference(callId: String): AppResult<Unit> =
        dispatch("split", callId) { CallRegistry.splitFromConference(callId) }

    // --- Video upgrade/downgrade (in-app VoIP ONLY — §2.3/§11) -----------------

    override suspend fun requestVideoUpgrade(callId: String): AppResult<Unit> {
        val call = calls.value.firstOrNull { it.id == callId }
            ?: return AppResult.Failure(IllegalStateException("No such call: $callId"), "Call not found")
        if (!call.isVoip) {
            // Truthful refusal: carrier video is never available to a third-party
            // dialer (§2.3). The in-call UI must hide this control for cellular calls.
            return AppResult.Failure(
                UnsupportedOperationException("Video is in-app VoIP only"),
                "Video calling is only available for in-app VoIP calls",
            )
        }
        return webRtcClient.addVideo()
    }

    override suspend fun downgradeToAudio(callId: String): AppResult<Unit> {
        val call = calls.value.firstOrNull { it.id == callId }
            ?: return AppResult.Failure(IllegalStateException("No such call: $callId"), "Call not found")
        if (!call.isVoip) return AppResult.Success(Unit) // nothing to downgrade
        return webRtcClient.removeVideo()
    }

    // --- Helpers ---------------------------------------------------------------

    /**
     * Runs a framework Call action on the main thread (Telecom requires it). [action]
     * returns true if it reached a live call/service; we translate false into a
     * presentable [AppResult.Failure] so the UI can react without exceptions.
     */
    private suspend inline fun dispatch(
        name: String,
        callId: String?,
        crossinline action: () -> Boolean,
    ): AppResult<Unit> = appResultOfSuspend {
        val ok = withContext(mainDispatcher) { action() }
        if (!ok) {
            Timber.tag(TelecomConstants.TAG).w("Telecom action '%s' had no live target (callId=%s)", name, callId)
            error("No live call/service for action '$name'")
        }
    }

    private fun derivePrimary(list: List<CallModel>): CallModel? =
        list.firstOrNull { it.isIncomingRinging }
            ?: list.firstOrNull { it.state == CallState.ACTIVE || it.state == CallState.CONFERENCE }
            ?: list.firstOrNull { it.state == CallState.DIALING || it.state == CallState.CONNECTING }
            ?: list.firstOrNull { !it.state.isTerminal }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
