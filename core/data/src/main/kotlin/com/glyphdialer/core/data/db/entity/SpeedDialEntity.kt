// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a dialpad speed-dial assignment, keyed by [slot] in 2..9
 * (BUILD_SPEC §8/§19). Slot 1 is reserved for voicemail, 0 for "+".
 */
@Entity(tableName = "speed_dials")
data class SpeedDialEntity(
    @PrimaryKey @ColumnInfo(name = "slot") val slot: Int,
    @ColumnInfo(name = "contactLookupKey") val contactLookupKey: String?,
    @ColumnInfo(name = "number") val number: String,
    @ColumnInfo(name = "displayName") val displayName: String?,
    @ColumnInfo(name = "photoUri") val photoUri: String?,
)
