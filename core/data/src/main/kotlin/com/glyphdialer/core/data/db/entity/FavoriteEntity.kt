// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a pinned favorite (BUILD_SPEC §19). Keyed by the stable
 * [contactLookupKey]; [position] is the user-defined grid order.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "contactLookupKey") val contactLookupKey: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "defaultNumber") val defaultNumber: String,
    @ColumnInfo(name = "displayName") val displayName: String?,
    @ColumnInfo(name = "photoUri") val photoUri: String?,
)
