// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.RetentionWindow
import com.glyphdialer.core.domain.repository.RecordingRepository
import com.glyphdialer.core.domain.repository.TranscriptRepository
import javax.inject.Inject

/** Outcome of a purge run: how many recordings/transcripts were deleted. */
data class PurgeResult(
    val recordingsDeleted: Int,
    val transcriptsDeleted: Int,
) {
    val total: Int get() = recordingsDeleted + transcriptsDeleted

    companion object {
        /** A no-op result (e.g. when the window is NEVER). */
        val NONE = PurgeResult(0, 0)
    }
}

/**
 * Auto-purge recordings and transcripts older than the user's retention window
 * (spec sections 12/19). Driven by the PurgeOldDataWorker.
 *
 * The retention CUTOFF math lives in [RetentionWindow.cutoffMillis] and is fully
 * unit-testable; this use case wires it to the repositories. For
 * [RetentionWindow.NEVER] nothing is purged.
 */
class PurgeOldDataUseCase @Inject constructor(
    private val recordings: RecordingRepository,
    private val transcripts: TranscriptRepository,
) {
    /**
     * @param window the active retention window.
     * @param now current wall-clock millis (injected for testability).
     */
    suspend operator fun invoke(window: RetentionWindow, now: Long): AppResult<PurgeResult> {
        val cutoff = window.cutoffMillis(now)
            ?: return AppResult.Success(PurgeResult.NONE) // NEVER: keep everything.

        val recordingsDeleted = when (val r = recordings.purgeOlderThan(cutoff)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> return AppResult.Failure(r.error, r.message)
        }
        val transcriptsDeleted = when (val t = transcripts.purgeOlderThan(cutoff)) {
            is AppResult.Success -> t.data
            is AppResult.Failure -> return AppResult.Failure(t.error, t.message)
        }
        return AppResult.Success(PurgeResult(recordingsDeleted, transcriptsDeleted))
    }
}
