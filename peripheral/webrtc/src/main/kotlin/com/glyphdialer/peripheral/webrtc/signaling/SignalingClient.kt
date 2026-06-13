// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.webrtc.signaling

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ktor WebSocket signaling client (BUILD_SPEC §11). Exchanges SDP offers/answers and
 * trickled ICE candidates (the [SignalingMessage] protocol) with the configured
 * backend.
 *
 * Lifecycle: [connect] opens the socket and starts a receive loop that decodes
 * frames into [SignalingMessage]s and republishes them on [incoming]; [send]
 * serializes and writes a message; [close] tears the socket down. The class is
 * transport-only — it knows nothing about peer connections (that is
 * [com.glyphdialer.peripheral.webrtc.WebRtcClientImpl]'s job).
 *
 * Threading: all socket I/O is confined to the injected IO dispatcher via [scope].
 */
@Singleton
class SignalingClient @Inject constructor(
    private val httpClient: HttpClient,
    private val config: SignalingConfig,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    private val scope = CoroutineScope(ioDispatcher)
    private val socketMutex = Mutex()

    private var session: DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null

    private val _incoming = MutableSharedFlow<SignalingMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Decoded inbound signaling messages from the backend. */
    val incoming: SharedFlow<SignalingMessage> = _incoming.asSharedFlow()

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Transport-level events (open/closed/error) for the client to react to. */
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()

    /** Whether a socket is currently open. */
    val isConnected: Boolean
        get() = session?.isActive == true

    /**
     * Open the WebSocket for [sessionId] and start receiving. Idempotent: if a
     * socket is already open it is closed first.
     */
    suspend fun connect(sessionId: String): AppResult<Unit> = socketMutex.withLock {
        appResultOfSuspend {
            closeInternal(reasonForLog = "reconnect")

            if (config.isPlaceholder) {
                // §9 honesty: do not pretend a backend exists. Surface loudly.
                Timber.w(
                    "SignalingConfig is still the placeholder backend (%s). " +
                        "Set a real SignalingConfig — see TODO(backend).",
                    config.host,
                )
            }

            val url = config.urlFor(sessionId)
            Timber.d("Opening signaling socket: %s", url)

            val newSession = httpClient.webSocketSession(urlString = url) {
                config.authToken?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            session = newSession

            receiveJob = scope.launch { receiveLoop(newSession) }

            _connectionEvents.tryEmit(ConnectionEvent.Open(sessionId))

            // Announce ourselves to the room.
            sendInternal(SignalingMessage.Join(sessionId = sessionId, from = config.clientId))
        }
    }

    /** Serialize and send [message] over the open socket. */
    suspend fun send(message: SignalingMessage): AppResult<Unit> = socketMutex.withLock {
        appResultOfSuspend { sendInternal(message) }
    }

    private suspend fun sendInternal(message: SignalingMessage) {
        val active = session ?: error("Signaling socket is not open")
        val text = json.encodeToString(SignalingMessage.serializer(), message)
        active.send(text)
        Timber.v("→ signaling: %s", message::class.simpleName)
    }

    private suspend fun receiveLoop(active: DefaultClientWebSocketSession) {
        try {
            for (frame in active.incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                val message = runCatching {
                    json.decodeFromString(SignalingMessage.serializer(), text)
                }.getOrElse { err ->
                    Timber.w(err, "Dropping un-decodable signaling frame")
                    null
                } ?: continue
                Timber.v("← signaling: %s", message::class.simpleName)
                _incoming.emit(message)
            }
            Timber.d("Signaling receive loop ended (socket closed by peer)")
            _connectionEvents.tryEmit(ConnectionEvent.Closed(graceful = true))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Timber.w(t, "Signaling receive loop failed")
            _connectionEvents.tryEmit(ConnectionEvent.Error(t))
        }
    }

    /** Close the socket and stop receiving. Safe to call when already closed. */
    suspend fun close(): AppResult<Unit> = socketMutex.withLock {
        appResultOfSuspend { closeInternal(reasonForLog = "explicit close") }
    }

    private suspend fun closeInternal(reasonForLog: String) {
        receiveJob?.cancelAndJoin()
        receiveJob = null
        session?.let { active ->
            runCatching { active.close() }
                .onFailure { Timber.w(it, "Error closing signaling socket (%s)", reasonForLog) }
        }
        if (session != null) {
            Timber.d("Signaling socket closed (%s)", reasonForLog)
            _connectionEvents.tryEmit(ConnectionEvent.Closed(graceful = true))
        }
        session = null
    }

    /** Transport-level signaling events. */
    sealed interface ConnectionEvent {
        data class Open(val sessionId: String) : ConnectionEvent
        data class Closed(val graceful: Boolean) : ConnectionEvent
        data class Error(val cause: Throwable) : ConnectionEvent
    }
}
