// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Connection state of an in-app WebRTC VoIP session (§11).
 */
enum class WebRtcState {
    IDLE,
    CONNECTING,
    CONNECTED,
    /** Media is flowing and a video track is active (in-app video — §2.3). */
    VIDEO,
    RECONNECTING,
    FAILED,
    DISCONNECTED,
}

/**
 * Peripheral contract for the in-app VoIP/video layer (§2.3/§11). Impl lives in
 * `:peripheral:webrtc` (the only module allowed to import WebRTC + Ktor signaling).
 *
 * HONESTY PRINCIPLE (§2.3): this is in-app VoIP video over WebRTC ONLY — it is NOT
 * carrier video and cannot upgrade a cellular call. Video is offered only between
 * two participants on this app / a federated backend.
 */
interface WebRtcClient {

    /** Observable session state for the in-call UI. */
    val state: StateFlow<WebRtcState>

    /** Whether the remote peer currently offers a video track. */
    val remoteHasVideo: StateFlow<Boolean>

    /** Establish signaling + peer connection for the session keyed by [sessionId]. */
    suspend fun connect(sessionId: String): AppResult<Unit>

    /** Create and send an SDP offer (caller side). */
    suspend fun createOffer(): AppResult<Unit>

    /** Create and send an SDP answer in response to a received offer (callee side). */
    suspend fun createAnswer(): AppResult<Unit>

    /** Add (negotiate) a local video track — the "switch to video" upgrade (§11). */
    suspend fun addVideo(): AppResult<Unit>

    /** Remove the local video track — downgrade back to audio (§11). */
    suspend fun removeVideo(): AppResult<Unit>

    /** Mute/unmute the local audio track. */
    suspend fun setAudioEnabled(enabled: Boolean): AppResult<Unit>

    /** Switch between front/back camera for the local video track. */
    suspend fun switchCamera(): AppResult<Unit>

    /** Tear down the peer connection and signaling. Safe to call when idle. */
    suspend fun disconnect(): AppResult<Unit>
}
