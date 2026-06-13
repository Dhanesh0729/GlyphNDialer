// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the platform [android.provider.BlockedNumberContract] block list
 * (BUILD_SPEC §8/§19). The contract is authoritative when the app is default
 * dialer; this table is a fast local cache used by the CallScreeningService check.
 *
 * Numbers are stored both raw and E.164-normalized so [normalizedNumber] can drive
 * a unique-index dedupe and fast `isBlocked` lookups.
 */
@Entity(
    tableName = "blocked_numbers",
    indices = [Index(value = ["normalizedNumber"], unique = true)],
)
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "rawNumber") val rawNumber: String,
    @ColumnInfo(name = "normalizedNumber") val normalizedNumber: String,
    @ColumnInfo(name = "createdAtMillis") val createdAtMillis: Long,
    @ColumnInfo(name = "reportedAsSpam") val reportedAsSpam: Boolean,
)
