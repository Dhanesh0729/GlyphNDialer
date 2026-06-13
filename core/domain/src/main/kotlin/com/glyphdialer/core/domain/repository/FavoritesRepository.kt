// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Favorite
import kotlinx.coroutines.flow.Flow

/** Favorites grid persistence (RoomEntity: FavoriteEntity, §8/§19). */
interface FavoritesRepository {

    /** Observe favorites in user-defined [Favorite.position] order. */
    fun observeFavorites(): Flow<List<Favorite>>

    /** Whether the contact [lookupKey] is currently a favorite. */
    fun isFavorite(lookupKey: String): Flow<Boolean>

    /** Add a favorite (appended to the end of the grid). */
    suspend fun addFavorite(lookupKey: String, defaultNumber: String): AppResult<Unit>

    /** Remove the favorite identified by [lookupKey]. */
    suspend fun removeFavorite(lookupKey: String): AppResult<Unit>

    /** Persist a new ordering (list is in the desired display order). */
    suspend fun reorder(orderedLookupKeys: List<String>): AppResult<Unit>

    /** Change the number dialed when a favorite is tapped. */
    suspend fun setDefaultNumber(lookupKey: String, number: String): AppResult<Unit>
}
