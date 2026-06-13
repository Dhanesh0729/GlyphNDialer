// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixSpinner
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EmptyState
import com.glyphdialer.feature.contacts.component.ContactRow
import com.glyphdialer.feature.contacts.component.FastScrollIndex
import com.glyphdialer.feature.contacts.component.FavoritesGrid
import kotlinx.coroutines.launch

/**
 * Contacts list screen entry point (BUILD_SPEC §8/§14).
 *
 * Stateful wrapper: collects [ContactsUiState], consumes one-shot [ContactsEffect]s
 * (navigation up to the host; dialing delegated up since the feature doesn't depend
 * on :telecom), and forwards events to the ViewModel. The layout is the stateless
 * [ContactsScreen] for previewability.
 *
 * @param onOpenContact navigate to a contact's detail.
 * @param onCreateContact navigate to the create-contact destination.
 * @param onDial host-provided dialer (TelecomManager / ACTION_CALL).
 */
@Composable
fun ContactsRoute(
    onOpenContact: (String) -> Unit,
    onCreateContact: () -> Unit,
    onDial: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ContactsEffect.NavigateToDetail -> onOpenContact(effect.lookupKey)
                ContactsEffect.NavigateToCreate -> onCreateContact()
                is ContactsEffect.PlaceCall -> onDial(effect.number)
            }
        }
    }

    ContactsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * Stateless contacts layout: a search field, the favorites grid (when browsing), an
 * alphabetised list with sticky letter headers, and a vertical fast-scroll rail
 * pinned to the trailing edge. Errors surface as snackbars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    uiState: ContactsUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ContactsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(ContactsEvent.DismissError)
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Map a section letter → the flat LazyColumn item index of its header so the
    // fast-scroll rail can jump there.
    val headerIndexByLetter by remember(uiState.sections, uiState.favorites) {
        derivedStateOf { computeHeaderIndices(uiState) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        ContactsSearchField(
                            query = uiState.searchQuery,
                            onQueryChange = { onEvent(ContactsEvent.Search(it)) },
                        )
                    } else {
                        Text(
                            "CONTACTS",
                            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(ContactsEvent.SetSearchActive(!uiState.isSearchActive)) },
                    ) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (uiState.isSearchActive) "Close search" else "Search contacts",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(ContactsEvent.CreateContact) },
                modifier = Modifier.testTag(TestTags.AddContactFab),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> ContactsLoading(modifier = Modifier.align(Alignment.Center))

                uiState.isSearchEmpty -> EmptyState(
                    title = "No matches",
                    message = "No contacts match \"${uiState.searchQuery}\".",
                    icon = Icons.Filled.Search,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Dimens.spaceXxl),
                )

                uiState.isEmpty -> EmptyState(
                    title = "No contacts",
                    message = "Contacts you add will show up here.",
                    icon = Icons.Filled.Person,
                    actionLabel = "ADD CONTACT",
                    onAction = { onEvent(ContactsEvent.CreateContact) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Dimens.spaceXxl),
                )

                else -> ContactsBody(
                    uiState = uiState,
                    listState = listState,
                    onEvent = onEvent,
                    onJumpToLetter = { letter ->
                        headerIndexByLetter[letter]?.let { index ->
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsBody(
    uiState: ContactsUiState,
    listState: LazyListState,
    onEvent: (ContactsEvent) -> Unit,
    onJumpToLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TestTags.ContactsList),
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                // Leave room for the fast-scroll rail on the trailing edge.
                end = Dimens.screenPadding + 16.dp,
                top = Dimens.spaceMd,
                bottom = Dimens.spaceXxl,
            ),
        ) {
            // Favorites grid sits above the A–Z list while browsing (hidden in search).
            if (uiState.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    FavoritesGrid(
                        favorites = uiState.favorites,
                        onCall = { onEvent(ContactsEvent.CallFavorite(it)) },
                        onOpen = { onEvent(ContactsEvent.OpenFavorite(it)) },
                        modifier = Modifier.padding(bottom = Dimens.spaceLg),
                    )
                }
            }

            uiState.sections.forEach { section ->
                stickyHeader(key = "header_${section.letter}") {
                    SectionHeader(letter = section.letter)
                }
                items(section.contacts, key = { it.lookupKey }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { onEvent(ContactsEvent.OpenContact(contact.lookupKey)) },
                    )
                }
            }
        }

        FastScrollIndex(
            presentLetters = uiState.indexLetters,
            onLetterSelected = onJumpToLetter,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Dimens.spaceXs),
        )
    }
}

@Composable
private fun SectionHeader(letter: Char, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = letter.toString(),
            modifier = Modifier.padding(vertical = Dimens.spaceXs),
            style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
            color = MaterialTheme.colorScheme.primary,
        )
        DottedDivider(modifier = Modifier.padding(bottom = Dimens.spaceXs))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().testTag(TestTags.SearchField),
        placeholder = { Text("Search contacts") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

@Composable
private fun ContactsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        DotMatrixSpinner(size = 40.dp)
        Text(
            text = "LOADING CONTACTS",
            style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Compute the flat LazyColumn index of each section header. Mirrors the item layout
 * in [ContactsBody]: an optional favorites item, then per section a header followed
 * by its rows. Pure so it can be derived cheaply.
 */
private fun computeHeaderIndices(uiState: ContactsUiState): Map<Char, Int> {
    val indices = HashMap<Char, Int>(uiState.sections.size)
    var cursor = if (uiState.favorites.isNotEmpty()) 1 else 0
    uiState.sections.forEach { section ->
        indices[section.letter] = cursor
        cursor += 1 + section.contacts.size // header + rows
    }
    return indices
}

/** Test tags for instrumented Compose tests. */
internal object TestTags {
    const val ContactsList = "contacts_list"
    const val SearchField = "contacts_search_field"
    const val AddContactFab = "contacts_add_fab"
}

// --- Previews ----------------------------------------------------------------------

private fun previewContact(id: Long, name: String, fav: Boolean = false) = Contact(
    id = id,
    lookupKey = "k$id",
    displayName = name,
    numbers = listOf(PhoneNumber(raw = "+1415555$id", formatted = "(415) 555-0$id")),
    isFavorite = fav,
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Contacts · list", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewContactsList() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        ContactsScreen(
            uiState = ContactsUiState(
                isLoading = false,
                favorites = listOf(
                    FavoriteGridItem("k1", "Ada Lovelace", null, "+14155550142"),
                ),
                sections = listOf(
                    ContactSection('A', listOf(previewContact(1, "Ada Lovelace", fav = true), previewContact(2, "Alan Turing"))),
                    ContactSection('G', listOf(previewContact(3, "Grace Hopper"))),
                    ContactSection('#', listOf(previewContact(4, "8-Ball Pool"))),
                ),
                indexLetters = listOf('A', 'G', '#'),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Contacts · empty", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewContactsEmpty() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        ContactsScreen(
            uiState = ContactsUiState(isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}
