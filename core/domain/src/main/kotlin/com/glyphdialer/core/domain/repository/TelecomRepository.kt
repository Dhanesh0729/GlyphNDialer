// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.CallModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single source of truth for live telephony state (§7).
 *
 * IMPLEMENTATION NOTE: unlike most repositories (impls in `:core:data`), this one
 * is implemented in `:telecom`, which owns the InCallService/ConnectionService and
 * mirrors the platform Call API into [calls]. Observable reads are [Flow]/[StateFlow];
 * actions are `suspend` and return [AppResult] when fallible.
 */
interface TelecomRepository {

    /** All calls currently tracked by the InCallService, newest-relevant first. */
    val calls: StateFlow<List<CallModel>>

    /** The active call the in-call UI should foreground, or null when idle. */
    val primaryCall: StateFlow<CallModel?>

    /** Live system call-audio state (route, mute, BT) for the route picker. */
    val audioState: StateFlow<AudioState>

    /** Place an outgoing call to [number]. [isVoip] requests the self-managed VoIP path (§7.4). */
    suspend fun placeCall(number: String, isVoip: Boolean = false): AppResult<Unit>

    /** Answer the ringing call identified by [callId]. */
    suspend fun answerCall(callId: String): AppResult<Unit>

    /** Reject the ringing call identified by [callId] (optionally with an SMS body). */
    suspend fun rejectCall(callId: String, withMessage: String? = null): AppResult<Unit>

    /** Disconnect the call identified by [callId]. */
    suspend fun endCall(callId: String): AppResult<Unit>

    /** Put on hold ([hold]=true) or resume ([hold]=false) the call [callId]. */
    suspend fun setHold(callId: String, hold: Boolean): AppResult<Unit>

    /** Mute/unmute the microphone for the active call session. */
    suspend fun setMuted(muted: Boolean): AppResult<Unit>

    /** Route call audio to [route] (requests BT SCO for [AudioRoute.BLUETOOTH]). */
    suspend fun setAudioRoute(route: AudioRoute): AppResult<Unit>

    /** Play a DTMF tone for [digit] (call [stopDtmf] to release for tones that hold). */
    suspend fun playDtmf(callId: String, digit: Char): AppResult<Unit>

    /** Stop any in-progress DTMF tone for [callId]. */
    suspend fun stopDtmf(callId: String): AppResult<Unit>

    /** Merge [callId] with [otherCallId] into a conference (§10). */
    suspend fun merge(callId: String, otherCallId: String): AppResult<Unit>

    /** Swap the active and held calls of a two-line/conference setup (§10). */
    suspend fun swap(callId: String): AppResult<Unit>

    /** Split [callId] out of its parent conference (§10). */
    suspend fun splitFromConference(callId: String): AppResult<Unit>

    /** Request an in-app VoIP video upgrade for [callId] (WebRTC only — §2.3/§11). */
    suspend fun requestVideoUpgrade(callId: String): AppResult<Unit>

    /** Downgrade [callId] back to audio-only (removes the video track). */
    suspend fun downgradeToAudio(callId: String): AppResult<Unit>
}
