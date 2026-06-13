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
 * Tier C — LOCAL SIDE ONLY (§12). Records the LOCAL microphone with
 * [MediaRecorder.AudioSource.VOICE_COMMUNICATION] (echo-cancelled/processed for calls),
 * falling back to [MediaRecorder.AudioSource.MIC]. This captures the user's own voice and
 * whatever the mic picks up acoustically — it does NOT capture the remote party's audio
 * stream. The UI MUST label this "my side only" and never present it as a full call
 * recording (honesty principle §2.1).
 *
 * Implemented with [AudioRecord] (not MediaRecorder) so we get raw PCM frames for live
 * amplitude metering and uniform encryption, rather than an opaque container.
 */
class LocalSideRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TierCaptureEngine {

    override val tier: RecordingTier = RecordingTier.LOCAL_ONE_SIDED

    override val format: AudioFormatSpec = AudioFormatSpec.WIDEBAND_MONO

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
        check(hasPermission()) { "RECORD_AUDIO not granted; cannot start LocalSideRecorder" }

        val minBuffer = AudioRecord.getMinBufferSize(
            format.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "Invalid AudioRecord min buffer: $minBuffer" }
        val bufferSize = minBuffer * BUFFER_MULTIPLIER

        val recorder = buildRecorder(bufferSize)
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize (state=${recorder.state})"
        }
        record = recorder
        recorder.startRecording()

        captureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            try {
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        sink.write(buffer, 0, read)
                        _amplitude.tryEmit(PcmUtils.peakAmplitude16(buffer, read))
                    } else if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        Timber.w("AudioRecord.read error code=%d; stopping local capture", read)
                        break
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "LocalSideRecorder capture loop failed")
            }
        }
        Timber.d("LocalSideRecorder started (LOCAL ONE-SIDED, %d Hz)", format.sampleRateHz)
    }

    override suspend fun stop() {
        captureJob?.cancelAndJoin()
        captureJob = null
        record?.let { r ->
            runCatching { if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop() }
            runCatching { r.release() }
        }
        record = null
        Timber.d("LocalSideRecorder stopped")
    }

    override fun release() {
        captureJob?.cancel()
        captureJob = null
        record?.let { runCatching { it.release() } }
        record = null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun buildRecorder(bufferSize: Int): AudioRecord {
        // Prefer VOICE_COMMUNICATION (call-tuned: AEC/NS). Fall back to MIC if the device
        // can't initialize it.
        for (source in intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )) {
            val candidate = AudioRecord(
                source,
                format.sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                Timber.d("LocalSideRecorder using audio source %d", source)
                return candidate
            }
            runCatching { candidate.release() }
        }
        error("No usable local audio source (VOICE_COMMUNICATION/MIC) on this device")
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val BUFFER_MULTIPLIER = 2
    }
}
