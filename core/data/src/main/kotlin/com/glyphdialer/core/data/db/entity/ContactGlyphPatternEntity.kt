// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Maps a contact's lookup key to a custom Glyph pattern.
 */
@Entity(tableName = "contact_glyph_patterns")
data class ContactGlyphPatternEntity(
    @PrimaryKey
    val contactLookupKey: String,
    val patternId: String
)
