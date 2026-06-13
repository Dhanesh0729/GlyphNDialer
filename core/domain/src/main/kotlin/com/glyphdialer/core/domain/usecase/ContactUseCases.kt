// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Free-text contacts search across name + numbers (§8). */
class SearchContactsUseCase @Inject constructor(
    private val contacts: ContactsRepository,
) {
    suspend operator fun invoke(query: String): AppResult<List<Contact>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return AppResult.Success(emptyList())
        return contacts.searchContacts(trimmed)
    }
}

/** Observe all contacts, optionally filtered to a single account type (§14). */
class GetContactsUseCase @Inject constructor(
    private val contacts: ContactsRepository,
) {
    operator fun invoke(accountType: String? = null): Flow<List<Contact>> =
        contacts.observeContacts(accountType)
}

/**
 * Toggle a contact's favorite status (§8). Adds with [defaultNumber] when favoriting;
 * removes otherwise.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val favorites: FavoritesRepository,
) {
    suspend operator fun invoke(
        lookupKey: String,
        makeFavorite: Boolean,
        defaultNumber: String? = null,
    ): AppResult<Unit> =
        if (makeFavorite) {
            val number = defaultNumber
                ?: return AppResult.Failure(
                    IllegalArgumentException("defaultNumber required to favorite a contact"),
                    "A number is required to add a favorite.",
                )
            favorites.addFavorite(lookupKey, number)
        } else {
            favorites.removeFavorite(lookupKey)
        }
}
