// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.webrtc

import org.webrtc.PeerConnection

/**
 * ICE server configuration for the in-app VoIP peer connection (BUILD_SPEC §11).
 *
 * STUN lets peers discover their public reflexive address for direct, peer-to-peer
 * media (no media relay cost). TURN relays media when a direct path is impossible
 * (symmetric NAT, restrictive firewalls) — it is REQUIRED for reliable calling in
 * the real world, but needs credentials you must provision.
 *
 * TODO(turn): provision a TURN server (coturn, or a managed service such as
 *  Twilio NTS / Cloudflare / Xirsys / metered.ca) and supply [TurnCredentials].
 *  Best practice: fetch SHORT-LIVED, per-session TURN credentials from your
 *  signaling backend at call setup rather than hard-coding long-lived secrets in
 *  the app. The placeholder below is intentionally non-functional.
 */
object IceConfig {

    /** Google's public STUN server — fine for development; not a substitute for TURN. */
    const val DEFAULT_STUN_URL: String = "stun:stun.l.google.com:19302"

    /** A second public STUN for redundancy. */
    const val FALLBACK_STUN_URL: String = "stun:stun1.l.google.com:19302"

    // TODO(turn): replace with your TURN server URL(s), e.g. "turn:turn.example.com:3478"
    //  and "turns:turn.example.com:5349?transport=tcp" for TLS/TCP fallback.
    const val PLACEHOLDER_TURN_URL: String = "turn:turn.invalid.glyphdialer.example:3478"

    /**
     * Long-lived (static) TURN credentials. PREFER ephemeral credentials fetched at
     * call time; static secrets in a shipped APK are extractable.
     */
    data class TurnCredentials(
        val urls: List<String>,
        val username: String,
        val password: String,
    )

    /**
     * Build the WebRTC [PeerConnection.IceServer] list. Always includes STUN; only
     * includes TURN when real [turn] credentials are supplied (the placeholder is
     * never silently shipped — §9 honesty).
     */
    fun iceServers(turn: TurnCredentials? = null): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        servers += PeerConnection.IceServer.builder(
            listOf(DEFAULT_STUN_URL, FALLBACK_STUN_URL),
        ).createIceServer()

        if (turn != null && turn.urls.isNotEmpty()) {
            servers += PeerConnection.IceServer.builder(turn.urls)
                .setUsername(turn.username)
                .setPassword(turn.password)
                .createIceServer()
        }

        return servers
    }

    /**
     * Produce a fully-formed [PeerConnection.RTCConfiguration] tuned for VoIP:
     * unified-plan SDP, continual gathering for ICE trickling, and the supplied ICE
     * servers.
     */
    fun rtcConfiguration(turn: TurnCredentials? = null): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(iceServers(turn)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            // Prefer ECDSA DTLS certificates (faster handshake than RSA).
            keyType = PeerConnection.KeyType.ECDSA
            // Pre-gather a small candidate pool to shave setup latency.
            iceCandidatePoolSize = 2
            // Only switch to TURN-relay if no direct/STUN path is found.
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }
}
