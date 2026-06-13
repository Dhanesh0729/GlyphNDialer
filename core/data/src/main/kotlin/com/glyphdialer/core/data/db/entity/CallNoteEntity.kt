// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a free-text note attached to a call (BUILD_SPEC §18/§19). Indexed by
 * [callId] and [normalizedNumber] so both the call-detail and per-number history
 * views can query efficiently.
 */
@Entity(
    tableName = "call_notes",
    indices = [
        Index("callId"),
        Index("normalizedNumber"),
    ],
)
data class CallNoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "callId") val callId: String,
    @ColumnInfo(name = "rawNumber") val rawNumber: String,
    @ColumnInfo(name = "normalizedNumber") val normalizedNumber: String?,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "createdAtMillis") val createdAtMillis: Long,
    @ColumnInfo(name = "updatedAtMillis") val updatedAtMillis: Long,
)
