// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphShapes
import com.glyphdialer.core.designsystem.theme.GlyphSprings
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DotMatrixText
import com.glyphdialer.core.ui.component.DottedDivider

/**
 * The Nothing-style quick-actions menu shown on long-press of a call-log row
 * (BUILD_SPEC §18 — "Quick actions on long-press ... in a Nothing-style
 * radial/strip menu"). Presented as a [ModalBottomSheet] "strip" with a header
 * (dot-matrix name flourish) and a horizontal row of engineered action chips:
 * call, message, record-next, copy, block.
 *
 * Stateless: the caller hoists [target] (non-null ⇒ visible) and the action/dismiss
 * lambdas. Each chip springs in with the signature bouncy motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickActionsSheet(
    target: QuickActionsTarget?,
    onAction: (CallLogQuickAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (target == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = GlyphShapes.Sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            // Header — dot-matrix flourish of the name, plain number below.
            DotMatrixText(
                text = (target.displayName ?: target.number).uppercase(),
                asDots = target.displayName == null, // names render dots only if pure ASCII-ish; numbers always
                dotColor = MaterialTheme.colorScheme.onSurface,
                dotSize = 3.dp,
                dotSpacing = 4.dp,
            )
            Text(
                text = target.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceXs))

            // The radial/strip of actions.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                CallLogQuickAction.entries.forEachIndexed { index, action ->
                    QuickActionChip(
                        action = action,
                        index = index,
                        onClick = { onAction(action) },
                    )
                }
            }
            
            // Today's history
            if (target.history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.spaceMd))
                DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceXs))
                Text(
                    text = "TODAY's HISTORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.spaceSm)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(target.history) { historyItem ->
                        HistoryRow(historyItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(group: CallLogGroup) {
    val visual = callTypeVisual(group.dominantType)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceSm, horizontal = Dimens.spaceMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = visual.contentDescription,
                tint = visual.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(16.dp)
            )
            val base = if (group.count > 1) "${visual.contentDescription} (${group.count})" else visual.contentDescription
            Text(
                text = base,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = group.relativeTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickActionChip(
    action: CallLogQuickAction,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = action.visual()
    // Spring the chip in with a tiny per-index stagger feel via scale animation.
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = GlyphSprings.bouncy(),
        label = "chipScale_$index",
    )
    val accent = LocalAccentColor.current
    val isDestructive = action == CallLogQuickAction.BLOCK
    val iconTint = if (isDestructive) accent else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .padding(horizontal = Dimens.spaceXs)
            .clickable(onClick = onClick)
            .semantics { contentDescription = visual.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .size(Dimens.iconButton + Dimens.spaceSm),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                Dimens.hairline,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = visual.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Preview(name = "QuickActions strip", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewQuickActions() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        // Preview the inner strip directly (the modal host can't render in @Preview).
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DotMatrixText(text = "ADA LOVELACE", dotColor = MaterialTheme.colorScheme.onSurface)
                DottedDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CallLogQuickAction.entries.forEachIndexed { i, a ->
                        QuickActionChip(action = a, index = i, onClick = {})
                    }
                }
            }
        }
    }
}
