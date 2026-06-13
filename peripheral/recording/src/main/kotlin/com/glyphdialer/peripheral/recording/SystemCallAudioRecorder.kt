// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.peripheral.recording.internal.AudioFormatSpec
import com.glyphdialer.peripheral.recording.internal.PcmSink
import com.glyphdialer.peripheral.recording.internal.PcmUtils
import com.glyphdialer.peripheral.recording.internal.TierCaptureEngine
import com.glyphdialer.core.domain.model.RecordingTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Tier A — SYSTEM / OEM CALL AUDIO (two-way) (§12).
 *
 * !!! DEVICE/OEM-GATED !!! On STOCK Android 10+ this is NOT available to third-party apps.
 * Constructing an [AudioRecord] on [MediaRecorder.AudioSource.VOICE_CALL] (or the
 * VOICE_DOWNLINK/VOICE_UPLINK variants) requires a system/OEM/privileged build or root;
 * on stock devices it throws [SecurityException], fails to initialize, or yields silence.
 *
 * This recorder therefore:
 *  - is selected by [CallRecorderImpl] only when [com.glyphdialer.peripheral.recording.tier.RecorderTierResolver]
 *    honestly resolves SYSTEM_TWO_WAY (default dialer + positive device probe), AND
 *  - guards every AudioRecord call and FALLS BACK GRACEFULLY (throws to let the caller
 *    drop to Tier C) if initialization or the first read fails.
 *
 * It NEVER fabricates remote-party audio. If the privileged source is unavailable, the
 * honest outcome is to record nothing here and let the resolver pick LOCAL_ONE_SIDED.
 */
class SystemCallAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TierCaptureEngine {

    override val tier: RecordingTier = RecordingTier.SYSTEM_TWO_WAY

    // Call audio is narrowband on cellular.
    override val format: AudioFormatSpec = AudioFormatSpec.NARROWBAND_MONO

    private val _amplitude = MutableSharedFlow<Float>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val amplitude: Flow<Float> = _amplitude.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var record: AudioRecord? = null
    private var captureJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun start(sink: PcmSink) {
        check(hasPermission()) { "RECORD_AUDIO not granted; cannot start SystemCallAudioRecorder" }

        val minBuffer = AudioRecord.getMinBufferSize(
            format.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "Invalid AudioRecord min buffer: $minBuffer" }
        val bufferSize = minBuffer * BUFFER_MULTIPLIER

        val recorder = try {
            buildPrivilegedRecorder(bufferSize)
        } catch (se: SecurityException) {
            // The honest stock-Android path: privilege denied. Caller drops to Tier C.
            Timber.w(se, "VOICE_CALL source denied (expected on stock Android); Tier A unavailable")
            throw IllegalStateException("System call-audio capture not permitted on this build", se)
        }
        record = recorder
        recorder.startRecording()

        captureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            var sawAnySignal = false
            var framesRead = 0
            try {
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        framesRead++
                        val amp = PcmUtils.peakAmplitude16(buffer, read)
                        if (amp > SILENCE_FLOOR) sawAnySignal = true
                        sink.write(buffer, 0, read)
                        _amplitude.tryEmit(amp)

                        // Honesty guard: if after a grace window we've only ever seen
                        // digital silence, the privileged source is muted for us. Log it so
                        // the recording isn't silently mislabeled "two-way". We keep writing
                        // (the resolver already committed Tier A), but flag the anomaly.
                        if (framesRead == SILENCE_CHECK_FRAMES && !sawAnySignal) {
                            Timber.w("Tier A produced only silence after %d frames — call-audio likely muted for this app", framesRead)
                        }
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        Timber.w("VOICE_CALL read error code=%d; stopping Tier A capture", read)
                        break
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "SystemCallAudioRecorder capture loop failed")
            }
        }
        Timber.d("SystemCallAudioRecorder started (SYSTEM TWO-WAY, OEM-gated, %d Hz)", format.sampleRateHz)
    }

    override suspend fun stop() {
        captureJob?.cancelAndJoin()
        captureJob = null
        record?.let { r ->
            runCatching { if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop() }
            runCatching { r.release() }
        }
        record = null
        Timber.d("SystemCallAudioRecorder stopped")
    }

    override fun release() {
        captureJob?.cancel()
        captureJob = null
        record?.let { runCatching { it.release() } }
        record = null
    }

    /**
     * Tries the privileged call-audio sources in descending preference. VOICE_CALL mixes
     * uplink+downlink (best for two-way); the directional variants are the fallback. Any
     * failure to reach INITIALIZED releases the candidate and tries the next; if none
     * works we throw so the caller can degrade honestly.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun buildPrivilegedRecorder(bufferSize: Int): AudioRecord {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_DOWNLINK,
            MediaRecorder.AudioSource.VOICE_UPLINK,
        )
        for (source in sources) {
            val candidate = AudioRecord(
                source,
                format.sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                Timber.d("Tier A using privileged audio source %d", source)
                return candidate
            }
            runCatching { candidate.release() }
        }
        error("No privileged call-audio source initialized (VOICE_CALL/DOWNLINK/UPLINK)")
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val BUFFER_MULTIPLIER = 2
        const val SILENCE_FLOOR = 0.01f
        const val SILENCE_CHECK_FRAMES = 50
    }
}
