// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.glyphdialer.core.domain.model.RecordingTier

/**
 * Room row for call-recording metadata (BUILD_SPEC §12/§19). The audio body itself
 * lives in app-private (optionally encrypted) storage at [filePath]; only metadata
 * is persisted here.
 *
 * [tier] stores the honest [RecordingTier] used so history can truthfully display
 * "two-way" vs "my side only" (§2.1).
 */
@Entity(
    tableName = "recordings",
    indices = [
        Index("startedAtMillis"),
        Index("callId"),
        Index("transcriptId"),
    ],
)
data class RecordingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "callId") val callId: String?,
    @ColumnInfo(name = "rawNumber") val rawNumber: String,
    @ColumnInfo(name = "normalizedNumber") val normalizedNumber: String?,
    @ColumnInfo(name = "contactName") val contactName: String?,
    @ColumnInfo(name = "startedAtMillis") val startedAtMillis: Long,
    @ColumnInfo(name = "durationMillis") val durationMillis: Long,
    @ColumnInfo(name = "tier") val tier: RecordingTier,
    @ColumnInfo(name = "filePath") val filePath: String,
    @ColumnInfo(name = "transcriptId") val transcriptId: String?,
    @ColumnInfo(name = "hasTranscript") val hasTranscript: Boolean,
    @ColumnInfo(name = "announced") val announced: Boolean,
)
