// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.glyphdialer.core.domain.glyph.CallVisual
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.telecom.CallRegistry
import com.glyphdialer.telecom.Constants
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import com.glyphdialer.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.glyphdialer.telecom.gesture.BackTapCallController
import com.glyphdialer.telecom.notification.CallNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import timber.log.Timber
import javax.inject.Inject

/**
 * The system-bound in-call UI provider (BUILD_SPEC §7.2). When Glyph Dialer holds
 * the default-dialer role, the OS binds this service for every call. We:
 *  - register a [Call.Callback] on each added call,
 *  - mirror live state into the framework-free [CallRegistry] (the single source of
 *    truth the in-call ViewModel observes through [com.glyphdialer.telecom.repository.TelecomRepositoryImpl]),
 *  - drive the call notification + full-screen incoming intent,
 *  - and play the Glyph choreography through the injected [GlyphController] — which
 *    is a no-op on non-Nothing hardware (§9 HONESTY PRINCIPLE), so this is always safe.
 *
 * NOTE: the OS instantiates this class, not Hilt — [@AndroidEntryPoint] enables field
 * injection of our singletons into the OS-created instance.
 */
@AndroidEntryPoint
class GlyphInCallService : InCallService() {

    @Inject lateinit var glyphController: GlyphController
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var contactsRepository: ContactsRepository
    @Inject lateinit var glyphRepository: CustomGlyphPatternRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var isServiceForeground = false

    @Inject lateinit var notifications: CallNotificationManager
    @Inject lateinit var backTapCallController: BackTapCallController

    /** One callback per Call so we can re-derive the registry on every transition. */
    private val callbacks = HashMap<Call, Call.Callback>()
    private var lastLiveCallIds: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        CallRegistry.attachService(this)
        Timber.tag(Constants.TAG).i("GlyphInCallService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            val callId = intent.getStringExtra(EXTRA_CALL_ID)
            Timber.tag(Constants.TAG).i("GlyphInCallService onStartCommand: action=%s, callId=%s", action, callId)
            if (callId != null) {
                when (action) {
                    ACTION_ANSWER -> {
                        val answered = CallRegistry.answer(callId)
                        Timber.tag(Constants.TAG).i("Answer call result: %b", answered)
                    }
                    ACTION_DECLINE -> {
                        val rejected = CallRegistry.reject(callId, null)
                        Timber.tag(Constants.TAG).i("Reject call result: %b", rejected)
                    }
                    ACTION_DISCONNECT -> {
                        val disconnected = CallRegistry.disconnect(callId)
                        Timber.tag(Constants.TAG).i("Disconnect call result: %b", disconnected)
                    }
                    ACTION_TOGGLE_MUTE -> {
                        toggleMute()
                    }
                    ACTION_TOGGLE_SPEAKER -> {
                        toggleSpeaker()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val id = CallRegistry.registerCall(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                val wasConnecting = CallRegistry.snapshot().find { it.id == id }?.state
                CallRegistry.onCallChanged()
                val isConnecting = CallRegistry.snapshot().find { it.id == id }?.state
                
                // If it transitioned to an active/dialing state, ensure UI is shown.
                val becameActive = wasConnecting != CallState.ACTIVE && isConnecting == CallState.ACTIVE
                val becameDialing = wasConnecting != isConnecting && (isConnecting == CallState.DIALING || isConnecting == CallState.CONNECTING)
                
                if (becameActive || becameDialing) {
                    notifications.bringInCallToForeground(showDialpad = false)
                }
                refresh()
            }

            override fun onDetailsChanged(c: Call, details: Call.Details) {
                CallRegistry.onCallChanged()
                refresh()
            }

            override fun onChildrenChanged(c: Call, children: MutableList<Call>) {
                CallRegistry.onCallChanged()
                refresh()
            }

            override fun onParentChanged(c: Call, parent: Call?) {
                CallRegistry.onCallChanged()
                refresh()
            }

            override fun onConferenceableCallsChanged(c: Call, conferenceable: MutableList<Call>) {
                CallRegistry.onCallChanged()
            }

            override fun onCallDestroyed(c: Call) {
                CallRegistry.unregisterCall(c)
                callbacks.remove(c)
                refresh()
            }
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        Timber.tag(Constants.TAG).d("onCallAdded id=%s", id)
        
        // Automatically show the InCall UI for outgoing calls (BUILD_SPEC §7.2)
        // Check state on added, or later in onStateChanged.
        val callModel = CallRegistry.snapshot().find { it.id == id }
        if (callModel != null && !callModel.isIncomingRinging) {
            notifications.bringInCallToForeground(showDialpad = false)
        }
        
        refresh()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        CallRegistry.unregisterCall(call)
        refresh()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallRegistry.onAudioStateChanged(audioState)
        refresh()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        notifications.bringInCallToForeground(showDialpad)
    }

    override fun onDestroy() {
        callbacks.forEach { (call, cb) -> runCatching { call.unregisterCallback(cb) } }
        callbacks.clear()
        applicationScope.cancel()
        CallRegistry.detachService()
        backTapCallController.release()
        notifications.clearAll()
        runCatching { glyphController.showOnCall(CallVisual.ENDED) }
        Timber.tag(Constants.TAG).i("GlyphInCallService destroyed")
        super.onDestroy()
    }

    /** Reconcile notifications + Glyph choreography from the current registry snapshot. */
    private fun refresh() {
        val calls = CallRegistry.snapshot()
        val primary = pickPrimary(calls)
        updateCallEndedFeedback(calls)
        backTapCallController.update(primary)
        updateForegroundState(primary)
        notifications.update(calls, primary)
        driveGlyph(primary)
    }

    private fun updateCallEndedFeedback(calls: List<CallModel>) {
        val liveIds = calls.filterNot { it.state.isTerminal }.map { it.id }.toSet()
        val ended = lastLiveCallIds - liveIds
        if (ended.isNotEmpty()) notifications.vibrateCallEnded()
        lastLiveCallIds = liveIds
    }

    private fun updateForegroundState(primary: CallModel?) {
        if (primary == null || primary.state.isTerminal || primary.isIncomingRinging) {
            if (isServiceForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isServiceForeground = false
                Timber.tag(Constants.TAG).i("GlyphInCallService: stopped foreground")
            }
        } else {
            val notification = notifications.buildOngoing(primary)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            } else {
                0
            }
            try {
                ServiceCompat.startForeground(
                    this,
                    com.glyphdialer.core.common.Constants.NotificationIds.ONGOING_CALL,
                    notification,
                    type
                )
                isServiceForeground = true
                Timber.tag(Constants.TAG).i("GlyphInCallService: started foreground with notification")
            } catch (t: Throwable) {
                Timber.tag(Constants.TAG).e(t, "GlyphInCallService startForeground failed")
            }
        }
    }

    private fun pickPrimary(calls: List<CallModel>): CallModel? =
        calls.firstOrNull { it.isIncomingRinging }
            ?: calls.firstOrNull { it.state == CallState.ACTIVE || it.state == CallState.CONFERENCE }
            ?: calls.firstOrNull { it.state == CallState.DIALING || it.state == CallState.CONNECTING }
            ?: calls.firstOrNull { !it.state.isTerminal }

    private fun toggleMute() {
        val currentMuted = CallRegistry.audioState.value.isMuted
        CallRegistry.setMuted(!currentMuted)
        refresh()
    }

    private fun toggleSpeaker() {
        val currentRoute = CallRegistry.audioState.value.route
        val targetRoute = if (currentRoute == AudioRoute.SPEAKER) {
            val supported = CallRegistry.audioState.value.supportedRoutes
            if (AudioRoute.EARPIECE in supported) {
                AudioRoute.EARPIECE
            } else if (AudioRoute.WIRED_HEADSET in supported) {
                AudioRoute.WIRED_HEADSET
            } else {
                AudioRoute.EARPIECE
            }
        } else {
            AudioRoute.SPEAKER
        }
        CallRegistry.setAudioRoute(targetRoute)
        refresh()
    }

    private fun driveGlyph(primary: CallModel?) {
        // GlyphController no-ops when unavailable (§9); calling unconditionally is safe.
        val visual = when (primary?.state) {
            CallState.RINGING, CallState.DIALING, CallState.CONNECTING -> CallVisual.RINGING
            CallState.ACTIVE -> CallVisual.ACTIVE
            CallState.HOLDING -> CallVisual.HOLD
            CallState.CONFERENCE -> CallVisual.CONFERENCE
            CallState.DISCONNECTING, CallState.DISCONNECTED, CallState.NEW, null -> CallVisual.ENDED
        }
        runCatching { glyphController.showOnCall(visual) }
        if (primary?.isIncomingRinging == true) {
            applicationScope.launch {
                var pattern: com.glyphdialer.core.domain.model.GlyphPattern? = null
                
                // 1. Try to find custom pattern mapped to this caller
                val contact = contactsRepository.findByNumber(primary.number.dialValue).let {
                    if (it is com.glyphdialer.core.common.AppResult.Success) it.data else null
                }
                
                if (contact != null) {
                    val customPattern = glyphRepository.getPatternForContact(contact.lookupKey)
                    if (customPattern != null) {
                        runCatching { glyphController.playIncomingShow(primary.number.dialValue.hashCode(), customPattern) }
                        return@launch
                    }
                }
                
                // 2. Fallback to default pattern from settings
                val defaultPattern = when (val res = settingsRepository.current()) {
                    is com.glyphdialer.core.common.AppResult.Success -> res.data.glyphPattern
                    else -> com.glyphdialer.core.domain.model.GlyphPattern.PULSE
                }
                runCatching { glyphController.playIncomingShow(primary.number.dialValue.hashCode(), defaultPattern) }
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.glyphdialer.telecom.action.ANSWER"
        const val ACTION_DECLINE = "com.glyphdialer.telecom.action.DECLINE"
        const val ACTION_DISCONNECT = "com.glyphdialer.telecom.action.DISCONNECT"
        const val ACTION_TOGGLE_MUTE = "com.glyphdialer.telecom.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_SPEAKER = "com.glyphdialer.telecom.action.TOGGLE_SPEAKER"
        const val EXTRA_CALL_ID = "com.glyphdialer.telecom.extra.CALL_ID"
    }
}
