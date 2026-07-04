// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.ui.component.DottedDivider

/**
 * The shared, themed building blocks for the Settings screen (BUILD_SPEC §15/§21).
 *
 * All primitives are stateless (state hoisted to the screen) and lean on the design
 * system: monospaced section labels, dotted dividers, hairline framing, the single
 * accent. Each is independently previewable and reusable across every group.
 */

private const val DISABLED_ALPHA = 0.38f

/** A monospaced, all-caps group label with a dotted rule (the §21 "monospaced labels"). */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(top = Dimens.spaceLg, bottom = Dimens.spaceSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = MaterialTheme.colorScheme.primary,
            )
            if (expanded != null) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.padding(top = Dimens.spaceXs))
        DottedDivider()
    }
}

@Composable
fun SettingsFolder(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(
            title = title,
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * A label + optional supporting text on the start, with arbitrary [trailing] content
 * (switch, value, chevron) on the end. The base of every settings row.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowHeight)
            .padding(vertical = Dimens.spaceSm)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Dimens.spaceMd))
            Box(contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/** A toggle row. [enabled] greys out and blocks interaction (e.g. a gated capability). */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    )
}

/**
 * A row that navigates somewhere on tap (chevron affordance). Optional [valueLabel]
 * shows the current selection inline (e.g. "12 numbers", "All accounts").
 */
@Composable
fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueLabel: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowHeight)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Dimens.spaceSm)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!valueLabel.isNullOrBlank()) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Dimens.spaceSm))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A read-only status indicator row (NOT interactive). Used for the honesty-critical
 * "active recording tier" display and contacts sync status (§9/§12/§21). The [value]
 * is rendered in the monospaced style with an optional [accent] tint.
 */
@Composable
fun SettingsStatusRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            Text(
                text = value.uppercase(),
                style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                color = accent,
            )
        },
    )
}

/**
 * A horizontally-laid-out single-choice selector rendered as filter chips. Generic
 * over the option type [T]; [label] maps each option to its display string. Used for
 * theme, font, accent, retention window, and engine selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SettingsChipSelector(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.padding(top = Dimens.spaceXs))
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                        )
                    },
                    shape = RoundedCornerShape(Dimens.spaceSm),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

/** A labeled slider for a continuous 0f..1f value (e.g. Glyph intensity, §17.4). */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceSm)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(value.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = 0f..1f,
        )
    }
}

/**
 * A small framed disclaimer block (§2.2 legal text). Rendered with a hairline border
 * so it reads as a caution without shouting. Stateless.
 */
@Composable
fun DisclaimerBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceSm)
            .border(
                width = Dimens.hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(Dimens.spaceSm),
            )
            .padding(Dimens.spaceMd),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
