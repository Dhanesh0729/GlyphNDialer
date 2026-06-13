// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.SpeedDialSlot
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DottedDivider

/**
 * A modal bottom sheet for assigning a contact number to a dialpad speed-dial key
 * (BUILD_SPEC §8 — "speed-dial assignment surfaced"; §14). Keys are 2–9 (1 is
 * reserved for voicemail, 0 for "+", per the domain [SpeedDialSlot] contract).
 *
 * Slots already taken by another contact are disabled. A slot already mapped to one
 * of this contact's numbers shows as "assigned" and tapping it clears the mapping.
 *
 * @param target the number being assigned.
 * @param occupiedSlots slots taken app-wide (disabled unless they're [myAssignedSlots]).
 * @param myAssignedSlots slot → number this contact already occupies (tap to clear).
 * @param onAssign assign [target] to the chosen slot.
 * @param onClear clear a slot this contact occupies.
 * @param onDismiss dismiss the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialSheet(
    target: PhoneNumber,
    occupiedSlots: Set<Int>,
    myAssignedSlots: Map<Int, String>,
    onAssign: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Text(
                text = "ASSIGN SPEED DIAL",
                style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = target.formatted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DottedDivider()

            val slots = (SpeedDialSlot.MIN_SLOT..SpeedDialSlot.MAX_SLOT).toList()
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Dimens.spaceSm),
            ) {
                items(slots, key = { it }) { slot ->
                    val mineNumber = myAssignedSlots[slot]
                    val mine = mineNumber != null
                    // Disabled only if taken by someone else (not me).
                    val takenByOther = slot in occupiedSlots && !mine
                    SlotKey(
                        slot = slot,
                        state = when {
                            mine -> SlotState.MINE
                            takenByOther -> SlotState.TAKEN
                            else -> SlotState.FREE
                        },
                        onClick = {
                            when {
                                mine -> onClear(slot)
                                takenByOther -> Unit // disabled
                                else -> onAssign(slot)
                            }
                        },
                    )
                }
            }

            Text(
                text = "Keys 2–9 only · 1 is voicemail, 0 is +",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spaceSm, bottom = Dimens.spaceLg),
            )
        }
    }
}

private enum class SlotState { FREE, MINE, TAKEN }

@Composable
private fun SlotKey(
    slot: Int,
    state: SlotState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val (bg, fg) = when (state) {
        SlotState.FREE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        SlotState.MINE -> accent to MaterialTheme.colorScheme.onPrimary
        SlotState.TAKEN -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) to
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .size(Dimens.minTouchTarget)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .then(
                if (state == SlotState.TAKEN) Modifier else Modifier.clickable(onClick = onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = slot.toString(),
            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
            color = fg,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "SpeedDialSheet", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewSpeedDialSheet() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        // Preview the body content directly (ModalBottomSheet needs a host at runtime).
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ASSIGN SPEED DIAL", style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items((SpeedDialSlot.MIN_SLOT..SpeedDialSlot.MAX_SLOT).toList()) { slot ->
                    SlotKey(
                        slot = slot,
                        state = when (slot) {
                            3 -> SlotState.MINE
                            5 -> SlotState.TAKEN
                            else -> SlotState.FREE
                        },
                        onClick = {},
                    )
                }
            }
        }
    }
}
