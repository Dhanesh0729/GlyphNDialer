// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.SpeedDialSlot
import kotlinx.coroutines.flow.Flow

/**
 * Speed-dial slot persistence for dialpad long-press keys 2–9 (RoomEntity:
 * SpeedDialEntity, §8/§19).
 */
interface SpeedDialRepository {

    /** Observe all assigned slots, sorted by [SpeedDialSlot.slot]. */
    fun observeSlots(): Flow<List<SpeedDialSlot>>

    /** One-shot fetch of the assignment in [slot] (2..9), or null if unassigned. */
    suspend fun getSlot(slot: Int): AppResult<SpeedDialSlot?>

    /** Assign or replace [slot] (2..9). */
    suspend fun assign(slot: Int, contactLookupKey: String?, number: String): AppResult<Unit>

    /** Clear the assignment in [slot]. */
    suspend fun clear(slot: Int): AppResult<Unit>
}
