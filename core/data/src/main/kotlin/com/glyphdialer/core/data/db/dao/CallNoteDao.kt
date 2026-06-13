// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glyphdialer.core.data.db.entity.CallNoteEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for in-call/post-call notes (BUILD_SPEC §18/§19). */
@Dao
interface CallNoteDao {

    @Query("SELECT * FROM call_notes WHERE callId = :callId ORDER BY createdAtMillis ASC")
    fun observeForCall(callId: String): Flow<List<CallNoteEntity>>

    @Query("SELECT * FROM call_notes WHERE normalizedNumber = :normalized OR rawNumber = :raw ORDER BY createdAtMillis DESC")
    fun observeForNumber(normalized: String?, raw: String): Flow<List<CallNoteEntity>>

    @Query("SELECT * FROM call_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CallNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CallNoteEntity)

    @Query("UPDATE call_notes SET text = :text, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateText(id: String, text: String, updatedAt: Long)

    @Query("DELETE FROM call_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
