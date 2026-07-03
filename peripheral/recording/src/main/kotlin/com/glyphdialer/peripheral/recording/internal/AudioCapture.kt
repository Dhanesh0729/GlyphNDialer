// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.internal

import com.glyphdialer.core.domain.model.RecordingTier
import kotlinx.coroutines.flow.Flow

/**
 * Canonical PCM audio format shared by every tier recorder so the downstream pipeline
 * (encryption, WAV header, level metering) is uniform. 16-bit signed little-endian PCM.
 */
data class AudioFormatSpec(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitsPerSample: Int = 16,
) {
    val bytesPerFrame: Int get() = channelCount * (bitsPerSample / 8)
    val byteRate: Int get() = sampleRateHz * bytesPerFrame

    companion object {
        /** Narrowband mono — the realistic rate for cellular call audio (Tier A/C). */
        val NARROWBAND_MONO = AudioFormatSpec(sampleRateHz = 8_000, channelCount = 1)

        /** Wideband mono — used for local mic (VOICE_COMMUNICATION) and VoIP downmix. */
        val WIDEBAND_MONO = AudioFormatSpec(sampleRateHz = 16_000, channelCount = 1)
    }
}

/**
 * Common surface for a single-tier capture engine. The [CallRecorderImpl] selects one
 * of these per call based on the resolved [RecordingTier] and drives its lifecycle.
 *
 * Implementations write raw PCM frames to the supplied [PcmSink] (which the
 * [com.glyphdialer.peripheral.recording.storage.EncryptedRecordingStore] wraps) and emit
 * normalized amplitude (0f..1f) for the waveform/Glyph mirror.
 */
interface TierCaptureEngine {

    /** The tier this engine implements (for honest reporting). */
    val tier: RecordingTier

    /** Normalized amplitude stream (0f..1f), throttled by the consumer. */
    val amplitude: Flow<Float>

    /** The PCM format this engine produces (so the WAV header is correct). */
    val format: AudioFormatSpec

    /**
     * Begin capture, writing PCM frames to [sink]. Suspends until capture has started
     * (or fails fast). Throws on unrecoverable init failure so the caller can fall back
     * to a lower tier.
     */
    suspend fun start(sink: PcmSink)

    /** Stop capture and flush. Idempotent. */
    suspend fun stop()

    /** Release any held audio resources. Safe when idle. */
    fun release()
}

/**
 * A write target for raw PCM frames. The encrypted store implements this; tier engines
 * only know how to push bytes. Keeping it tiny lets the VoIP tier feed externally-sourced
 * PCM (from :peripheral:webrtc) through the same path.
 */
interface PcmSink {
    /** Append [length] bytes from [data] starting at [offset]. */
    suspend fun write(data: ByteArray, offset: Int, length: Int)
}
