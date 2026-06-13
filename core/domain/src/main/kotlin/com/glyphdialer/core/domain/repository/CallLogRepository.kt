// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CallType
import kotlinx.coroutines.flow.Flow

/**
 * Access to the platform call log ([android.provider.CallLog.Calls], §8). Write
 * operations require the default-dialer role.
 */
interface CallLogRepository {

    /**
     * Observe call-log entries, optionally restricted to [types] (e.g. missed-only)
     * and capped at [limit]. Emits on provider changes.
     */
    fun observeCallLog(
        types: Set<CallType>? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<CallLogEntry>>

    /** One-shot fetch of a page of entries for paging/search. */
    suspend fun getCallLog(
        types: Set<CallType>? = null,
        limit: Int = DEFAULT_LIMIT,
        offset: Int = 0,
    ): AppResult<List<CallLogEntry>>

    /** Observe entries for a specific [number]'s history (detail view). */
    fun observeForNumber(number: String): Flow<List<CallLogEntry>>

    /** Search the call log by name/number for [query]. */
    suspend fun search(query: String): AppResult<List<CallLogEntry>>

    /** Delete specific entries by id. */
    suspend fun deleteEntries(ids: List<Long>): AppResult<Unit>

    /** Clear the entire call log. */
    suspend fun clearAll(): AppResult<Unit>

    /** Mark missed-call entries as read (clears the badge). */
    suspend fun markAllRead(): AppResult<Unit>

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}
