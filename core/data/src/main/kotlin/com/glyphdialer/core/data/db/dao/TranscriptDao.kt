// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.glyphdialer.core.data.db.entity.TranscriptEntity
import com.glyphdialer.core.data.db.entity.TranscriptSegmentEntity
import com.glyphdialer.core.data.db.entity.TranscriptWithSegments
import kotlinx.coroutines.flow.Flow

/** Room DAO for [TranscriptEntity] + its [TranscriptSegmentEntity] children (§13/§19). */
@Dao
interface TranscriptDao {

    @Transaction
    @Query("SELECT * FROM transcripts ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<TranscriptWithSegments>>

    @Transaction
    @Query("SELECT * FROM transcripts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TranscriptWithSegments?

    @Transaction
    @Query("SELECT * FROM transcripts WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getForRecording(recordingId: String): TranscriptWithSegments?

    @Transaction
    @Query("SELECT * FROM transcripts WHERE fullText LIKE '%' || :query || '%' ORDER BY createdAtMillis DESC")
    suspend fun search(query: String): List<TranscriptWithSegments>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(entity: TranscriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("DELETE FROM transcript_segments WHERE transcriptId = :transcriptId")
    suspend fun deleteSegmentsFor(transcriptId: String)

    /** Insert a transcript and (replacing any prior) its segments atomically. */
    @Transaction
    suspend fun upsertWithSegments(transcript: TranscriptEntity, segments: List<TranscriptSegmentEntity>) {
        insertTranscript(transcript)
        deleteSegmentsFor(transcript.id)
        if (segments.isNotEmpty()) insertSegments(segments)
    }

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transcripts WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
