// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.glyphdialer.core.domain.model.TranscriptionEngineType

/**
 * Room row for a full transcript (BUILD_SPEC §13/§19). Segments live in
 * [TranscriptSegmentEntity] and are joined via [TranscriptWithSegments].
 *
 * [isLocalSideOnly] preserves the honesty flag (§2.4): cellular transcripts that
 * captured only the local mic must say so in the UI.
 */
@Entity(
    tableName = "transcripts",
    indices = [
        Index("recordingId"),
        Index("createdAtMillis"),
    ],
)
data class TranscriptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recordingId") val recordingId: String?,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "fullText") val fullText: String,
    @ColumnInfo(name = "engine") val engine: TranscriptionEngineType,
    @ColumnInfo(name = "createdAtMillis") val createdAtMillis: Long,
    @ColumnInfo(name = "isLocalSideOnly") val isLocalSideOnly: Boolean,
)

/**
 * Room row for one timestamped transcript segment (BUILD_SPEC §19). Cascades on
 * delete so removing a [TranscriptEntity] removes its segments.
 */
@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transcriptId")],
)
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transcriptId") val transcriptId: String,
    @ColumnInfo(name = "startMillis") val startMillis: Long,
    @ColumnInfo(name = "endMillis") val endMillis: Long,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "speaker") val speaker: String?,
    @ColumnInfo(name = "confidence") val confidence: Float?,
    /** Ordinal position within the transcript, for stable ordering. */
    @ColumnInfo(name = "ordinal") val ordinal: Int,
)
