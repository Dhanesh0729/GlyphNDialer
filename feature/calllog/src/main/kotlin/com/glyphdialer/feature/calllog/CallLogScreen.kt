// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.CallType
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.DotMatrixSpinner
import com.glyphdialer.core.ui.component.EmptyState

/**
 * Stateful entry point for the Recents / Call-log destination. Collects state from
 * [CallLogViewModel] with lifecycle awareness, wires one-shot [CallLogEffect]s to
 * platform intents (place call, SMS, clipboard, toast), and delegates "open
 * details" navigation up to the host.
 *
 * @param onNavigateToDetails host-owned navigation to a per-number history view.
 * @param viewModel the Hilt-provided ViewModel.
 */
@Composable
fun CallLogScreenRoute(
    onNavigateToDetails: (number: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CallLogEffect.PlaceCall -> context.placeCall(effect.number)
                is CallLogEffect.ComposeMessage -> context.composeSms(effect.number)
                is CallLogEffect.CopyToClipboard -> {
                    context.copyToClipboard(effect.number)
                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                }
                is CallLogEffect.NavigateToDetails -> onNavigateToDetails(effect.number)
                is CallLogEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    CallLogScreen(
        state = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

/**
 * The stateless Recents / Call-log screen (BUILD_SPEC §8, §18). Renders the grouped
 * list, the all/missed filter, the search field, the bulk-selection contextual bar,
 * and the long-press radial quick-actions sheet. All interaction is hoisted via
 * [onEvent]; the only state it owns is local list scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    state: CallLogUiState,
    onEvent: (CallLogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    count = state.selectedCount,
                    onClose = { onEvent(CallLogEvent.ExitSelection) },
                    onDelete = { onEvent(CallLogEvent.DeleteSelected) },
                )
            } else {
                CallLogTopBar(
                    isSearchActive = state.isSearchActive,
                    query = state.searchQuery,
                    onQueryChange = { onEvent(CallLogEvent.Search(it)) },
                    onToggleSearch = { onEvent(CallLogEvent.SetSearchActive(it)) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!state.isSearchActive && !state.selectionMode) {
                    FilterRow(
                        filter = state.filter,
                        missedBadge = state.missedBadgeCount,
                        onFilter = { onEvent(CallLogEvent.SetFilter(it)) },
                    )
                    DottedDivider(modifier = Modifier.padding(horizontal = Dimens.screenPadding))
                }

                when {
                    state.isLoading -> LoadingState()
                    state.isEmpty -> EmptyCallLog(filter = state.filter, searching = state.searchQuery.isNotBlank())
                    else -> CallLogList(state = state, onEvent = onEvent)
                }
            }
        }
    }

    // Long-press radial / strip quick-actions menu (BUILD_SPEC §18).
    QuickActionsSheet(
        target = state.quickActions,
        onAction = { action ->
            state.quickActions?.let { onEvent(CallLogEvent.QuickAction(action, it)) }
        },
        onDismiss = { onEvent(CallLogEvent.DismissQuickActions) },
    )
}

@Composable
private fun CallLogList(
    state: CallLogUiState,
    onEvent: (CallLogEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Dimens.spaceSm),
    ) {
        items(items = state.groups, key = { it.id }) { group ->
            CallLogRow(
                group = group,
                selectionMode = state.selectionMode,
                isSelected = group.id in state.selectedIds,
                onCall = { onEvent(CallLogEvent.CallBack(group)) },
                onLongPress = { onEvent(CallLogEvent.OpenQuickActions(group)) },
                onToggleSelected = { onEvent(CallLogEvent.ToggleSelected(group)) },
                onMessage = { onEvent(CallLogEvent.Message(group)) },
                onDetails = { onEvent(CallLogEvent.OpenDetails(group)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallLogTopBar(
    isSearchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
) {
    TopAppBar(
        title = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search recents") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                )
            } else {
                Text("Recents", style = MaterialTheme.typography.titleLarge)
            }
        },
        navigationIcon = {
            if (isSearchActive) {
                IconButton(onClick = { onToggleSearch(false) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = { onToggleSearch(true) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Exit selection")
            }
        },
        actions = {
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete selected",
                    tint = LocalAccentColor.current,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    filter: CallLogFilter,
    missedBadge: Int,
    onFilter: (CallLogFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter == CallLogFilter.ALL,
            onClick = { onFilter(CallLogFilter.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == CallLogFilter.MISSED,
            onClick = { onFilter(CallLogFilter.MISSED) },
            label = {
                Text(if (missedBadge > 0) "Missed ($missedBadge)" else "Missed")
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DotMatrixSpinner(size = 48.dp, contentDescription = "Loading recents")
    }
}

@Composable
private fun EmptyCallLog(filter: CallLogFilter, searching: Boolean) {
    val (title, message) = when {
        searching -> "No matches" to "No calls match your search."
        filter == CallLogFilter.MISSED -> "No missed calls" to "Calls you miss will show up here."
        else -> "No recents" to "Calls you make and receive will show up here."
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = title,
            message = message,
            icon = if (searching) Icons.Filled.Search else Icons.Filled.History,
        )
    }
}

// ---- Platform-intent helpers (the screen owns these; the VM stays platform-free) ----

private fun Context.placeCall(number: String) {
    // Default-dialer call placement is routed through :app's TelecomManager wiring;
    // from here we fire the standard ACTION_CALL intent which the system delivers to
    // the default dialer (this app once the role is granted). If the role/permission
    // is absent it falls back to ACTION_DIAL (pre-fills the dialpad) so we never crash.
    val telUri = Uri.fromParts(Constants.TEL_SCHEME, number, null)
    val callIntent = Intent(Intent.ACTION_CALL, telUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(callIntent) }.onFailure {
        val dialIntent = Intent(Intent.ACTION_DIAL, telUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(dialIntent) }
    }
}

private fun Context.composeSms(number: String) {
    val smsUri = Uri.fromParts("smsto", number, null)
    val intent = Intent(Intent.ACTION_SENDTO, smsUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.copyToClipboard(number: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", number))
}

// ---- Previews -----------------------------------------------------------------

private fun previewGroup(
    id: Long,
    name: String?,
    number: String,
    type: CallType,
    relative: String,
    count: Int = 1,
    voip: Boolean = false,
    spam: String? = null,
) = CallLogGroup(
    id = id,
    entryIds = listOf(id),
    displayName = name,
    number = number,
    formattedNumber = number,
    photoUri = null,
    dominantType = type,
    timestampMillis = 0L,
    relativeTime = relative,
    count = count,
    durationSeconds = if (type == CallType.MISSED) 0 else 132,
    isVoip = voip,
    isVideo = false,
    spamLabel = spam,
)

private val previewGroups = listOf(
    previewGroup(1, "Ada Lovelace", "+1 415-555-0142", CallType.MISSED, "5m", count = 3),
    previewGroup(2, "Grace Hopper", "+1 202-555-0188", CallType.OUTGOING, "1h"),
    previewGroup(3, null, "+44 20 7946 0991", CallType.INCOMING, "Yesterday", voip = true),
    previewGroup(4, null, "+1 800-555-0000", CallType.BLOCKED, "2d", spam = "Spam"),
)

@Preview(name = "Call log · dark", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCallLogDark() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        CallLogScreen(
            state = CallLogUiState(isLoading = false, groups = previewGroups, missedBadgeCount = 1),
            onEvent = {},
        )
    }
}

@Preview(name = "Call log · missed filter", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCallLogMissed() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        CallLogScreen(
            state = CallLogUiState(
                isLoading = false,
                groups = previewGroups.filter { it.isMissed },
                filter = CallLogFilter.MISSED,
                missedBadgeCount = 1,
            ),
            onEvent = {},
        )
    }
}

@Preview(name = "Call log · empty light", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun PreviewCallLogEmpty() {
    GlyphTheme(themeMode = ThemeMode.LIGHT) {
        CallLogScreen(
            state = CallLogUiState(isLoading = false, groups = emptyList()),
            onEvent = {},
        )
    }
}

@Preview(name = "Call log · selection", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewCallLogSelection() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        CallLogScreen(
            state = CallLogUiState(
                isLoading = false,
                groups = previewGroups,
                selectionMode = true,
                selectedIds = setOf(1L, 3L),
            ),
            onEvent = {},
        )
    }
}
