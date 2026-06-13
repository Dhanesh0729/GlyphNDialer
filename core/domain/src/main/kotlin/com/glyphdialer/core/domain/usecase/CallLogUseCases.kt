// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CallType
import com.glyphdialer.core.domain.repository.BlockedNumberRepository
import com.glyphdialer.core.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observe the call log, optionally restricted to certain [CallType]s (e.g. the
 * missed-only filter, §8).
 */
class GetCallLogUseCase @Inject constructor(
    private val callLog: CallLogRepository,
) {
    operator fun invoke(
        types: Set<CallType>? = null,
        limit: Int = CallLogRepository.DEFAULT_LIMIT,
    ): Flow<List<CallLogEntry>> = callLog.observeCallLog(types, limit)

    /** Convenience: only missed-like entries for the badge/filter. */
    fun missedOnly(limit: Int = CallLogRepository.DEFAULT_LIMIT): Flow<List<CallLogEntry>> =
        callLog.observeCallLog(setOf(CallType.MISSED, CallType.REJECTED, CallType.BLOCKED), limit)
}

/** Block (and optionally report) a number (§8). */
class BlockNumberUseCase @Inject constructor(
    private val blocked: BlockedNumberRepository,
) {
    suspend operator fun invoke(number: String, reportAsSpam: Boolean = false): AppResult<Unit> {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) {
            return AppResult.Failure(IllegalArgumentException("blank number"), "Cannot block an empty number.")
        }
        return blocked.block(trimmed, reportAsSpam)
    }
}
