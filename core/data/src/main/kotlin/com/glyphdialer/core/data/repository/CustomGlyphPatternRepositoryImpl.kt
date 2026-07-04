// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import com.glyphdialer.core.data.db.dao.ContactGlyphPatternDao
import com.glyphdialer.core.data.db.dao.CustomGlyphPatternDao
import com.glyphdialer.core.data.db.entity.ContactGlyphPatternEntity
import com.glyphdialer.core.data.db.entity.CustomGlyphFrameEntity
import com.glyphdialer.core.data.db.entity.CustomGlyphPatternEntity
import com.glyphdialer.core.domain.model.CustomGlyphPattern
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CustomGlyphPatternRepositoryImpl @Inject constructor(
    private val patternDao: CustomGlyphPatternDao,
    private val contactPatternDao: ContactGlyphPatternDao
) : CustomGlyphPatternRepository {

    override fun observeAllPatterns(): Flow<List<CustomGlyphPattern>> {
        return patternDao.observeAllPatterns().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPatternById(id: String): CustomGlyphPattern? {
        return patternDao.getPatternById(id)?.toDomain()
    }

    override suspend fun savePattern(pattern: CustomGlyphPattern) {
        val patternEntity = CustomGlyphPatternEntity(
            id = pattern.id,
            name = pattern.name
        )
        val frameEntities = pattern.frames.mapIndexed { index, frame ->
            CustomGlyphFrameEntity(
                patternId = pattern.id,
                indexInSequence = index,
                intensity = frame.intensity,
                durationMs = frame.durationMs,
                zones = frame.zones
            )
        }
        patternDao.savePattern(patternEntity, frameEntities)
    }

    override suspend fun deletePattern(id: String) {
        patternDao.deletePattern(id)
    }
    
    override suspend fun assignPatternToContact(patternId: String, lookupKey: String) {
        contactPatternDao.assignPattern(ContactGlyphPatternEntity(contactLookupKey = lookupKey, patternId = patternId))
    }

    override suspend fun getPatternForContact(lookupKey: String): CustomGlyphPattern? {
        val patternId = contactPatternDao.getPatternIdForContact(lookupKey) ?: return null
        return getPatternById(patternId)
    }

    override suspend fun removePatternFromContact(lookupKey: String) {
        contactPatternDao.removeAssignment(lookupKey)
    }
}
