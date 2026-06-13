// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.webrtc

import android.content.Context
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.repository.WebRtcClient
import com.glyphdialer.core.domain.repository.WebRtcState
import com.glyphdialer.peripheral.webrtc.signaling.SignalingClient
import com.glyphdialer.peripheral.webrtc.signaling.SignalingMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production [WebRtcClient] (BUILD_SPEC §11). Owns a single [PeerConnection] for an
 * in-app VoIP session, a local audio track that is always present, and an OPTIONAL
 * local video track for the "switch to video" upgrade.
 *
 * HONESTY PRINCIPLE (§2.3/§9): this is in-app WebRTC VoIP video ONLY. It cannot
 * upgrade a cellular call and only works between two participants reachable on the
 * configured signaling backend. Video availability is surfaced via [remoteHasVideo]
 * and the [WebRtcState.VIDEO] state; the camera is never enabled silently —
 * [addVideo] renegotiates and a [SignalingMessage.VideoRequest] prompts the remote
 * party to accept.
 *
 * Threading: all WebRTC and signaling work is confined to [scope] (the injected IO
 * dispatcher). The PeerConnection observer callbacks arrive on WebRTC's signaling
 * thread and are bounced onto [scope] before touching state.
 */
@Singleton
class WebRtcClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val eglBase: EglBase,
    private val signalingClient: SignalingClient,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    private val turnCredentials: IceConfig.TurnCredentials?,
) : WebRtcClient {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /** Serializes negotiation so an offer/answer is never interleaved. */
    private val negotiationMutex = Mutex()

    private val _state = MutableStateFlow(WebRtcState.IDLE)
    override val state: StateFlow<WebRtcState> = _state.asStateFlow()

    private val _remoteHasVideo = MutableStateFlow(false)
    override val remoteHasVideo: StateFlow<Boolean> = _remoteHasVideo.asStateFlow()

    // --- WebRTC objects (created in connect, torn down in disconnect) ---
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null

    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var cameraIsFront = true

    private var currentSessionId: String? = null

    // -------------------------------------------------------------------------
    // WebRtcClient API
    // -------------------------------------------------------------------------

    override suspend fun connect(sessionId: String): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                Timber.d("WebRTC connect: session=%s", sessionId)
                _state.value = WebRtcState.CONNECTING
                currentSessionId = sessionId

                createPeerConnection()
                ensureLocalAudioTrack()

                // Begin signaling and start fanning inbound messages into the PC.
                observeSignaling()
                signalingClient.connect(sessionId).throwOnFailure("signaling connect")
            }
        }

    override suspend fun createOffer(): AppResult<Unit> = appResultOfSuspend {
        negotiationMutex.withLock { negotiateAsCaller() }
    }

    override suspend fun createAnswer(): AppResult<Unit> = appResultOfSuspend {
        // The actual answer is produced reactively when a remote OFFER arrives (see
        // [handleRemoteOffer]); explicit invocation is a no-op acknowledgement so the
        // callee-side flow matches the interface. We surface a clear log rather than
        // faking an answer with no offer in hand (§9).
        val pc = peerConnection
        if (pc?.remoteDescription?.type == SessionDescription.Type.OFFER) {
            negotiationMutex.withLock { answerPendingOffer(pc) }
        } else {
            Timber.d("createAnswer() called with no pending remote offer; awaiting offer")
        }
    }

    override suspend fun addVideo(): AppResult<Unit> = appResultOfSuspend {
        val session = currentSessionId ?: error("Not connected")
        val capturer = createCameraCapturer()
            ?: error("No camera available for in-app video (§9: video unavailable)")

        withContext(ioDispatcher) {
            // Prompt the remote party to accept video before we light the camera (§11).
            signalingClient.send(SignalingMessage.VideoRequest(session))
                .throwOnFailure("video-request")

            startLocalVideo(capturer)
            negotiationMutex.withLock { negotiateAsCaller() }
            _state.value = WebRtcState.VIDEO
        }
    }

    override suspend fun removeVideo(): AppResult<Unit> = appResultOfSuspend {
        withContext(ioDispatcher) {
            stopLocalVideo()
            negotiationMutex.withLock { negotiateAsCaller() }
            // Back to plain connected audio (unless the remote still sends video).
            _state.value = if (_remoteHasVideo.value) WebRtcState.VIDEO else WebRtcState.CONNECTED
        }
    }

    override suspend fun setAudioEnabled(enabled: Boolean): AppResult<Unit> =
        appResultOfSuspend {
            localAudioTrack?.setEnabled(enabled)
            Timber.d("Local audio enabled=%s", enabled)
        }

    override suspend fun switchCamera(): AppResult<Unit> = appResultOfSuspend {
        val capturer = videoCapturer as? CameraVideoCapturer
            ?: error("No active camera to switch")
        suspendCancellableCoroutine { cont ->
            capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    cameraIsFront = isFrontCamera
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onCameraSwitchError(errorDescription: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Camera switch failed: $errorDescription"),
                        )
                    }
                }
            })
        }
    }

    override suspend fun disconnect(): AppResult<Unit> = appResultOfSuspend {
        withContext(ioDispatcher) {
            Timber.d("WebRTC disconnect")
            currentSessionId?.let { session ->
                runCatching { signalingClient.send(SignalingMessage.Bye(session)) }
            }
            signalingClient.close()
            teardownMedia()
            peerConnection?.dispose()
            peerConnection = null
            currentSessionId = null
            _remoteHasVideo.value = false
            _state.value = WebRtcState.DISCONNECTED
        }
    }

    // -------------------------------------------------------------------------
    // Peer connection & tracks
    // -------------------------------------------------------------------------

    private fun createPeerConnection() {
        val rtcConfig = IceConfig.rtcConfiguration(turnCredentials)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, pcObserver)
            ?: error("PeerConnectionFactory.createPeerConnection returned null")
    }

    private fun ensureLocalAudioTrack() {
        if (localAudioTrack != null) return
        val audioConstraints = MediaConstraints().apply {
            // Standard VoIP audio processing.
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        val track = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        track.setEnabled(true)
        localAudioTrack = track
        peerConnection?.addTrack(track, listOf(STREAM_ID))
    }

    /**
     * Build a WebRTC [Camera2Capturer], preferring the front camera. Returns null
     * when the device has no usable camera (§9: caller must treat video as
     * unavailable rather than pretend).
     */
    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        if (deviceNames.isEmpty()) {
            Timber.w("No camera devices present; in-app video unavailable")
            return null
        }
        // Prefer front camera for a video call.
        deviceNames.firstOrNull { enumerator.isFrontFacing(it) }?.let { name ->
            cameraIsFront = true
            return enumerator.createCapturer(name, null)
        }
        // Fall back to any camera.
        deviceNames.firstOrNull { enumerator.isBackFacing(it) }?.let { name ->
            cameraIsFront = false
            return enumerator.createCapturer(name, null)
        }
        cameraIsFront = false
        return enumerator.createCapturer(deviceNames.first(), null)
    }

    private fun startLocalVideo(capturer: VideoCapturer) {
        if (localVideoTrack != null) return

        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val source = peerConnectionFactory.createVideoSource(capturer.isScreencast)
        capturer.initialize(helper, context, source.capturerObserver)
        capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

        val track = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, source)
        track.setEnabled(true)

        surfaceTextureHelper = helper
        videoSource = source
        videoCapturer = capturer
        localVideoTrack = track

        peerConnection?.addTrack(track, listOf(STREAM_ID))
        Timber.d("Local video track started (front=%s)", cameraIsFront)
    }

    private fun stopLocalVideo() {
        val track = localVideoTrack ?: return
        // Remove the sender carrying our video track so renegotiation drops it.
        peerConnection?.senders
            ?.firstOrNull { it.track()?.id() == track.id() }
            ?.let { peerConnection?.removeTrack(it) }

        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoSource?.dispose()
        surfaceTextureHelper?.dispose()
        track.dispose()

        videoCapturer = null
        videoSource = null
        surfaceTextureHelper = null
        localVideoTrack = null
        Timber.d("Local video track stopped")
    }

    private fun teardownMedia() {
        stopLocalVideo()
        localAudioTrack?.dispose()
        localAudioTrack = null
    }

    // -------------------------------------------------------------------------
    // Negotiation (createOffer → setLocal → signal → setRemote)
    // -------------------------------------------------------------------------

    private suspend fun negotiateAsCaller() {
        val pc = peerConnection ?: error("No peer connection")
        val session = currentSessionId ?: error("No session")

        val offer = pc.createOfferSuspend(offerAnswerConstraints())
        pc.setLocalDescriptionSuspend(offer)
        signalingClient.send(SignalingMessage.Offer(session, offer.description))
            .throwOnFailure("send offer")
        Timber.d("Sent SDP offer (renegotiation)")
    }

    private suspend fun answerPendingOffer(pc: PeerConnection) {
        val session = currentSessionId ?: error("No session")
        val answer = pc.createAnswerSuspend(offerAnswerConstraints())
        pc.setLocalDescriptionSuspend(answer)
        signalingClient.send(SignalingMessage.Answer(session, answer.description))
            .throwOnFailure("send answer")
        Timber.d("Sent SDP answer")
    }

    private fun offerAnswerConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    // -------------------------------------------------------------------------
    // Inbound signaling
    // -------------------------------------------------------------------------

    private fun observeSignaling() {
        signalingClient.incoming
            .onEach { message -> handleSignalingMessage(message) }
            .launchIn(scope)

        signalingClient.connectionEvents
            .onEach { event ->
                when (event) {
                    is SignalingClient.ConnectionEvent.Error -> {
                        Timber.w(event.cause, "Signaling error → state FAILED")
                        if (_state.value != WebRtcState.DISCONNECTED) {
                            _state.value = WebRtcState.FAILED
                        }
                    }
                    is SignalingClient.ConnectionEvent.Closed -> {
                        if (_state.value != WebRtcState.DISCONNECTED) {
                            _state.value = WebRtcState.RECONNECTING
                        }
                    }
                    is SignalingClient.ConnectionEvent.Open -> Unit
                }
            }
            .launchIn(scope)
    }

    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        val pc = peerConnection ?: return
        when (message) {
            is SignalingMessage.PeerJoined -> {
                // We are the caller — start the offer once the peer is present.
                Timber.d("Peer joined; creating offer")
                negotiationMutex.withLock { negotiateAsCaller() }
            }

            is SignalingMessage.Offer -> {
                negotiationMutex.withLock { handleRemoteOffer(pc, message.sdp) }
            }

            is SignalingMessage.Answer -> {
                val desc = SessionDescription(SessionDescription.Type.ANSWER, message.sdp)
                pc.setRemoteDescriptionSuspend(desc)
                Timber.d("Applied remote SDP answer")
            }

            is SignalingMessage.IceCandidate -> {
                pc.addIceCandidate(
                    IceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate),
                )
            }

            is SignalingMessage.VideoRequest -> {
                // The remote wants to add video. The actual accept/decline is a UI
                // decision; we simply note that an upgrade is being requested. The
                // in-call layer should call addVideo() (to send our own video) once
                // the user accepts. Honesty: we never auto-enable the camera here.
                Timber.d("Remote requested video upgrade (awaiting user decision)")
            }

            is SignalingMessage.VideoResponse -> {
                Timber.d("Remote video response: accepted=%s", message.accepted)
            }

            is SignalingMessage.PeerLeft,
            is SignalingMessage.Bye,
            -> {
                Timber.d("Remote left session")
                _state.value = WebRtcState.DISCONNECTED
            }

            is SignalingMessage.Join -> Unit // server-bound; ignore if echoed back
        }
    }

    private suspend fun handleRemoteOffer(pc: PeerConnection, sdp: String) {
        val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescriptionSuspend(desc)
        answerPendingOffer(pc)
    }

    // -------------------------------------------------------------------------
    // PeerConnection observer
    // -------------------------------------------------------------------------

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            // Trickle the candidate to the remote via signaling.
            val session = currentSessionId ?: return
            scope.launch {
                signalingClient.send(
                    SignalingMessage.IceCandidate(
                        sessionId = session,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        candidate = candidate.sdp,
                    ),
                )
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            scope.launch {
                val mapped = when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTING ->
                        WebRtcState.CONNECTING
                    PeerConnection.PeerConnectionState.CONNECTED ->
                        if (_remoteHasVideo.value || localVideoTrack != null) {
                            WebRtcState.VIDEO
                        } else {
                            WebRtcState.CONNECTED
                        }
                    PeerConnection.PeerConnectionState.DISCONNECTED ->
                        WebRtcState.RECONNECTING
                    PeerConnection.PeerConnectionState.FAILED ->
                        WebRtcState.FAILED
                    PeerConnection.PeerConnectionState.CLOSED ->
                        WebRtcState.DISCONNECTED
                    PeerConnection.PeerConnectionState.NEW -> WebRtcState.IDLE
                }
                Timber.d("PeerConnection state=%s → %s", newState, mapped)
                _state.value = mapped
            }
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {
            val track = receiver?.track() ?: return
            if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                Timber.d("Remote video track added")
                _remoteHasVideo.value = true
                scope.launch {
                    if (_state.value == WebRtcState.CONNECTED) _state.value = WebRtcState.VIDEO
                }
            }
        }

        override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
            val track = transceiver?.receiver?.track() ?: return
            if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                _remoteHasVideo.value = transceiver.currentDirection?.let {
                    it == org.webrtc.RtpTransceiver.RtpTransceiverDirection.SEND_RECV ||
                        it == org.webrtc.RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                } ?: true
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            val track = receiver?.track() ?: return
            if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                Timber.d("Remote video track removed")
                _remoteHasVideo.value = false
                scope.launch {
                    if (_state.value == WebRtcState.VIDEO && localVideoTrack == null) {
                        _state.value = WebRtcState.CONNECTED
                    }
                }
            }
        }

        override fun onRenegotiationNeeded() {
            // We drive renegotiation explicitly from addVideo()/removeVideo(); no-op.
            Timber.v("onRenegotiationNeeded (handled explicitly)")
        }

        // Remaining callbacks are unused but must be implemented.
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
        override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) = Unit
    }

    /** Releases the scope (call only when the singleton is being torn down). */
    fun shutdown() {
        scope.cancel()
    }

    // -------------------------------------------------------------------------
    // Suspend bridges over the callback-based WebRTC SDP API
    // -------------------------------------------------------------------------

    private suspend fun PeerConnection.createOfferSuspend(
        constraints: MediaConstraints,
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        createOffer(sdpCreateObserver(cont), constraints)
    }

    private suspend fun PeerConnection.createAnswerSuspend(
        constraints: MediaConstraints,
    ): SessionDescription = suspendCancellableCoroutine { cont ->
        createAnswer(sdpCreateObserver(cont), constraints)
    }

    private suspend fun PeerConnection.setLocalDescriptionSuspend(desc: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            setLocalDescription(sdpSetObserver(cont), desc)
        }

    private suspend fun PeerConnection.setRemoteDescriptionSuspend(desc: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            setRemoteDescription(sdpSetObserver(cont), desc)
        }

    private fun sdpCreateObserver(
        cont: kotlinx.coroutines.CancellableContinuation<SessionDescription>,
    ) = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {
            if (cont.isActive) cont.resume(desc)
        }

        override fun onCreateFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(SdpException("create", error))
        }

        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    private fun sdpSetObserver(
        cont: kotlinx.coroutines.CancellableContinuation<Unit>,
    ) = object : SdpObserver {
        override fun onSetSuccess() {
            if (cont.isActive) cont.resume(Unit)
        }

        override fun onSetFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(SdpException("set", error))
        }

        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onCreateFailure(error: String?) = Unit
    }

    private fun AppResult<Unit>.throwOnFailure(what: String) {
        if (this is AppResult.Failure) {
            throw IllegalStateException("Signaling failed ($what): ${message ?: error.message}", error)
        }
    }

    private class SdpException(phase: String, detail: String?) :
        IllegalStateException("SDP $phase failed: ${detail ?: "unknown"}")

    private companion object {
        const val STREAM_ID = "glyph-stream"
        const val AUDIO_TRACK_ID = "glyph-audio"
        const val VIDEO_TRACK_ID = "glyph-video"
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        const val VIDEO_FPS = 30
    }
}
