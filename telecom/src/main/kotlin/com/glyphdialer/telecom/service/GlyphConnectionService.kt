// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.annotation.RequiresApi
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.core.domain.repository.WebRtcClient
import com.glyphdialer.core.domain.repository.WebRtcState
import com.glyphdialer.telecom.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Self-managed [ConnectionService] for in-app WebRTC VoIP (BUILD_SPEC §7.4, §11).
 *
 * Registering a [PhoneAccount] with [PhoneAccount.CAPABILITY_SELF_MANAGED] lets our
 * VoIP calls participate in the system call UX: audio focus, Bluetooth routing,
 * "phone is in a call" state, and interop with cellular calls — WITHOUT becoming a
 * carrier call. Each [GlyphConnection] bridges its lifecycle to the injected
 * [WebRtcClient] (the actual media lives in `:peripheral:webrtc`; we never import it
 * here — only the domain interface, per CONVENTIONS.md §3).
 *
 * HONESTY PRINCIPLE (§2.3/§9): this path is the ONLY way Glyph Dialer offers video.
 * It is in-app VoIP video, never carrier video, and is gated to in-app calls.
 */
@AndroidEntryPoint
class GlyphConnectionService : ConnectionService() {

    @Inject lateinit var webRtcClient: WebRtcClient

    @Inject lateinit var glyphController: GlyphController
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val sessionId = request?.extras?.getString(Constants.EXTRA_SESSION_ID)
            ?: request?.address?.schemeSpecificPart
            ?: "voip-${System.currentTimeMillis()}"
        val startWithVideo = request?.extras?.getBoolean(Constants.EXTRA_START_WITH_VIDEO, false) == true
        Timber.tag(Constants.TAG).i("Outgoing self-managed connection session=%s", sessionId)
        return GlyphConnection(
            sessionId = sessionId,
            address = request?.address,
            webRtcClient = webRtcClient,
            glyphController = glyphController,
            settingsRepository = settingsRepository,
            outgoing = true,
            startWithVideo = startWithVideo,
        )
            .also { it.setDialing() }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val sessionId = request?.extras?.getString(Constants.EXTRA_SESSION_ID)
            ?: "voip-${System.currentTimeMillis()}"
        Timber.tag(Constants.TAG).i("Incoming self-managed connection session=%s", sessionId)
        return GlyphConnection(
            sessionId = sessionId,
            address = request?.address,
            webRtcClient = webRtcClient,
            glyphController = glyphController,
            settingsRepository = settingsRepository,
            outgoing = false,
            startWithVideo = false,
        )
            .also { it.setRinging() }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Timber.tag(Constants.TAG).w("Outgoing self-managed connection failed")
    }

    companion object {

        /** The [PhoneAccountHandle] for our self-managed VoIP account. */
        fun handle(context: Context): PhoneAccountHandle =
            PhoneAccountHandle(
                ComponentName(context, GlyphConnectionService::class.java),
                Constants.SELF_MANAGED_ACCOUNT_ID,
            )

        /**
         * Register the self-managed VoIP [PhoneAccount] (§7.4). Idempotent; safe to
         * call on every app start. No-op below the API where self-managed accounts
         * exist (API 26+), where this whole feature is unsupported.
         */
        fun registerPhoneAccount(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return
            val account = PhoneAccount.builder(handle(context), "Glyph Dialer VoIP")
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED or
                        PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                        PhoneAccount.CAPABILITY_VIDEO_CALLING,
                )
                .setShortDescription("In-app VoIP & video calls (WebRTC)")
                .build()
            runCatching { telecom.registerPhoneAccount(account) }
                .onFailure { Timber.tag(Constants.TAG).e(it, "Failed to register VoIP PhoneAccount") }
        }

        fun unregisterPhoneAccount(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return
            runCatching { telecom.unregisterPhoneAccount(handle(context)) }
        }

        /**
         * Place an in-app VoIP call through the self-managed PhoneAccount.
         * [startWithVideo] requests camera/video negotiation in the initial offer.
         */
        fun placeVoipCall(
            context: Context,
            number: String,
            startWithVideo: Boolean,
        ): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            registerPhoneAccount(context)
            val sessionId = "voip-${number.hashCode()}-${System.currentTimeMillis()}"
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(context))
                putString(Constants.EXTRA_SESSION_ID, sessionId)
                putBoolean(Constants.EXTRA_IS_VOIP, true)
                putBoolean(Constants.EXTRA_START_WITH_VIDEO, startWithVideo)
            }
            return runCatching {
                telecom.placeCall(Uri.fromParts("tel", number, null), extras)
                true
            }.onFailure {
                Timber.tag(Constants.TAG).w(it, "Failed to place self-managed VoIP call")
            }.getOrDefault(false)
        }
    }
}

/**
 * A single self-managed VoIP [Connection] bridged to the [WebRtcClient]. The Telecom
 * framework drives lifecycle (answer/hold/disconnect) and we translate those into
 * WebRTC signaling actions, and mirror WebRTC state back onto the connection.
 */
@RequiresApi(Build.VERSION_CODES.O)
private class GlyphConnection(
    private val sessionId: String,
    address: Uri?,
    private val webRtcClient: WebRtcClient,
    private val glyphController: GlyphController,
    private val settingsRepository: SettingsRepository,
    private val outgoing: Boolean,
    private val startWithVideo: Boolean,
) : Connection() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)

    init {
        setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
        // Self-managed connections must declare audio-mode support so the platform
        // grants audio focus and routes Bluetooth like a real call.
        audioModeIsVoip = true
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_HOLD or
            CAPABILITY_SUPPORT_HOLD or
            CAPABILITY_MUTE
        observeWebRtc()
    }

    /** Reconcile the framework Connection state from the WebRTC session state (§11). */
    private fun observeWebRtc() {
        webRtcClient.state
            .onEach { st ->
                when (st) {
                    WebRtcState.CONNECTED -> {
                        if (state != STATE_ACTIVE) setActive()
                        setVideoState(VideoProfile.STATE_AUDIO_ONLY)
                    }
                    WebRtcState.VIDEO -> {
                        if (state != STATE_ACTIVE) setActive()
                        setVideoState(VideoProfile.STATE_BIDIRECTIONAL)
                    }
                    WebRtcState.RECONNECTING, WebRtcState.CONNECTING -> { /* keep current */ }
                    WebRtcState.FAILED -> setDisconnected(DisconnectCause(DisconnectCause.ERROR))
                    WebRtcState.DISCONNECTED, WebRtcState.IDLE -> {
                        if (state != STATE_DISCONNECTED) setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                    }
                }
            }
            .launchIn(scope)
    }

    override fun onAnswer() {
        Timber.tag(Constants.TAG).d("Connection.onAnswer session=%s", sessionId)
        scope.launch {
            webRtcClient.connect(sessionId)
            webRtcClient.createAnswer()
        }
    }

    override fun onShowIncomingCallUi() {
        // Self-managed apps must post their own incoming-call UI; the InCallService
        // notification path handles this for default-dialer mode, and :feature:incall
        // surfaces the full-screen UI. Nothing extra required here.
        scope.launch {
            val pattern = when (val res = settingsRepository.current()) {
                is com.glyphdialer.core.common.AppResult.Success -> res.data.glyphPattern
                else -> com.glyphdialer.core.domain.model.GlyphPattern.PULSE
            }
            glyphController.playIncomingShow(sessionId.hashCode(), pattern)
        }
    }

    override fun onAbort() {
        teardown(DisconnectCause(DisconnectCause.CANCELED))
    }

    override fun onReject() {
        teardown(DisconnectCause(DisconnectCause.REJECTED))
    }

    override fun onDisconnect() {
        teardown(DisconnectCause(DisconnectCause.LOCAL))
    }

    override fun onHold() {
        setOnHold()
        scope.launch { webRtcClient.setAudioEnabled(false) }
    }

    override fun onUnhold() {
        setActive()
        scope.launch { webRtcClient.setAudioEnabled(true) }
    }

    override fun onPlayDtmfTone(c: Char) {
        // VoIP DTMF would be carried as RFC 2833 telephone-event by the WebRTC layer;
        // the domain WebRtcClient does not expose a DTMF API yet, so this is a no-op
        // placeholder rather than faking tone injection (§2 honesty).
        Timber.tag(Constants.TAG).d("VoIP DTMF '%s' (not yet wired to WebRtcClient)", c)
    }

    /** Begin the caller-side WebRTC negotiation once the framework marks us dialing. */
    override fun onStateChanged(newState: Int) {
        super.onStateChanged(newState)
        if (outgoing && newState == STATE_DIALING) {
            scope.launch {
                webRtcClient.connect(sessionId)
                if (startWithVideo) {
                    webRtcClient.addVideo()
                } else {
                    webRtcClient.createOffer()
                }
            }
        }
    }

    private fun teardown(cause: DisconnectCause) {
        scope.launch { webRtcClient.disconnect() }
        setDisconnected(cause)
        destroy()
        job.cancel()
    }
}
