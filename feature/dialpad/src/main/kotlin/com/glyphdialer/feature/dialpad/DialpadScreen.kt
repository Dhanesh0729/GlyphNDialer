// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.AppPlan
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.usecase.T9Match
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.core.ui.component.GlyphKey
import com.glyphdialer.core.ui.component.PlanBadge
import com.glyphdialer.feature.dialpad.component.T9ResultRow
import com.glyphdialer.feature.dialpad.glyph.KeyStrokeMirror

/**
 * Route-level wrapper (CONVENTIONS.md §5): obtains the [DialpadViewModel], collects
 * [DialpadUiState] with lifecycle awareness, wires one-shot [DialpadEffect]s to the
 * host (snackbar, add-contact intent, speed-dial assignment), and delegates rendering
 * to the stateless [DialpadContent].
 *
 * @param onNavigateToAddContact host handler for [DialpadEffect.AddToContacts] (opens
 *   the platform insert-contact intent prefilled with the number).
 * @param onAssignSpeedDial host handler for [DialpadEffect.AssignSpeedDial] (opens a
 *   contact picker for the given slot; the result is fed back via
 *   [DialpadViewModel.assignSpeedDial]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialpadRouteScreen(
    modifier: Modifier = Modifier,
    onNavigateToAddContact: (number: String) -> Unit = {},
    onAssignSpeedDial: (slot: Int) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: DialpadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DialpadEffect.AddToContacts -> onNavigateToAddContact(effect.number)
                is DialpadEffect.AssignSpeedDial -> onAssignSpeedDial(effect.slot)
                is DialpadEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                DialpadEffect.CallPlaced -> Unit // system in-call UI takes over
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            DialpadTopBar(
                plan = uiState.plan,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        DialpadContent(
            state = uiState,
            onEvent = viewModel::onEvent,
            onPasteRequested = { viewModel.onEvent(DialpadEvent.Paste(readClipboardText(context))) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialpadTopBar(
    plan: AppPlan,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "GLYPH DIALER",
                    style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                )
                Spacer(Modifier.width(Dimens.spaceSm))
                PlanBadge(
                    text = plan.name,
                    isPremium = plan != AppPlan.FREE
                )
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier,
    )
}

/**
 * The STATELESS dialpad content (CONVENTIONS.md §5 — state hoisting): renders
 * [DialpadUiState] and emits [DialpadEvent]s. No ViewModel, no Android framework
 * dependency beyond plain Compose, so it is trivially previewable and testable.
 *
 * Layout (top→bottom): the formatted number display + edit affordances, the on-screen
 * Glyph stroke mirror (§17.4/§18 ★), the live T9 results list, the T9 keypad, and the
 * prominent call button.
 *
 * @param state the immutable UI state.
 * @param onEvent intent sink.
 * @param onPasteRequested invoked when the paste affordance is tapped; the wrapper
 *   reads the clipboard and dispatches [DialpadEvent.Paste] (kept out of this stateless
 *   layer so it stays framework-free).
 */
@Composable
fun DialpadContent(
    state: DialpadUiState,
    onEvent: (DialpadEvent) -> Unit,
    onPasteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Dimens.spaceMd))

        NumberDisplay(
            state = state,
            onBackspace = { onEvent(DialpadEvent.Backspace) },
            onClearAll = { onEvent(DialpadEvent.ClearAll) },
            onPaste = onPasteRequested,
            onAddContact = { onEvent(DialpadEvent.AddContact) },
        )

        // ★ On-screen mirror of the per-key Glyph stroke (always shown when the user
        //   enabled dialpad strokes; the physical Glyph stroke is fired separately).
        if (state.glyphMirrorEnabled) {
            KeyStrokeMirror(
                trigger = state.lastStroke,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spaceXs),
            )
        } else {
            Spacer(Modifier.height(Dimens.spaceSm))
        }

        DottedDivider(modifier = Modifier.padding(bottom = Dimens.spaceSm))

        // Live T9 smart-search results occupy the flexible middle region.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            T9Results(
                results = state.t9Results,
                onFill = { onEvent(DialpadEvent.FillFromMatch(it)) },
                onCall = { onEvent(DialpadEvent.CallNumber(it)) },
            )
        }

        Keypad(
            hapticFeedbackEnabled = state.hapticFeedbackEnabled,
            onKeyPress = { onEvent(DialpadEvent.KeyPress(it)) },
            onLongPress = { onEvent(DialpadEvent.LongPress(it)) },
        )

        Spacer(Modifier.height(Dimens.spaceMd))

        CallButton(
            enabled = state.canCall,
            onClick = { onEvent(DialpadEvent.Call) },
        )

        Spacer(Modifier.height(Dimens.spaceLg))
    }
}

/** The number-entry display row with backspace / clear / paste / add-contact affordances. */
@Composable
private fun NumberDisplay(
    state: DialpadUiState,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onPaste: () -> Unit,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Paste / add-contact on the leading edge.
        IconButton(onClick = onPaste) {
            Icon(
                imageVector = Icons.Filled.ContentPaste,
                contentDescription = "Paste number",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = state.formattedNumber.ifEmpty { state.entered },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Entered number: ${state.entered}" },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall.merge(NumberStyle),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (state.hasInput) {
            IconButton(onClick = onAddContact) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = "Add to contacts",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Backspace: tap deletes one char, long-press clears all (BUILD_SPEC §8).
        BackspaceKey(
            visible = state.hasInput,
            onBackspace = onBackspace,
            onClearAll = onClearAll,
        )
    }
}

@Composable
private fun BackspaceKey(
    visible: Boolean,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val haptic = LocalHapticFeedback.current
        // Tap deletes one character; long-press clears all (BUILD_SPEC §8). A raw
        // pointerInput gives us the tap + long-press pair without an extra widget.
        Box(
            modifier = Modifier
                .size(Dimens.iconButton)
                .clip(CircleShape)
                .semantics {
                    contentDescription = "Delete last digit, long press to clear all"
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onBackspace() },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearAll()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The live T9 smart-search results list (BUILD_SPEC §8). */
@Composable
private fun T9Results(
    results: List<T9Match>,
    onFill: (T9Match) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs),
    ) {
        items(
            items = results,
            key = { it.contact.lookupKey.ifEmpty { it.contact.id.toString() } },
        ) { match ->
            T9ResultRow(match = match, onFill = onFill, onCall = onCall)
        }
    }
}

/** The prominent, accent-filled call button (BUILD_SPEC §8). */
@Composable
private fun CallButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .size(Dimens.callButtonSize)
            .clip(CircleShape)
            .semantics { contentDescription = "Call" },
        shape = CircleShape,
        color = container,
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = null,
                tint = content,
            )
        }
    }
}

/**
 * The 4×3 T9 keypad (BUILD_SPEC §8). Each cell is the shared [GlyphKey] from
 * `:core:ui`, wired so a tap appends ([onKeyPress]) and a long-press fires the
 * shortcut ([onLongPress]).
 *
 * The Glyph stroke is intentionally NOT triggered from [GlyphKey.onDigitStroke] here:
 * the ViewModel fires both the physical stroke and the on-screen mirror off the
 * [DialpadEvent.KeyPress] it receives, so the mirror and the physical Glyph stay in
 * lock-step regardless of how the key was triggered (and honesty-gated in one place).
 */
@Composable
private fun Keypad(
    hapticFeedbackEnabled: Boolean,
    onKeyPress: (Char) -> Unit,
    onLongPress: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KEYPAD_ROWS.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    GlyphKey(
                        digit = key.digit,
                        letters = key.letters,
                        onPress = {
                            if (hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onKeyPress(it)
                        },
                        onLongPress = { onLongPress(key.digit) },
                        // onDigitStroke deliberately left as the default no-op; see kdoc.
                    )
                }
            }
        }
    }
}

/** One physical key's label (digit + ITU letters / shortcut hint). */
private data class KeyDef(val digit: Char, val letters: String)

/** The standard ITU T9 layout, with `0`'s `+` hint and the `* / #` row (BUILD_SPEC §8). */
private val KEYPAD_ROWS: List<List<KeyDef>> = listOf(
    listOf(KeyDef('1', "VM"), KeyDef('2', "ABC"), KeyDef('3', "DEF")),
    listOf(KeyDef('4', "GHI"), KeyDef('5', "JKL"), KeyDef('6', "MNO")),
    listOf(KeyDef('7', "PQRS"), KeyDef('8', "TUV"), KeyDef('9', "WXYZ")),
    listOf(KeyDef('*', ""), KeyDef('0', "+"), KeyDef('#', "")),
)

/** Read the most recent clipboard text item, or empty when none. */
private fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
}

@Preview(name = "Dialpad · entered", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDialpadEntered() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        DialpadContent(
            state = DialpadUiState(
                entered = "4155550142",
                formattedNumber = "(415) 555-0142",
                glyphMirrorEnabled = true,
            ),
            onEvent = {},
            onPasteRequested = {},
        )
    }
}

@Preview(name = "Dialpad · empty", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDialpadEmpty() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        DialpadContent(state = DialpadUiState(), onEvent = {}, onPasteRequested = {})
    }
}
