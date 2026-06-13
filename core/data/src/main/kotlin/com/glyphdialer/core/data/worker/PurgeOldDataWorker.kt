// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.fold
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.core.domain.usecase.PurgeOldDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Periodic retention purge (BUILD_SPEC §12/§19): deletes recordings and transcripts
 * older than the user's [com.glyphdialer.core.domain.model.RetentionWindow]. The
 * cutoff math lives in the window enum and use case; this worker just supplies
 * `now` and wires WorkManager retry semantics.
 */
@HiltWorker
class PurgeOldDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val purge: PurgeOldDataUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = when (val r = settings.current()) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> {
                Timber.w(r.error, "Purge: failed to read settings; retrying")
                return Result.retry()
            }
        }
        return purge(prefs.retentionWindow, System.currentTimeMillis()).fold(
            onSuccess = { result ->
                Timber.i("Purge complete: %d recordings, %d transcripts",
                    result.recordingsDeleted, result.transcriptsDeleted)
                Result.success()
            },
            onFailure = { failure ->
                Timber.w(failure.error, "Purge failed; retrying")
                Result.retry()
            },
        )
    }

    companion object {
        const val UNIQUE_NAME: String = com.glyphdialer.core.common.Constants.Work.PURGE_OLD_DATA
    }
}
