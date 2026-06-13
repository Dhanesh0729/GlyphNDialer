// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.webrtc.signaling

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire protocol exchanged with the signaling backend over the Ktor WebSocket
 * (BUILD_SPEC §11). All variants are members of a single sealed hierarchy so the
 * polymorphic kotlinx.serialization codec can round-trip them with a `type`
 * discriminator.
 *
 * This is a small, opinionated protocol: it carries SDP offers/answers and trickled
 * ICE candidates plus a handful of session-control messages (join, peer presence,
 * the in-app "switch to video" prompt/response, and bye). A real backend
 * (your own Ktor server, LiveKit, Twilio, etc.) must speak the same shape — see the
 * TODO on [com.glyphdialer.peripheral.webrtc.signaling.SignalingConfig].
 */
@Serializable
sealed interface SignalingMessage {

    /** The session this message belongs to (the WebRTC room/call key). */
    val sessionId: String

    /**
     * Client → server. Joins (or creates) the room for [sessionId]. The server
     * replies with [PeerJoined] for any peer already present and forwards future
     * peers as they arrive.
     */
    @Serializable
    @SerialName("join")
    data class Join(
        override val sessionId: String,
        /** A stable identifier for this client within the session. */
        val from: String,
    ) : SignalingMessage

    /** Server → client. A remote peer has joined the room (caller should offer). */
    @Serializable
    @SerialName("peer-joined")
    data class PeerJoined(
        override val sessionId: String,
        val peerId: String,
    ) : SignalingMessage

    /** Server → client. A remote peer left the room. */
    @Serializable
    @SerialName("peer-left")
    data class PeerLeft(
        override val sessionId: String,
        val peerId: String,
    ) : SignalingMessage

    /** An SDP offer (caller side, also used to renegotiate when adding/removing video). */
    @Serializable
    @SerialName("offer")
    data class Offer(
        override val sessionId: String,
        /** RFC 4566 SDP blob. */
        val sdp: String,
    ) : SignalingMessage

    /** An SDP answer (callee side / renegotiation response). */
    @Serializable
    @SerialName("answer")
    data class Answer(
        override val sessionId: String,
        val sdp: String,
    ) : SignalingMessage

    /** A trickled ICE candidate. */
    @Serializable
    @SerialName("ice")
    data class IceCandidate(
        override val sessionId: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String,
    ) : SignalingMessage

    /**
     * In-app "switch to video" prompt (§11). When a peer adds a video track it
     * renegotiates with a new [Offer]; this control message lets the UI surface an
     * explicit accept/decline prompt to the remote party rather than silently
     * enabling the camera.
     */
    @Serializable
    @SerialName("video-request")
    data class VideoRequest(
        override val sessionId: String,
    ) : SignalingMessage

    /** Remote party's response to a [VideoRequest]. */
    @Serializable
    @SerialName("video-response")
    data class VideoResponse(
        override val sessionId: String,
        val accepted: Boolean,
    ) : SignalingMessage

    /** Graceful teardown. */
    @Serializable
    @SerialName("bye")
    data class Bye(
        override val sessionId: String,
    ) : SignalingMessage
}
