// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.CallType
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.NumberLabel
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixSpinner
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EmptyState
import com.glyphdialer.feature.contacts.component.ContactAvatar

/**
 * Contact Detail screen entry point (BUILD_SPEC §8).
 *
 * Stateful wrapper: collects [ContactDetailUiState], consumes one-shot
 * [ContactDetailEffect]s (dialing/messaging delegated up to the host; edit/up handled
 * by the nav graph), and forwards events to the ViewModel. The layout is the stateless
 * [ContactDetailScreen].
 *
 * @param onNavigateUp pop the detail destination.
 * @param onEdit navigate to the edit destination for the given lookup key.
 * @param onDial host-provided dialer.
 * @param onVideoDial host-provided in-app video caller.
 * @param onMessage host-provided SMS composer.
 */
@Composable
fun ContactDetailRoute(
    onNavigateUp: () -> Unit,
    onEdit: (String) -> Unit,
    onDial: (String) -> Unit,
    onVideoDial: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ContactDetailEffect.PlaceCall -> onDial(effect.number)
                is ContactDetailEffect.PlaceVideoCall -> onVideoDial(effect.number)
                is ContactDetailEffect.ComposeMessage -> onMessage(effect.number)
                is ContactDetailEffect.NavigateToEdit -> onEdit(effect.lookupKey)
                ContactDetailEffect.NavigateUpAfterDelete -> onNavigateUp()
                is ContactDetailEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ContactDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )

    // Speed-dial picker, surfaced over the detail when a number is targeted (§8/§14).
    uiState.speedDialSheet?.let { sheet ->
        SpeedDialSheet(
            target = sheet.number,
            occupiedSlots = uiState.occupiedSlots,
            myAssignedSlots = uiState.assignedSpeedDialSlots,
            onAssign = { slot -> viewModel.onEvent(ContactDetailEvent.AssignSpeedDial(slot)) },
            onClear = { slot -> viewModel.onEvent(ContactDetailEvent.ClearSpeedDial(slot)) },
            onDismiss = { viewModel.onEvent(ContactDetailEvent.DismissSpeedDial) },
        )
    }
}

/** Stateless detail layout: header (photo + name + favorite), numbers with default
 * selection + actions, speed-dial summary, and the recent-interactions timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    uiState: ContactDetailUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ContactDetailEvent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by remember(uiState.contact?.lookupKey) { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(ContactDetailEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.contact?.displayName ?: "CONTACT",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.contact != null) {
                        IconButton(onClick = { onEvent(ContactDetailEvent.ToggleFavorite) }) {
                            Icon(
                                imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (uiState.isFavorite) "Remove favorite" else "Add favorite",
                                tint = if (uiState.isFavorite) LocalAccentColor.current else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { onEvent(ContactDetailEvent.Edit) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit contact")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete contact")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> DotMatrixSpinner(
                    size = 40.dp,
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.isMissing -> EmptyState(
                    title = "Not found",
                    message = "This contact may have been deleted.",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Dimens.spaceXxl),
                )

                uiState.contact != null -> DetailBody(uiState = uiState, contact = uiState.contact, onEvent = onEvent)
            }
        }
    }
    if (showDeleteConfirmation && uiState.contact != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete contact?") },
            text = { Text("This removes the contact from the device contacts provider.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onEvent(ContactDetailEvent.Delete)
                    },
                ) { Text("DELETE") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("CANCEL") }
            },
        )
    }
}

@Composable
private fun DetailBody(
    uiState: ContactDetailUiState,
    contact: Contact,
    onEvent: (ContactDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Dimens.screenPadding,
            vertical = Dimens.spaceLg,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        // ---- Header: photo + name -------------------------------------------------
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            ) {
                ContactAvatar(
                    seed = contact.displayName.ifBlank { contact.primaryNumber?.dialValue ?: contact.lookupKey },
                    photoUri = contact.photoUri ?: contact.thumbnailUri,
                    size = Dimens.avatarLg,
                )
                Text(
                    text = contact.displayName.ifBlank { "(no name)" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                contact.accountType?.let { account ->
                    Text(
                        text = account,
                        style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Numbers --------------------------------------------------------------
        item(key = "numbers_header") { SectionLabel("NUMBERS") }
        items(contact.numbers, key = { "num_${it.raw}" }) { number ->
            NumberRow(
                number = number,
                isDefault = number.dialValue == uiState.effectiveDefaultNumber,
                assignedSlot = uiState.assignedSpeedDialSlots.entries
                    .firstOrNull { it.value == number.dialValue }?.key,
                videoCallEnabled = uiState.videoCallAvailable,
                onCall = { onEvent(ContactDetailEvent.CallNumber(number.dialValue)) },
                onVideoCall = { onEvent(ContactDetailEvent.VideoCallNumber(number.dialValue)) },
                onMessage = { onEvent(ContactDetailEvent.MessageNumber(number.dialValue)) },
                onSetDefault = { onEvent(ContactDetailEvent.SetDefaultNumber(number.dialValue)) },
                onSpeedDial = { onEvent(ContactDetailEvent.OpenSpeedDial(number)) },
            )
        }

        if (contact.numbers.isEmpty()) {
            item(key = "no_numbers") {
                Text(
                    "No phone numbers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Recent interactions --------------------------------------------------
        item(key = "recent_header") {
            Column {
                DottedDivider(modifier = Modifier.padding(bottom = Dimens.spaceLg))
                SectionLabel("RECENT")
            }
        }
        if (uiState.recentInteractions.isEmpty()) {
            item(key = "no_recent") {
                Text(
                    "No recent calls with this contact.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(uiState.recentInteractions, key = { it.entryId }) { interaction ->
                RecentInteractionRow(interaction)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NumberRow(
    number: PhoneNumber,
    isDefault: Boolean,
    assignedSlot: Int?,
    videoCallEnabled: Boolean,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    onMessage: () -> Unit,
    onSetDefault: () -> Unit,
    onSpeedDial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        // Default-number selector (set-default-number, §8).
        RadioButton(selected = isDefault, onClick = onSetDefault)

        Column(modifier = Modifier.weight(1f).clickable(onClick = onCall)) {
            Text(
                text = number.formatted,
                style = MaterialTheme.typography.bodyLarge.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                Text(
                    text = number.labelText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (assignedSlot != null) {
                    Text(
                        text = "· SPEED $assignedSlot",
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        IconButton(onClick = onSpeedDial) {
            Icon(Icons.Filled.Dialpad, contentDescription = "Assign speed dial")
        }
        IconButton(onClick = onMessage) {
            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message")
        }
        IconButton(onClick = onVideoCall, enabled = videoCallEnabled) {
            Icon(Icons.Filled.Videocam, contentDescription = "Video call")
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = LocalAccentColor.current)
        }
    }
}

@Composable
private fun RecentInteractionRow(
    interaction: RecentInteraction,
    modifier: Modifier = Modifier,
) {
    val visual = callTypeIcon(interaction.type)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = visual.contentDescription,
            tint = if (visual.accented) LocalAccentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(interaction.relativeTime)
                    if (interaction.isVoip) append(" · VoIP")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (interaction.durationSeconds > 0 && interaction.type != CallType.MISSED) {
                Text(
                    text = RelativeTime.formatDuration(interaction.durationSeconds),
                    style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Human label for a number's [NumberLabel] (custom labels show their free text). */
private fun PhoneNumber.labelText(): String = when (label) {
    NumberLabel.MOBILE -> "Mobile"
    NumberLabel.HOME -> "Home"
    NumberLabel.WORK -> "Work"
    NumberLabel.MAIN -> "Main"
    NumberLabel.FAX_WORK -> "Work fax"
    NumberLabel.FAX_HOME -> "Home fax"
    NumberLabel.PAGER -> "Pager"
    NumberLabel.OTHER -> "Other"
    NumberLabel.CUSTOM -> customLabel ?: "Custom"
}

// --- Previews ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "ContactDetail", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewContactDetail() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        ContactDetailScreen(
            uiState = ContactDetailUiState(
                isLoading = false,
                contact = Contact(
                    id = 1,
                    lookupKey = "k1",
                    displayName = "Ada Lovelace",
                    numbers = listOf(
                        PhoneNumber(raw = "+14155550142", formatted = "(415) 555-0142", label = NumberLabel.MOBILE, isPrimary = true),
                        PhoneNumber(raw = "+14155550199", formatted = "(415) 555-0199", label = NumberLabel.WORK),
                    ),
                    accountType = "com.google",
                    isFavorite = true,
                ),
                isFavorite = true,
                defaultNumber = "+14155550142",
                assignedSpeedDialSlots = mapOf(2 to "+14155550142"),
                recentInteractions = listOf(
                    RecentInteraction(1, "(415) 555-0142", CallType.OUTGOING, "5m ago", 142, false),
                    RecentInteraction(2, "(415) 555-0142", CallType.MISSED, "Yesterday", 0, false),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
