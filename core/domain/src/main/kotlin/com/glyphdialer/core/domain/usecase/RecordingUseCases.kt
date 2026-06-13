// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.Recording
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.repository.RecordingRepository
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.core.domain.repository.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Start/stop call recording at the highest honestly-supported tier (spec sections 2.1/12).
 *
 * HONESTY PRINCIPLE: this use case never claims two-way capture. It first checks
 * the user's recording-enabled preference, then resolves the actual tier; if the
 * tier is [RecordingTier.UNAVAILABLE] it fails with a clear message rather than
 * pretending to record.
 */
class RecordCallUseCase @Inject constructor(
    private val recordings: RecordingRepository,
    private val settings: SettingsRepository,
) {
    /** Start recording [call]. Returns the [Recording] (carrying its honest tier). */
    suspend fun start(call: CallModel): AppResult<Recording> {
        val prefs = when (val current = settings.current()) {
            is AppResult.Success -> current.data
            is AppResult.Failure -> return AppResult.Failure(current.error, current.message)
        }
        if (!prefs.recordingEnabled) {
            return AppResult.Failure(
                IllegalStateException("recording disabled"),
                "Call recording is turned off in Settings.",
            )
        }
        val tier = recordings.resolveTier(call)
        if (tier == RecordingTier.UNAVAILABLE) {
            return AppResult.Failure(
                UnsupportedOperationException("recording unavailable"),
                "Recording isn't available on this device for this call.",
            )
        }
        return recordings.startRecording(call)
    }

    /** Stop the in-progress recording. */
    suspend fun stop(): AppResult<Recording?> = recordings.stopRecording()

    /** Convenience invoke: start when [start] is true, else stop. */
    suspend operator fun invoke(call: CallModel, start: Boolean): AppResult<Recording?> =
        if (start) start(call) else stop()
}

/** Observe stored recordings, newest first (spec section 12). */
class GetRecordingsUseCase @Inject constructor(
    private val recordings: RecordingRepository,
) {
    operator fun invoke(): Flow<List<Recording>> = recordings.observeRecordings()
}

/**
 * Transcribe a stored recording (spec section 13). The resulting [Transcript] carries
 * [Transcript.isLocalSideOnly] so the UI can be honest about cellular limits (spec section 2.4).
 */
class TranscribeCallUseCase @Inject constructor(
    private val transcripts: TranscriptRepository,
) {
    suspend operator fun invoke(recordingId: String): AppResult<Transcript> =
        transcripts.transcribeRecording(recordingId)
}
