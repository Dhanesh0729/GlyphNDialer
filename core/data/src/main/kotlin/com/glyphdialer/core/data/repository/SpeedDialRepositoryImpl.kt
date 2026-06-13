// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.data.db.dao.SpeedDialDao
import com.glyphdialer.core.data.db.entity.SpeedDialEntity
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.domain.model.SpeedDialSlot
import com.glyphdialer.core.domain.repository.SpeedDialRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [SpeedDialRepository] (CONVENTIONS.md §5). */
@Singleton
class SpeedDialRepositoryImpl @Inject constructor(
    private val dao: SpeedDialDao,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SpeedDialRepository {

    override fun observeSlots(): Flow<List<SpeedDialSlot>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getSlot(slot: Int): AppResult<SpeedDialSlot?> =
        appResultOfSuspend { withContext(ioDispatcher) { dao.getSlot(slot)?.toDomain() } }

    override suspend fun assign(slot: Int, contactLookupKey: String?, number: String): AppResult<Unit> =
        appResultOfSuspend {
            require(slot in SpeedDialSlot.MIN_SLOT..SpeedDialSlot.MAX_SLOT) {
                "Speed-dial slot must be in ${SpeedDialSlot.MIN_SLOT}..${SpeedDialSlot.MAX_SLOT} (was $slot)"
            }
            withContext(ioDispatcher) {
                dao.upsert(
                    SpeedDialEntity(
                        slot = slot,
                        contactLookupKey = contactLookupKey,
                        number = number,
                        displayName = null,
                        photoUri = null,
                    ),
                )
            }
        }

    override suspend fun clear(slot: Int): AppResult<Unit> =
        appResultOfSuspend { withContext(ioDispatcher) { dao.clearSlot(slot) } }
}
