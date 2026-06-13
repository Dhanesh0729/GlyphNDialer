// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.list

import androidx.compose.runtime.Immutable
import com.glyphdialer.core.domain.model.Contact

/**
 * The single immutable UI state for the Contacts list screen (CONVENTIONS.md §5;
 * BUILD_SPEC §8/§14 — list with fast-scroll alphabet index, search, favorites grid).
 *
 * The state is pre-shaped for rendering so the composable stays dumb:
 *  - [sections] is the already-bucketed A–Z (+ "#") list the screen draws directly.
 *  - [favorites] is the resolved favorites grid (photos + dot-matrix fallback).
 *  - [indexLetters] is the ordered set of letters that actually have entries, which
 *    the fast-scroll rail renders (letters with no contacts are dimmed).
 *
 * @property isLoading true during the very first load before any data arrives.
 * @property sections the alphabetised, display-ready sections (search-filtered).
 * @property favorites the resolved favorites grid items (empty hides the grid).
 * @property indexLetters letters present in [sections], in display order.
 * @property searchQuery the live search-field text; blank means "browsing".
 * @property isSearchActive whether the search field is expanded/focused.
 * @property accountFilter the active RawContacts.ACCOUNT_TYPE filter, or null = all.
 * @property errorMessage a transient human-readable error to surface, or null.
 */
@Immutable
data class ContactsUiState(
    val isLoading: Boolean = true,
    val sections: List<ContactSection> = emptyList(),
    val favorites: List<FavoriteGridItem> = emptyList(),
    val indexLetters: List<Char> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val accountFilter: String? = null,
    val errorMessage: String? = null,
) {
    /** Total number of contacts across all [sections]. */
    val contactCount: Int get() = sections.sumOf { it.contacts.size }

    /** True when there is nothing to show after loading (drives the empty state). */
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()

    /** True when an active search returned no matches (a distinct empty copy). */
    val isSearchEmpty: Boolean get() = !isLoading && searchQuery.isNotBlank() && sections.isEmpty()
}

/**
 * One alphabet bucket in the list: a [letter] header ('A'..'Z' or '#') and the
 * contacts whose [Contact.sortIndexChar] falls under it, pre-sorted by name.
 */
@Immutable
data class ContactSection(
    val letter: Char,
    val contacts: List<Contact>,
)

/**
 * A resolved favorite for the photo grid (BUILD_SPEC §8 — "FavoritesGrid (photos)").
 * Carries the dial number so a tap can call without re-resolving the contact.
 *
 * @property lookupKey stable contact key (favorite identity + navigation).
 * @property displayName the favorite's name, or null (falls back to the number).
 * @property photoUri contact photo content:// URI, or null → [com.glyphdialer.core.ui.component.DotMatrixAvatar].
 * @property number the default number dialed when the favorite is tapped.
 */
@Immutable
data class FavoriteGridItem(
    val lookupKey: String,
    val displayName: String?,
    val photoUri: String?,
    val number: String,
) {
    /** Deterministic seed for the dot-matrix avatar fallback. */
    val avatarSeed: String get() = displayName ?: number
}

/**
 * User intents flowing UP from the Contacts list into [ContactsViewModel.onEvent]
 * (CONVENTIONS.md §5). Kept exhaustive so the reducer is too.
 */
sealed interface ContactsEvent {
    /** Update the live search query (debounced in the ViewModel). */
    data class Search(val query: String) : ContactsEvent

    /** Expand/collapse the search field. */
    data class SetSearchActive(val active: Boolean) : ContactsEvent

    /** Tap a contact row to open the detail screen. */
    data class OpenContact(val lookupKey: String) : ContactsEvent

    /** Tap a favorite tile to dial its default number. */
    data class CallFavorite(val item: FavoriteGridItem) : ContactsEvent

    /** Long-press a favorite tile to open the detail screen (manage/reorder). */
    data class OpenFavorite(val item: FavoriteGridItem) : ContactsEvent

    /** Tap the "+" FAB to create a new contact. */
    data object CreateContact : ContactsEvent

    /** Dismiss the currently-shown error message. */
    data object DismissError : ContactsEvent
}

/**
 * One-shot side effects the ViewModel asks the host to perform (CONVENTIONS.md §5
 * — navigation/dialing via a `Channel`). The feature does not depend on :telecom,
 * so dialing is delegated up to :app.
 */
sealed interface ContactsEffect {
    /** Navigate to the contact-detail destination for [lookupKey]. */
    data class NavigateToDetail(val lookupKey: String) : ContactsEffect

    /** Navigate to the contact-create destination. */
    data object NavigateToCreate : ContactsEffect

    /** Place a call to [number] (host issues TelecomManager.placeCall via :app). */
    data class PlaceCall(val number: String) : ContactsEffect
}
