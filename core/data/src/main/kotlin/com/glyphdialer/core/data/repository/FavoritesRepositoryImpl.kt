// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.data.db.dao.FavoriteDao
import com.glyphdialer.core.data.db.entity.FavoriteEntity
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.domain.model.Favorite
import com.glyphdialer.core.domain.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [FavoritesRepository] (CONVENTIONS.md §5). */
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoriteDao,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : FavoritesRepository {

    override fun observeFavorites(): Flow<List<Favorite>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun isFavorite(lookupKey: String): Flow<Boolean> =
        dao.isFavorite(lookupKey).distinctUntilChanged().flowOn(ioDispatcher)

    override suspend fun addFavorite(lookupKey: String, defaultNumber: String): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val existing = dao.getByKey(lookupKey)
                val position = existing?.position ?: (dao.maxPosition() + 1)
                dao.upsert(
                    FavoriteEntity(
                        contactLookupKey = lookupKey,
                        position = position,
                        defaultNumber = defaultNumber,
                        displayName = existing?.displayName,
                        photoUri = existing?.photoUri,
                    ),
                )
            }
        }

    override suspend fun removeFavorite(lookupKey: String): AppResult<Unit> =
        appResultOfSuspend { withContext(ioDispatcher) { dao.deleteByKey(lookupKey) } }

    override suspend fun reorder(orderedLookupKeys: List<String>): AppResult<Unit> =
        appResultOfSuspend { withContext(ioDispatcher) { dao.reorder(orderedLookupKeys) } }

    override suspend fun setDefaultNumber(lookupKey: String, number: String): AppResult<Unit> =
        appResultOfSuspend { withContext(ioDispatcher) { dao.setDefaultNumber(lookupKey, number) } }
}
