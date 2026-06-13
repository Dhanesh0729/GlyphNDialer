// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.fake

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.repository.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Hand-rolled fake [TranscriptRepository] for pure use-case tests. Records the
 * cutoffs passed to [purgeOlderThan] and returns a configurable deleted count or
 * failure.
 */
class FakeTranscriptRepository : TranscriptRepository {

    /** Cutoffs received by [purgeOlderThan], in call order. */
    val purgeCalls = mutableListOf<Long>()

    /** Value returned from [purgeOlderThan] when not failing. */
    var deletedCount: Int = 0

    /** When non-null, [purgeOlderThan] fails with this throwable. */
    var failWith: Throwable? = null

    override fun observeTranscripts(): Flow<List<Transcript>> = flowOf(emptyList())

    override suspend fun search(query: String): AppResult<List<Transcript>> =
        AppResult.Success(emptyList())

    override suspend fun getTranscript(id: String): AppResult<Transcript?> = AppResult.Success(null)

    override suspend fun getForRecording(recordingId: String): AppResult<Transcript?> =
        AppResult.Success(null)

    override suspend fun transcribeRecording(recordingId: String): AppResult<Transcript> =
        AppResult.Failure(UnsupportedOperationException("not used in tests"))

    override fun liveCaptions(): Flow<String> = flowOf()

    override suspend fun deleteTranscript(id: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun purgeOlderThan(cutoffMillis: Long): AppResult<Int> {
        purgeCalls += cutoffMillis
        failWith?.let { return AppResult.Failure(it, it.message) }
        return AppResult.Success(deletedCount)
    }
}
