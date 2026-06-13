// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.glyphdialer.core.data.db.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for [RecordingEntity] (BUILD_SPEC §12/§19). */
@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY startedAtMillis DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecordingEntity)

    @Update
    suspend fun update(entity: RecordingEntity)

    @Query("UPDATE recordings SET transcriptId = :transcriptId, hasTranscript = 1 WHERE id = :id")
    suspend fun attachTranscript(id: String, transcriptId: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Rows older than [cutoffMillis], used by the retention purge to delete their bodies first. */
    @Query("SELECT * FROM recordings WHERE startedAtMillis < :cutoffMillis")
    suspend fun olderThan(cutoffMillis: Long): List<RecordingEntity>

    @Query("DELETE FROM recordings WHERE startedAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
