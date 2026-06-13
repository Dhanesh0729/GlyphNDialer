// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.glyphdialer.core.data.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/** Room DAO for the favorites grid (BUILD_SPEC §8/§19). */
@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY position ASC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contactLookupKey = :lookupKey)")
    fun isFavorite(lookupKey: String): Flow<Boolean>

    @Query("SELECT * FROM favorites WHERE contactLookupKey = :lookupKey LIMIT 1")
    suspend fun getByKey(lookupKey: String): FavoriteEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM favorites")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("UPDATE favorites SET defaultNumber = :number WHERE contactLookupKey = :lookupKey")
    suspend fun setDefaultNumber(lookupKey: String, number: String)

    @Query("UPDATE favorites SET position = :position WHERE contactLookupKey = :lookupKey")
    suspend fun setPosition(lookupKey: String, position: Int)

    @Query("DELETE FROM favorites WHERE contactLookupKey = :lookupKey")
    suspend fun deleteByKey(lookupKey: String)

    /** Persist a new ordering by rewriting each row's position in one transaction. */
    @Transaction
    suspend fun reorder(orderedLookupKeys: List<String>) {
        orderedLookupKeys.forEachIndexed { index, key -> setPosition(key, index) }
    }
}
