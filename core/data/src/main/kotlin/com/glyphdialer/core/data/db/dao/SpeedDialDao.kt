// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glyphdialer.core.data.db.entity.SpeedDialEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for dialpad speed-dial slots 2..9 (BUILD_SPEC §8/§19). */
@Dao
interface SpeedDialDao {

    @Query("SELECT * FROM speed_dials ORDER BY slot ASC")
    fun observeAll(): Flow<List<SpeedDialEntity>>

    @Query("SELECT * FROM speed_dials WHERE slot = :slot LIMIT 1")
    suspend fun getSlot(slot: Int): SpeedDialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpeedDialEntity)

    @Query("DELETE FROM speed_dials WHERE slot = :slot")
    suspend fun clearSlot(slot: Int)
}
