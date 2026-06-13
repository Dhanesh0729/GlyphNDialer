// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.fake

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.Recording
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Hand-rolled fake [RecordingRepository] for pure use-case tests. Records the
 * cutoffs passed to [purgeOlderThan] and returns a configurable deleted count or
 * failure.
 */
class FakeRecordingRepository : RecordingRepository {

    /** Cutoffs received by [purgeOlderThan], in call order. */
    val purgeCalls = mutableListOf<Long>()

    /** Value returned from [purgeOlderThan] when not failing. */
    var deletedCount: Int = 0

    /** When non-null, [purgeOlderThan] fails with this throwable. */
    var failWith: Throwable? = null

    override val isRecording: StateFlow<Boolean> = MutableStateFlow(false)
    override val activeTier: StateFlow<RecordingTier> = MutableStateFlow(RecordingTier.UNAVAILABLE)

    override suspend fun resolveTier(call: CallModel): RecordingTier = RecordingTier.UNAVAILABLE

    override suspend fun startRecording(call: CallModel): AppResult<Recording> =
        AppResult.Failure(UnsupportedOperationException("not used in tests"))

    override suspend fun stopRecording(): AppResult<Recording?> = AppResult.Success(null)

    override fun observeRecordings(): Flow<List<Recording>> = flowOf(emptyList())

    override suspend fun getRecording(id: String): AppResult<Recording?> = AppResult.Success(null)

    override suspend fun deleteRecording(id: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun export(id: String): AppResult<String> =
        AppResult.Failure(UnsupportedOperationException("not used in tests"))

    override suspend fun purgeOlderThan(cutoffMillis: Long): AppResult<Int> {
        purgeCalls += cutoffMillis
        failWith?.let { return AppResult.Failure(it, it.message) }
        return AppResult.Success(deletedCount)
    }
}
