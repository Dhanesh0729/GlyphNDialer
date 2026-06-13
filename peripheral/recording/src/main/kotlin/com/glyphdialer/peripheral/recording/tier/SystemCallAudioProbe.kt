// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.tier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes whether THIS device/build will actually let a (default-dialer) app capture the
 * voice-call audio stream — the precondition for [com.glyphdialer.core.domain.model.RecordingTier.SYSTEM_TWO_WAY].
 *
 * HONESTY PRINCIPLE (§2.1): the existence of [MediaRecorder.AudioSource.VOICE_CALL] does
 * NOT mean a third-party app may use it. On stock Android 10+ initializing an
 * [AudioRecord] on a VOICE_CALL source either throws, fails to initialize, or yields
 * silence. The default implementation reflects that reality and returns `false` after a
 * genuine, safe initialization attempt — so the app never *claims* Tier A it can't honor.
 *
 * OEM/system/rooted integrations can replace the Hilt binding with an implementation that
 * returns `true` (e.g. behind a platform signature permission or an OEM SDK).
 */
interface SystemCallAudioProbe {
    /**
     * @return true only if call-audio capture is genuinely available on this build.
     *         Must be safe to call repeatedly and must never throw.
     */
    fun canCaptureCallAudio(): Boolean
}

/**
 * Conservative default probe. Attempts to construct (NOT start) an [AudioRecord] on the
 * VOICE_CALL source and reports success only if the recorder reaches
 * [AudioRecord.STATE_INITIALIZED] without throwing [SecurityException]. On stock devices
 * this returns `false`, which is the honest answer.
 *
 * The probe deliberately does not *start* recording or read frames — that would be
 * intrusive and pointless before an actual call exists. Reaching INITIALIZED on a
 * VOICE_CALL source is the strongest cheap signal that the build honors the source for
 * this app; [SystemCallAudioRecorder] still degrades gracefully at start time if the
 * stream turns out to be silent.
 */
@Singleton
class DefaultSystemCallAudioProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemCallAudioProbe {

    override fun canCaptureCallAudio(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        var recorder: AudioRecord? = null
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                Timber.d("VOICE_CALL probe: invalid min buffer (%d) -> unavailable", minBuffer)
                return false
            }

            @Suppress("MissingPermission") // guarded above
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_CALL,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
            val ok = recorder.state == AudioRecord.STATE_INITIALIZED
            Timber.d("VOICE_CALL probe: AudioRecord initialized=%b", ok)
            ok
        } catch (se: SecurityException) {
            // Expected on stock builds: the source is privileged.
            Timber.d("VOICE_CALL probe: SecurityException -> not available")
            false
        } catch (t: Throwable) {
            Timber.d(t, "VOICE_CALL probe: failed -> not available")
            false
        } finally {
            runCatching { recorder?.release() }
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 8_000 // narrowband is the realistic call-audio rate
    }
}
