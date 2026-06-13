// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.BlockedNumber
import kotlinx.coroutines.flow.Flow

/**
 * Call-blocking list backed by [android.provider.BlockedNumberContract] when the
 * app is default dialer, mirrored locally for fast checks (§8).
 */
interface BlockedNumberRepository {

    /** Observe the full block list, newest first. */
    fun observeBlocked(): Flow<List<BlockedNumber>>

    /** Fast check used by the CallScreeningService: is [number] blocked? */
    suspend fun isBlocked(number: String): AppResult<Boolean>

    /** Block [number]; set [reportAsSpam] for "block & report". */
    suspend fun block(number: String, reportAsSpam: Boolean = false): AppResult<Unit>

    /** Unblock [number]. */
    suspend fun unblock(number: String): AppResult<Unit>
}
