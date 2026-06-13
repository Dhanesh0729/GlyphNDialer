// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphSprings
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.ui.component.DotMatrixAvatar

/**
 * One call-log row (BUILD_SPEC §8): avatar (photo via Coil, else dot-matrix
 * fingerprint), name/number, the call-type icon + relative time + group count,
 * VoIP/video badges. Wrapped in a [SwipeToDismissBox] so a swipe one way composes a
 * message and the other way opens details; supports tap-to-call-back, long-press →
 * quick actions, and a selection checkmark in bulk-select mode.
 *
 * Stateless: all interaction is hoisted to lambdas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallLogRow(
    group: CallLogGroup,
    selectionMode: Boolean,
    isSelected: Boolean,
    onCall: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
    onMessage: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // Reset after a directional swipe — these are "reveal an action" gestures,
        // not destructive dismisses, so we never actually remove the item.
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onMessage(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDetails(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
    ) {
        RowContent(
            group = group,
            selectionMode = selectionMode,
            isSelected = isSelected,
            onCall = onCall,
            onLongPress = onLongPress,
            onToggleSelected = onToggleSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowContent(
    group: CallLogGroup,
    selectionMode: Boolean,
    isSelected: Boolean,
    onCall: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
) {
    val accent = LocalAccentColor.current
    val visual = remember(group.dominantType) { callTypeVisual(group.dominantType) }
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelected() else onCall() },
                onLongClick = { if (!selectionMode) onLongPress() },
            )
            .heightIn(min = Dimens.rowHeight)
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        // Leading: selection check, contact photo, or dot-matrix fingerprint.
        Box(
            modifier = Modifier.size(Dimens.avatarSm),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selectionMode -> SelectionMark(isSelected)
                group.photoUri != null -> AsyncImage(
                    model = group.photoUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(Dimens.avatarSm)
                        .clip(CircleShape),
                )
                else -> DotMatrixAvatar(seed = group.avatarSeed, size = Dimens.avatarSm)
            }
        }

        // Title + subtitle.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (group.isMissed) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = visual.contentDescription,
                    tint = visual.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = buildSubtitle(group),
                    style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.isVoip) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = "In-app VoIP call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (group.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Video call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        // Trailing: relative time (and spam label when present).
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = group.relativeTime,
                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!group.spamLabel.isNullOrBlank()) {
                Text(
                    text = group.spamLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SelectionMark(isSelected: Boolean) {
    val accent = LocalAccentColor.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = GlyphSprings.bouncy(),
        label = "selectionScale",
    )
    Box(
        modifier = Modifier
            .size(Dimens.avatarSm)
            .clip(CircleShape)
            .background(
                if (isSelected) accent else MaterialTheme.colorScheme.surfaceVariant,
            )
            .semantics { contentDescription = if (isSelected) "Selected" else "Not selected" },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp * scale),
            )
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (icon, alignment, label, color) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> SwipeDecor(
            Icons.AutoMirrored.Filled.Message,
            Alignment.CenterStart,
            "Message",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwipeToDismissBoxValue.EndToStart -> SwipeDecor(
            Icons.Filled.Info,
            Alignment.CenterEnd,
            "Details",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwipeToDismissBoxValue.Settled -> SwipeDecor(
            Icons.Filled.Info,
            Alignment.CenterEnd,
            "",
            Color.Transparent,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spaceXl),
        contentAlignment = alignment,
    ) {
        if (color != Color.Transparent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = color)
                Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private data class SwipeDecor(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val alignment: Alignment,
    val label: String,
    val color: Color,
)

/** Builds the row subtitle: formatted number, plus a "(N)" group count when > 1. */
private fun buildSubtitle(group: CallLogGroup): String {
    val base = group.formattedNumber
    return if (group.count > 1) "$base  (${group.count})" else base
}
