// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.telecom.mapper.CallMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide registry that mirrors the live [android.telecom.Call] objects owned
 * by [com.glyphdialer.telecom.service.GlyphInCallService] into framework-free
 * [CallModel]s, and exposes the in-call *action* surface (BUILD_SPEC §7.6, §10).
 *
 * It is a singleton `object` (not Hilt-scoped) because the bound [InCallService] is
 * instantiated by the OS — not by the Hilt graph — and both the service and the
 * Hilt-injected [com.glyphdialer.telecom.repository.TelecomRepositoryImpl] must
 * observe the SAME live state. The service feeds it; the repository (and the in-call
 * UI through the repo) reads its [calls] flow and invokes its actions.
 *
 * Thread-safety: all framework Call actions are invoked on the binder/main thread by
 * the service; the repository action methods here marshal onto the main dispatcher
 * before touching a Call (see TelecomRepositoryImpl). Reads of [calls]/[audioState]
 * are lock-free StateFlow snapshots.
 */
import android.annotation.SuppressLint

@SuppressLint("StaticFieldLeak")
object CallRegistry {

    /** Stable, monotonically-increasing per-session call id (Call identity isn't serializable). */
    private val idSeq = AtomicLong(0L)

    /** Live framework handles keyed by our stable id, and the reverse lookup. */
    private val callsById = LinkedHashMap<String, Call>()
    private val idByCall = HashMap<Call, String>()

    private val _calls = MutableStateFlow<List<CallModel>>(emptyList())

    /** Observable list of live calls (newest-relevant first), framework-free. */
    val calls: StateFlow<List<CallModel>> = _calls.asStateFlow()

    private val _audioState = MutableStateFlow(AudioState())

    /** Observable system call-audio state (route/mute/BT) for the route picker. */
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    /**
     * The bound service, set in onCreate / cleared in onDestroy. Audio routing and
     * session mute go through the InCallService, not an individual Call.
     */
    @Volatile
    private var inCallService: InCallService? = null

    // --- Service lifecycle wiring (called only by GlyphInCallService) ----------

    fun attachService(service: InCallService) {
        inCallService = service
        Timber.tag(Constants.TAG).d("InCallService attached")
    }

    fun detachService() {
        inCallService = null
        callsById.clear()
        idByCall.clear()
        _calls.value = emptyList()
        _audioState.value = AudioState()
        Timber.tag(Constants.TAG).d("InCallService detached; registry cleared")
    }

    // --- Call set maintenance (called only by GlyphInCallService) --------------

    /** Register a newly-added [call] and return its assigned stable id. */
    @Synchronized
    fun registerCall(call: Call): String {
        idByCall[call]?.let { return it } // idempotent on re-add
        val id = "call-${idSeq.incrementAndGet()}"
        callsById[id] = call
        idByCall[call] = id
        val state = if (android.os.Build.VERSION.SDK_INT >= 31) call.details.state else call.state
        Timber.tag(Constants.TAG).d("Call added id=%s state=%d", id, state)
        rebuild()
        return id
    }

    /** Remove a [call] that the framework reported as gone. */
    @Synchronized
    fun unregisterCall(call: Call) {
        val id = idByCall.remove(call) ?: return
        callsById.remove(id)
        Timber.tag(Constants.TAG).d("Call removed id=%s", id)
        rebuild()
    }

    /** Re-derive the [CallModel] snapshot after any state/children/details change. */
    @Synchronized
    fun onCallChanged() = rebuild()

    /** Mirror the system [CallAudioState] into the domain [AudioState]. */
    fun onAudioStateChanged(state: CallAudioState) {
        _audioState.value = mapAudioState(state)
        // Mute is session-wide; reflect it onto every model on the next rebuild.
        synchronized(this) { rebuild() }
    }

    private fun rebuild() {
        val muted = _audioState.value.isMuted
        val models = callsById.entries.map { (id, call) ->
            val parentId = call.parent?.let { idByCall[it] }
            val childIds = call.children.mapNotNull { idByCall[it] }
            CallMapper.toModel(id = id, call = call, parentId = parentId, childIds = childIds, isMuted = muted)
        }
        // Foreground ordering: ringing/dialing first, then active, holding, then terminal.
        _calls.value = models.sortedBy { rank(it.state) }
    }

    private fun rank(state: CallState): Int = when (state) {
        CallState.RINGING -> 0
        CallState.DIALING, CallState.CONNECTING, CallState.NEW -> 1
        CallState.ACTIVE, CallState.CONFERENCE -> 2
        CallState.HOLDING -> 3
        CallState.DISCONNECTING, CallState.DISCONNECTED -> 4
    }

    // --- Lookup helpers --------------------------------------------------------

    @Synchronized
    private fun call(id: String): Call? = callsById[id]

    /** Snapshot of the current models (used by the repository's primaryCall derivation). */
    fun snapshot(): List<CallModel> = _calls.value

    // --- Actions (BUILD_SPEC §7.6 / §10) ---------------------------------------
    // Each returns true if the action was dispatched to a live Call, false if the
    // call/service was gone (the repository converts false into an AppResult.Failure).

    fun answer(id: String): Boolean = call(id)?.let {
        it.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY); true
    } ?: false

    fun reject(id: String, message: String?): Boolean = call(id)?.let { c ->
        if (message != null) c.reject(true, message) else c.reject(false, null)
        true
    } ?: false

    fun disconnect(id: String): Boolean = call(id)?.let { it.disconnect(); true } ?: false

    fun hold(id: String): Boolean = call(id)?.let { it.hold(); true } ?: false

    fun unhold(id: String): Boolean = call(id)?.let { it.unhold(); true } ?: false

    /** Session-wide mute via the InCallService (telecom mute is not per-call). */
    fun setMuted(muted: Boolean): Boolean =
        inCallService?.let { it.setMuted(muted); true } ?: false

    fun setAudioRoute(route: AudioRoute): Boolean {
        val svc = inCallService ?: return false
        when (route) {
            AudioRoute.BLUETOOTH -> {
                // Prefer a specific connected BT device (API 28+); otherwise fall back
                // to the generic BLUETOOTH route mask.
                val device = svc.callAudioState?.supportedBluetoothDevices?.firstOrNull()
                if (device != null) {
                    svc.requestBluetoothAudio(device)
                } else {
                    svc.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                }
            }
            AudioRoute.SPEAKER -> svc.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            AudioRoute.WIRED_HEADSET -> svc.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
            AudioRoute.EARPIECE -> svc.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
        }
        return true
    }

    fun playDtmf(id: String, digit: Char): Boolean = call(id)?.let { it.playDtmfTone(digit); true } ?: false

    fun stopDtmf(id: String): Boolean = call(id)?.let { it.stopDtmfTone(); true } ?: false

    /** Merge two independent calls into a conference (§10): conference(other). */
    fun conference(id: String, otherId: String): Boolean {
        val a = call(id) ?: return false
        val b = call(otherId) ?: return false
        a.conference(b)
        return true
    }

    /** Merge the background call into the foreground conference (§10). */
    fun mergeConference(id: String): Boolean = call(id)?.let { it.mergeConference(); true } ?: false

    /** Swap the active and held calls of a two-line/conference setup (§10). */
    fun swapConference(id: String): Boolean = call(id)?.let { it.swapConference(); true } ?: false

    /** Split [id] out of its parent conference (§10). */
    fun splitFromConference(id: String): Boolean = call(id)?.let { it.splitFromConference(); true } ?: false

    // --- Mapping ---------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun mapAudioState(s: CallAudioState): AudioState {
        val supported = buildSet {
            val mask = s.supportedRouteMask
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute.EARPIECE)
            if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute.SPEAKER)
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) add(AudioRoute.BLUETOOTH)
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute.WIRED_HEADSET)
        }.ifEmpty { setOf(AudioRoute.EARPIECE) }

        val active = when (s.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }
        return AudioState(
            route = active,
            supportedRoutes = supported,
            isMuted = s.isMuted,
            bluetoothDeviceName = runCatching {
                s.activeBluetoothDevice?.name
            }.getOrNull(),
        )
    }
}
