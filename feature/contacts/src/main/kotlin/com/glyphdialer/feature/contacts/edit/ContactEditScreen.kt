// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.NumberLabel
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixSpinner

/**
 * Contact Create/Edit screen entry point (BUILD_SPEC §8).
 *
 * Stateful wrapper: collects [ContactEditUiState], consumes one-shot
 * [ContactEditEffect]s ([ContactEditEffect.Saved] hands the new lookup key up so the
 * host can land on the detail), and forwards events to the ViewModel.
 *
 * @param onNavigateUp pop the edit destination (cancel).
 * @param onSaved called with the saved contact's lookup key for post-save navigation.
 */
@Composable
fun ContactEditRoute(
    onNavigateUp: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ContactEditEffect.Saved -> onSaved(effect.lookupKey)
                is ContactEditEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ContactEditScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

/** Stateless create/edit form: a name field plus a dynamic list of labeled number
 * rows, with a top-bar save action gated on [ContactEditUiState.canSave]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    uiState: ContactEditUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ContactEditEvent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(ContactEditEvent.DismissError)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(uiState.title, style = MaterialTheme.typography.titleMedium.merge(NumberStyle)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onEvent(ContactEditEvent.Save) },
                        enabled = uiState.canSave,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text("SAVE", modifier = Modifier.padding(start = Dimens.spaceXs))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                DotMatrixSpinner(size = 40.dp, modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            ) {
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = { onEvent(ContactEditEvent.NameChanged(it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.numbers.forEach { draft ->
                    NumberDraftRow(
                        draft = draft,
                        onValueChange = { onEvent(ContactEditEvent.NumberChanged(draft.id, it)) },
                        onLabelChange = { onEvent(ContactEditEvent.LabelChanged(draft.id, it)) },
                        onRemove = { onEvent(ContactEditEvent.RemoveNumber(draft.id)) },
                    )
                }

                OutlinedButton(
                    onClick = { onEvent(ContactEditEvent.AddNumber) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add number", modifier = Modifier.padding(start = Dimens.spaceXs))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberDraftRow(
    draft: NumberDraft,
    onValueChange: (String) -> Unit,
    onLabelChange: (NumberLabel) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        OutlinedTextField(
            value = draft.value,
            onValueChange = onValueChange,
            label = { Text("Phone") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.weight(1f),
        )

        LabelPicker(selected = draft.label, onSelected = onLabelChange)

        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove number")
        }
    }
}

@Composable
private fun LabelPicker(
    selected: NumberLabel,
    onSelected: (NumberLabel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(selected.displayText(), style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change label")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NumberLabel.entries.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label.displayText()) },
                    onClick = {
                        onSelected(label)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Short, human-friendly label text for the picker. */
private fun NumberLabel.displayText(): String = when (this) {
    NumberLabel.MOBILE -> "Mobile"
    NumberLabel.HOME -> "Home"
    NumberLabel.WORK -> "Work"
    NumberLabel.MAIN -> "Main"
    NumberLabel.FAX_WORK -> "Work fax"
    NumberLabel.FAX_HOME -> "Home fax"
    NumberLabel.PAGER -> "Pager"
    NumberLabel.OTHER -> "Other"
    NumberLabel.CUSTOM -> "Custom"
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "ContactEdit · create", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewContactEditCreate() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        ContactEditScreen(
            uiState = ContactEditUiState(
                isEditing = false,
                displayName = "Ada Lovelace",
                numbers = listOf(
                    NumberDraft(value = "(415) 555-0142", label = NumberLabel.MOBILE),
                    NumberDraft(value = "(415) 555-0199", label = NumberLabel.WORK),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
