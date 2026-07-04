// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glyphdialer.core.data.db.entity.ContactGlyphPatternEntity

@Dao
interface ContactGlyphPatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignPattern(entity: ContactGlyphPatternEntity)

    @Query("SELECT patternId FROM contact_glyph_patterns WHERE contactLookupKey = :lookupKey LIMIT 1")
    suspend fun getPatternIdForContact(lookupKey: String): String?
    
    @Query("DELETE FROM contact_glyph_patterns WHERE contactLookupKey = :lookupKey")
    suspend fun removeAssignment(lookupKey: String)
}
