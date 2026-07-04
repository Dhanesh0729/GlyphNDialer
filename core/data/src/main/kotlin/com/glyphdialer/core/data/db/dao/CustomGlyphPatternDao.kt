// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.glyphdialer.core.data.db.entity.CustomGlyphFrameEntity
import com.glyphdialer.core.data.db.entity.CustomGlyphPatternEntity
import com.glyphdialer.core.data.db.entity.CustomGlyphPatternWithFrames
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomGlyphPatternDao {

    @Transaction
    @Query("SELECT * FROM custom_glyph_pattern ORDER BY name ASC")
    fun observeAllPatterns(): Flow<List<CustomGlyphPatternWithFrames>>

    @Transaction
    @Query("SELECT * FROM custom_glyph_pattern WHERE id = :id LIMIT 1")
    suspend fun getPatternById(id: String): CustomGlyphPatternWithFrames?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: CustomGlyphPatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(frames: List<CustomGlyphFrameEntity>)

    @Query("DELETE FROM custom_glyph_frame WHERE patternId = :patternId")
    suspend fun deleteFramesForPattern(patternId: String)

    @Query("DELETE FROM custom_glyph_pattern WHERE id = :id")
    suspend fun deletePattern(id: String)

    @Transaction
    suspend fun savePattern(pattern: CustomGlyphPatternEntity, frames: List<CustomGlyphFrameEntity>) {
        insertPattern(pattern)
        deleteFramesForPattern(pattern.id)
        insertFrames(frames)
    }
}
