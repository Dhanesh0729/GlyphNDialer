// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.usecase.ObservePreferencesUseCase
import com.glyphdialer.core.domain.usecase.PurgeOldDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic auto-purge of recordings/transcripts older than the user's retention
 * window (BUILD_SPEC §12/§19; CONVENTIONS.md). Scheduled once from [GlyphDialerApp].
 *
 * The worker reads the *current* retention window from preferences each run (so a
 * settings change takes effect on the next run without rescheduling) and delegates the
 * cutoff math + deletion to [PurgeOldDataUseCase]. Honors [PurgeResult.NONE] for
 * `NEVER`. Constructed by [HiltWorkerFactory] via @AssistedInject.
 */
@HiltWorker
class PurgeOldDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val observePreferences: ObservePreferencesUseCase,
    private val purgeOldData: PurgeOldDataUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = runCatching { observePreferences().first() }.getOrElse {
            Timber.tag(TAG).w(it, "Could not read preferences; retrying purge later")
            return Result.retry()
        }

        return when (val result = purgeOldData(prefs.retentionWindow, System.currentTimeMillis())) {
            is AppResult.Success -> {
                Timber.tag(TAG).i(
                    "Purge complete: %d recordings, %d transcripts (window=%s)",
                    result.data.recordingsDeleted,
                    result.data.transcriptsDeleted,
                    prefs.retentionWindow,
                )
                Result.success()
            }

            is AppResult.Failure -> {
                Timber.tag(TAG).w(result.error, "Purge failed: %s", result.message)
                Result.retry()
            }
        }
    }

    companion object {
        const val TAG = "PurgeOldDataWorker"

        /** Unique periodic work name so re-scheduling on every app start is idempotent. */
        const val UNIQUE_WORK_NAME = "glyph_purge_old_data"

        /**
         * Enqueue the daily purge as unique periodic work. [ExistingPeriodicWorkPolicy.KEEP]
         * means an already-scheduled chain survives an app restart unchanged.
         */
        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                // Purge is local-only; no network needed. Defer until the device is idle
                // to avoid contending with foreground work.
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<PurgeOldDataWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Timber.tag(TAG).d("Scheduled periodic purge work")
        }
    }
}
