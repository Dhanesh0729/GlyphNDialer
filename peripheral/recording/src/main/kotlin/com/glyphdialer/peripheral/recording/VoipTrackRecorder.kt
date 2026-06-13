// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording

import com.glyphdialer.peripheral.recording.internal.AudioFormatSpec
import com.glyphdialer.peripheral.recording.internal.PcmSink
import com.glyphdialer.peripheral.recording.internal.PcmUtils
import com.glyphdialer.peripheral.recording.internal.TierCaptureEngine
import com.glyphdialer.core.domain.model.RecordingTier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier B — VOIP / WEBRTC TWO-WAY (§12). For in-app calls we OWN both media tracks, so we
 * can record full two-way audio at full quality, honestly.
 *
 * This module does NOT depend on `:peripheral:webrtc` (the dependency graph in
 * CONVENTIONS.md §3 only allows :core:domain/:core:common). Instead we expose a SINK API:
 * the WebRTC module is given this recorder as an [VoipPcmFeed] and pushes already-mixed
 * local+remote PCM frames in. That keeps the WebRTC dependency one-directional and avoids
 * a cycle (webrtc -> recording would conflict with recording -> webrtc).
 *
 * Integration contract (for :peripheral:webrtc / :app wiring):
 *  1. Resolve this recorder via Hilt and call [configure] with the negotiated PCM format
 *     (typically 48 kHz mono after WebRTC downmix, or [AudioFormatSpec.WIDEBAND_MONO]).
 *  2. From the WebRTC audio sink callback, call [feed] with mixed PCM frames.
 *  3. [CallRecorderImpl] drives [start]/[stop]; [start] simply opens the gate so fed
 *     frames are written, [stop] closes it.
 *
 * Until a WebRTC producer is wired, [feed] is simply never called and the recording is an
 * empty (but valid) WAV — never a fake.
 */
@Singleton
class VoipTrackRecorder @Inject constructor() : TierCaptureEngine, VoipPcmFeed {

    override val tier: RecordingTier = RecordingTier.VOIP_TWO_WAY

    // Default to wideband mono; overridable via configure() once WebRTC negotiates.
    override var format: AudioFormatSpec = AudioFormatSpec.WIDEBAND_MONO
        private set

    private val _amplitude = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val amplitude: Flow<Float> = _amplitude.asSharedFlow()

    private val gateOpen = AtomicBoolean(false)

    @Volatile
    private var sink: PcmSink? = null

    /** Set the PCM format the WebRTC pipeline will deliver (call before [start]). */
    fun configure(format: AudioFormatSpec) {
        this.format = format
        Timber.d("VoipTrackRecorder configured: %d Hz, %d ch", format.sampleRateHz, format.channelCount)
    }

    override suspend fun start(sink: PcmSink) {
        this.sink = sink
        gateOpen.set(true)
        Timber.d("VoipTrackRecorder armed (VOIP TWO-WAY); awaiting fed PCM frames")
        // TODO(webrtc): :peripheral:webrtc must call feed() with mixed local+remote PCM.
        // No producer attached yet => no fabricated audio; the file stays empty but valid.
    }

    override suspend fun stop() {
        gateOpen.set(false)
        sink = null
        Timber.d("VoipTrackRecorder disarmed")
    }

    override fun release() {
        gateOpen.set(false)
        sink = null
    }

    // --- VoipPcmFeed (called by :peripheral:webrtc) ---

    override fun feed(data: ByteArray, offset: Int, length: Int) {
        if (!gateOpen.get()) return
        val target = sink ?: return
        // The feed callback is on the WebRTC audio thread; the sink write is cheap
        // (buffered file append) so we invoke it directly. PcmSink.write is suspend, but
        // we bridge synchronously here via runCatching + a non-suspending fast path on the
        // store; if a future sink truly needs suspension this becomes a channel.
        runCatching {
            kotlinx.coroutines.runBlocking { target.write(data, offset, length) }
            _amplitude.tryEmit(PcmUtils.peakAmplitude16(data, length, offset))
        }.onFailure { Timber.w(it, "VoipTrackRecorder.feed write failed") }
    }

    override fun isArmed(): Boolean = gateOpen.get()
}

/**
 * The push API the WebRTC module uses to deliver mixed local+remote PCM into the Tier B
 * recorder. Declared here (in :peripheral:recording) and consumed by :peripheral:webrtc,
 * preserving the one-way dependency edge (webrtc -> recording is NOT in the graph; instead
 * :app hands the webrtc client this feed).
 */
interface VoipPcmFeed {
    /** True while the recorder is armed and will accept frames. */
    fun isArmed(): Boolean

    /**
     * Push [length] bytes of 16-bit PCM (matching the configured format) from [data] at
     * [offset]. No-op when not armed. Must be cheap and non-blocking from the audio thread.
     */
    fun feed(data: ByteArray, offset: Int, length: Int)
}
