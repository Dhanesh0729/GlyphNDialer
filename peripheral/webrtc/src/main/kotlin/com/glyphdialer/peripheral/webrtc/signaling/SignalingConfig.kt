// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.webrtc.signaling

/**
 * Configuration for the signaling transport (BUILD_SPEC §11).
 *
 * TODO(backend): point [scheme]/[host]/[port]/[path] at YOUR signaling backend.
 *  This may be:
 *    - your own Ktor `webSocket("/rtc/{session}")` server,
 *    - a managed provider (LiveKit, Twilio Video, Daily, etc.) — in which case the
 *      [SignalingMessage] shape and [com.glyphdialer.peripheral.webrtc.signaling.SignalingClient]
 *      framing must be adapted to that provider's protocol.
 *  The default below is an obviously-non-functional placeholder so that the module
 *  fails fast and loudly (connection refused) rather than silently pretending to
 *  work — honoring the §9 honesty principle.
 */
data class SignalingConfig(
    /** "ws" or "wss" (use "wss" in production). */
    val scheme: String = DEFAULT_SCHEME,
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    /**
     * WebSocket path. `{session}` is substituted with the connecting session id.
     * Customize to match your backend's routing.
     */
    val path: String = DEFAULT_PATH,
    /**
     * Optional bearer token / API key forwarded as a header on the WS upgrade.
     * TODO(backend): wire up to your auth (per-user JWT, room token, etc.).
     */
    val authToken: String? = null,
    /** This client's stable id within a session (defaults to a random per-process id). */
    val clientId: String = DEFAULT_CLIENT_ID,
) {
    /** Resolve the full WebSocket URL for [sessionId]. */
    fun urlFor(sessionId: String): String {
        val resolvedPath = path.replace(SESSION_PLACEHOLDER, sessionId)
        val normalizedPath = if (resolvedPath.startsWith("/")) resolvedPath else "/$resolvedPath"
        return "$scheme://$host:$port$normalizedPath"
    }

    /** True when this config is still the shipped placeholder (no real backend set). */
    val isPlaceholder: Boolean
        get() = host == PLACEHOLDER_HOST

    companion object {
        const val SESSION_PLACEHOLDER: String = "{session}"

        // TODO(backend): replace these placeholders with your deployment values.
        const val PLACEHOLDER_HOST = "signaling.invalid.glyphdialer.example"

        private const val DEFAULT_SCHEME = "wss"
        private const val DEFAULT_HOST = PLACEHOLDER_HOST
        private const val DEFAULT_PORT = 443
        private const val DEFAULT_PATH = "/rtc/{session}"

        private val DEFAULT_CLIENT_ID: String =
            "android-" + java.util.UUID.randomUUID().toString().take(8)
    }
}
