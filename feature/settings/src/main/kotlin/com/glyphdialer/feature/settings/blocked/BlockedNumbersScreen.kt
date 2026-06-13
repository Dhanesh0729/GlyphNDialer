// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.BlockedNumber
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.EmptyState
import com.glyphdialer.feature.settings.component.DisclaimerBlock

/**
 * Blocked-numbers sub-screen entry point (BUILD_SPEC §8/§21).
 *
 * Lets the user view, add, and remove blocked numbers. When the app isn't the default
 * dialer, writes are gated and the screen says so honestly (§9).
 */
@Composable
fun BlockedNumbersRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockedNumbersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.refreshRole() }

    BlockedNumbersScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    uiState: BlockedNumbersUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (BlockedNumbersEvent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(BlockedNumbersEvent.DismissError)
        }
    }

    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "BLOCKED NUMBERS",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.screenPadding),
        ) {
            if (!uiState.isDefaultDialer) {
                DisclaimerBlock(
                    text = "Glyph Dialer must be set as your default phone app to manage the " +
                        "system block list. Blocked numbers below are read-only until then.",
                )
            }

            AddRow(
                draft = uiState.draftNumber,
                canAdd = uiState.canAdd && uiState.isDefaultDialer,
                onDraftChange = { onEvent(BlockedNumbersEvent.DraftChanged(it)) },
                onAdd = { onEvent(BlockedNumbersEvent.Add(reportAsSpam = false)) },
            )

            DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceSm))

            Box(Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        com.glyphdialer.core.ui.component.DotMatrixSpinner(
                            size = 40.dp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    uiState.isEmpty -> EmptyState(
                        title = "No blocked numbers",
                        message = "Numbers you block will appear here.",
                        icon = Icons.Filled.Block,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = Dimens.spaceXxl),
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(BlockedTestTags.List),
                        contentPadding = PaddingValues(vertical = Dimens.spaceSm),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                    ) {
                        items(uiState.blocked, key = { it.number.dialValue }) { entry ->
                            BlockedRow(
                                entry = entry,
                                enabled = uiState.isDefaultDialer,
                                onUnblock = { onEvent(BlockedNumbersEvent.Unblock(entry.number.dialValue)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRow(
    draft: String,
    canAdd: Boolean,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("Number to block") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            textStyle = MaterialTheme.typography.bodyLarge.merge(NumberStyle),
        )
        Spacer(Modifier.width(Dimens.spaceSm))
        Button(onClick = onAdd, enabled = canAdd) {
            Text("BLOCK", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
        }
    }
}

@Composable
private fun BlockedRow(
    entry: BlockedNumber,
    enabled: Boolean,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.number.formatted,
                style = MaterialTheme.typography.bodyLarge.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.reportedAsSpam) {
                Text(
                    text = "Reported as spam",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onUnblock, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Unblock ${entry.number.formatted}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Test tags for instrumented Compose tests. */
object BlockedTestTags {
    const val List = "blocked_numbers_list"
}

// --- Previews ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Blocked numbers", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewBlockedNumbers() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        BlockedNumbersScreen(
            uiState = BlockedNumbersUiState(
                isLoading = false,
                isDefaultDialer = true,
                blocked = listOf(
                    BlockedNumber(
                        id = 1,
                        number = PhoneNumber(raw = "+14155550111", formatted = "(415) 555-0111"),
                        reportedAsSpam = true,
                    ),
                    BlockedNumber(
                        id = 2,
                        number = PhoneNumber(raw = "+18005551234", formatted = "(800) 555-1234"),
                    ),
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
