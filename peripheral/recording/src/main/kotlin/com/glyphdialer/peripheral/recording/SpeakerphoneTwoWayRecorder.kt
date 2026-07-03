// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.repository.TelecomRepository
import com.glyphdialer.peripheral.recording.internal.AudioFormatSpec
import com.glyphdialer.peripheral.recording.internal.PcmSink
import com.glyphdialer.peripheral.recording.internal.TierCaptureEngine
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

/**
 * SPEAKER_TWO_WAY engine (§2.1) — the honest stock-Android path to recording BOTH sides.
 *
 * It routes the active call to the loudspeaker (via [TelecomRepository]) so the microphone
 * picks up the remote party acoustically, then delegates the actual PCM capture to
 * [LocalSideRecorder]. The result genuinely contains both sides — at lower fidelity than
 * SYSTEM/VoIP capture, and with the call forced onto speaker.
 *
 * HONESTY (§2.1): if the loudspeaker route cannot be engaged (e.g. we don't own an
 * InCallService / aren't the default dialer), [start] THROWS so [CallRecorderImpl] degrades
 * to [RecordingTier.LOCAL_ONE_SIDED] rather than claiming two-way it can't deliver. The
 * prior audio route is restored on [stop].
 *
 * Because the call is on speaker, the recording announcement played by
 * [com.glyphdialer.peripheral.recording.service.RecordingForegroundService] is audible to
 * the other party too — the non-covert, legally-friendly path (§2.2).
 *
 * NOTE: this delegates to a dedicated [LocalSideRecorder] instance (not the one
 * [CallRecorderImpl] uses for the LOCAL_ONE_SIDED tier) — they are never active at once.
 */
class SpeakerphoneTwoWayRecorder @Inject constructor(
    private val local: LocalSideRecorder,
    private val telecomRepository: TelecomRepository,
) : TierCaptureEngine {

    override val tier: RecordingTier = RecordingTier.SPEAKER_TWO_WAY

    override val format: AudioFormatSpec get() = local.format

    override val amplitude: Flow<Float> get() = local.amplitude

    /** The route to restore when recording stops (null until [start] succeeds). */
    private var priorRoute: AudioRoute? = null

    override suspend fun start(sink: PcmSink) {
        val current = telecomRepository.audioState.value.route
        // Engage the loudspeaker so the mic captures the remote party. If this fails we
        // cannot honestly deliver two-way — throw so the caller falls back to local-only.
        when (val result = telecomRepository.setAudioRoute(AudioRoute.SPEAKER)) {
            is AppResult.Success -> priorRoute = current
            is AppResult.Failure -> throw IllegalStateException(
                "Could not route the call to speaker for two-way capture; degrading to local-only",
                result.error,
            )
        }
        Timber.d("SpeakerphoneTwoWayRecorder: routed to SPEAKER (was %s); starting mic capture", current)
        local.start(sink)
    }

    override suspend fun stop() {
        runCatching { local.stop() }.onFailure { Timber.w(it, "local.stop failed") }
        // Restore the route the user had before recording forced speaker.
        priorRoute?.let { route ->
            when (val result = telecomRepository.setAudioRoute(route)) {
                is AppResult.Failure -> Timber.w(result.error, "Could not restore audio route to %s", route)
                is AppResult.Success -> Timber.d("Restored audio route to %s after recording", route)
            }
        }
        priorRoute = null
    }

    override fun release() {
        local.release()
    }
}
