// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.Favorite
import com.glyphdialer.core.domain.repository.FavoritesRepository
import com.glyphdialer.core.domain.usecase.GetContactsUseCase
import com.glyphdialer.core.domain.usecase.SearchContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Contacts list screen (BUILD_SPEC §8/§14; CONVENTIONS.md §5).
 *
 * Exposes one immutable [ContactsUiState] via [uiState] and consumes intents through
 * [onEvent]; one-shot navigation/dialing effects flow over [effects].
 *
 * Data pipeline: a debounced search query drives a [flatMapLatest] that either
 * observes all contacts ([GetContactsUseCase]) or runs a one-shot
 * [SearchContactsUseCase], the results are sectioned A–Z (+ "#") off the main thread
 * via [ContactSectioner], and combined with the resolved favorites grid into
 * [uiState]. All dispatchers are injected (never hard-coded); fallible work returns
 * [AppResult] rather than throwing across layers.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val getContacts: GetContactsUseCase,
    private val searchContacts: SearchContactsUseCase,
    private val favoritesRepository: FavoritesRepository,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // ---- UI inputs (drive the data pipeline) --------------------------------
    private val searchQuery = MutableStateFlow("")
    private val isSearchActive = MutableStateFlow(false)
    private val accountFilter = MutableStateFlow<String?>(null)

    // ---- Derived data state -------------------------------------------------
    private val dataState = MutableStateFlow(DataState())

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ContactsEffect>(Channel.BUFFERED)
    val effects: Flow<ContactsEffect> = _effects.receiveAsFlow()

    init {
        observeContacts()
        observeFavorites()
        wireUiState()
    }

    /** Single entry point for user intents (CONVENTIONS.md §5). */
    fun onEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.Search -> searchQuery.value = event.query
            is ContactsEvent.SetSearchActive -> onSearchActiveChanged(event.active)
            is ContactsEvent.OpenContact -> emitEffect(ContactsEffect.NavigateToDetail(event.lookupKey))
            is ContactsEvent.CallFavorite -> emitEffect(ContactsEffect.PlaceCall(event.item.number))
            is ContactsEvent.OpenFavorite -> emitEffect(ContactsEffect.NavigateToDetail(event.item.lookupKey))
            ContactsEvent.CreateContact -> emitEffect(ContactsEffect.NavigateToCreate)
            ContactsEvent.DismissError -> dataState.update { it.copy(errorMessage = null) }
        }
    }

    /** Set the RawContacts.ACCOUNT_TYPE filter (e.g. "com.google"), or null for all (§14). */
    fun setAccountFilter(accountType: String?) {
        accountFilter.value = accountType
    }

    // ---- Data pipeline ------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeContacts() {
        viewModelScope.launch {
            combine(
                accountFilter,
                searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }.distinctUntilChanged(),
            ) { account, query -> account to query }
                .flatMapLatest { (account, query) -> contactsFor(account, query) }
                .map { contacts -> ContactSectioner.section(contacts) }
                .flowOn(defaultDispatcher)
                .catch { t ->
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.e(t, "Contacts stream failed")
                    dataState.update { it.copy(isLoading = false, errorMessage = "Couldn't load contacts.") }
                }
                .collect { sections ->
                    dataState.update {
                        it.copy(
                            isLoading = false,
                            sections = sections,
                            indexLetters = ContactSectioner.presentLetters(sections),
                        )
                    }
                }
        }
    }

    /** Resolve the source flow for the current account filter + query (§8/§14). */
    private fun contactsFor(accountType: String?, query: String): Flow<List<Contact>> {
        if (query.isNotBlank()) {
            // One-shot search re-run as a single-element flow so it slots into the
            // same flatMapLatest pipeline; results still get sectioned.
            return flowOf(Unit).map {
                when (val r = searchContacts(query.trim())) {
                    is AppResult.Success -> r.data.filterByAccount(accountType)
                    is AppResult.Failure -> {
                        Timber.w(r.error, "Contacts search failed: ${r.message}")
                        dataState.update { it.copy(errorMessage = r.message ?: "Search failed.") }
                        emptyList()
                    }
                }
            }
        }
        return getContacts(accountType)
    }

    /**
     * Search is account-agnostic in the repository, so re-apply the active account
     * filter client-side to keep the filtered view consistent while searching (§14).
     */
    private fun List<Contact>.filterByAccount(accountType: String?): List<Contact> =
        if (accountType == null) this else filter { it.accountType == accountType }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.observeFavorites()
                .map { favorites -> favorites.map { it.toGridItem() } }
                .flowOn(defaultDispatcher)
                .catch { t ->
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.w(t, "Favorites stream failed")
                }
                .collect { items -> dataState.update { it.copy(favorites = items) } }
        }
    }

    /** Fold the data state and the ephemeral search state into one UiState. */
    private fun wireUiState() {
        viewModelScope.launch {
            combine(
                dataState,
                searchQuery,
                isSearchActive,
                accountFilter,
            ) { data, query, searching, account ->
                ContactsUiState(
                    isLoading = data.isLoading,
                    // Hide the favorites grid while searching — search should show only matches.
                    sections = data.sections,
                    favorites = if (query.isBlank()) data.favorites else emptyList(),
                    indexLetters = data.indexLetters,
                    searchQuery = query,
                    isSearchActive = searching,
                    accountFilter = account,
                    errorMessage = data.errorMessage,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    // ---- Intent handlers ----------------------------------------------------

    private fun onSearchActiveChanged(active: Boolean) {
        isSearchActive.value = active
        if (!active) searchQuery.value = ""
    }

    private fun emitEffect(effect: ContactsEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun Favorite.toGridItem() = FavoriteGridItem(
        lookupKey = contactLookupKey,
        displayName = displayName,
        photoUri = photoUri,
        number = defaultNumber,
    )

    /** Internal carrier for the data side of the state. */
    private data class DataState(
        val isLoading: Boolean = true,
        val sections: List<ContactSection> = emptyList(),
        val favorites: List<FavoriteGridItem> = emptyList(),
        val indexLetters: List<Char> = emptyList(),
        val errorMessage: String? = null,
    )

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
