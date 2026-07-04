// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.domain.model.CustomGlyphPattern
import kotlinx.coroutines.flow.Flow

interface CustomGlyphPatternRepository {
    fun observeAllPatterns(): Flow<List<CustomGlyphPattern>>
    suspend fun getPatternById(id: String): CustomGlyphPattern?
    suspend fun savePattern(pattern: CustomGlyphPattern)
    suspend fun deletePattern(id: String)
    suspend fun assignPatternToContact(patternId: String, lookupKey: String)
    suspend fun replaceAssignments(patternId: String, lookupKeys: Set<String>)
    suspend fun getContactKeysForPattern(patternId: String): Set<String>
    suspend fun getPatternForContact(lookupKey: String): CustomGlyphPattern?
    suspend fun removePatternFromContact(lookupKey: String)
}
