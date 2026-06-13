// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glyphdialer.core.data.db.entity.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for the local [BlockedNumberEntity] cache (BUILD_SPEC §8/§19). */
@Dao
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalizedNumber = :normalized)")
    suspend fun isBlocked(normalized: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :normalized")
    suspend fun deleteByNormalized(normalized: String)

    /** Replace the entire cache (used when re-syncing from the platform contract). */
    @Query("DELETE FROM blocked_numbers")
    suspend fun clear()
}
