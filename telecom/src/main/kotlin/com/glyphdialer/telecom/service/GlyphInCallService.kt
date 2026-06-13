// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.glyphdialer.core.domain.glyph.CallVisual
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.telecom.CallRegistry
import com.glyphdialer.telecom.Constants
import com.glyphdialer.telecom.notification.CallNotificationManager
import dagger.hilt.android.AndroidEntryPoint
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

    @Inject lateinit var notifications: CallNotificationManager

    /** One callback per Call so we can re-derive the registry on every transition. */
    private val callbacks = HashMap<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        CallRegistry.attachService(this)
        Timber.tag(Constants.TAG).i("GlyphInCallService created")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val id = CallRegistry.registerCall(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                CallRegistry.onCallChanged()
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
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        notifications.bringInCallToForeground(showDialpad)
    }

    override fun onDestroy() {
        callbacks.forEach { (call, cb) -> runCatching { call.unregisterCallback(cb) } }
        callbacks.clear()
        CallRegistry.detachService()
        notifications.clearAll()
        runCatching { glyphController.showOnCall(CallVisual.ENDED) }
        Timber.tag(Constants.TAG).i("GlyphInCallService destroyed")
        super.onDestroy()
    }

    /** Reconcile notifications + Glyph choreography from the current registry snapshot. */
    private fun refresh() {
        val calls = CallRegistry.snapshot()
        val primary = pickPrimary(calls)
        notifications.update(calls, primary)
        driveGlyph(primary)
    }

    private fun pickPrimary(calls: List<CallModel>): CallModel? =
        calls.firstOrNull { it.isIncomingRinging }
            ?: calls.firstOrNull { it.state == CallState.ACTIVE || it.state == CallState.CONFERENCE }
            ?: calls.firstOrNull { it.state == CallState.DIALING || it.state == CallState.CONNECTING }
            ?: calls.firstOrNull { !it.state.isTerminal }

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
            runCatching { glyphController.playIncomingShow(primary.number.dialValue.hashCode()) }
        }
    }
}
