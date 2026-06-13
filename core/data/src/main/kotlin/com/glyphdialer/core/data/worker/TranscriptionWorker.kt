// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glyphdialer.core.common.fold
import com.glyphdialer.core.domain.repository.TranscriptRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Background transcription of a finished recording (BUILD_SPEC §13/§19). Delegates
 * to [TranscriptRepository], which in turn drives the injected
 * [com.glyphdialer.core.domain.repository.TranscriptionEngine]. The :app module
 * enqueues this constrained to charging/idle for heavy on-device models (§19).
 *
 * HONESTY PRINCIPLE (§2.4): the engine output's `isLocalSideOnly` flag is persisted
 * by the repository; this worker never asserts a two-way transcript.
 */
@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transcripts: TranscriptRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val recordingId = inputData.getString(KEY_RECORDING_ID)
        if (recordingId.isNullOrBlank()) {
            Timber.w("TranscriptionWorker started with no %s; failing", KEY_RECORDING_ID)
            return Result.failure()
        }
        return transcripts.transcribeRecording(recordingId).fold(
            onSuccess = { transcript ->
                Timber.i("Transcribed recording %s -> %s", recordingId, transcript.id)
                Result.success()
            },
            onFailure = { failure ->
                Timber.w(failure.error, "Transcription of %s failed; retrying", recordingId)
                Result.retry()
            },
        )
    }

    companion object {
        const val UNIQUE_NAME: String = com.glyphdialer.core.common.Constants.Work.TRANSCRIPTION

        /** Input-data key carrying the recording id to transcribe. */
        const val KEY_RECORDING_ID: String = "recording_id"
    }
}
