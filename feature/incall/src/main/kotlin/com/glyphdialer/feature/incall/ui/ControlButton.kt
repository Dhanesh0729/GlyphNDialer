// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle

/**
 * A circular in-call control button (mute/route/hold/record/etc.) with an underline
 * label, styled to the engineered monochrome aesthetic.
 *
 * When [active] the fill switches to the accent (or a custom [activeColor]); when
 * [enabled] is false it dims and ignores taps (honest disabled state for unsupported
 * capabilities — §9). Stateless: the caller owns [active]/[enabled] and [onClick].
 */
@Composable
fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = label,
) {
    val fill = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        active -> activeColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        active -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.callButtonSize)
                .clip(CircleShape)
                .background(fill, CircleShape)
                .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickableRole(enabled, onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = content,
                modifier = Modifier.size(Dimens.iconButton.div(2).plus(4.dp)),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Clickable only when [enabled]; carries the Button role for accessibility. */
private fun Modifier.clickableRole(enabled: Boolean, onClick: () -> Unit): Modifier =
    androidx.compose.foundation.clickable(
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )

/** A larger primary action (end / answer) — solid filled circle. */
@Composable
fun PrimaryCallButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
) {
    Box(
        modifier = modifier
            .size(Dimens.callButtonSize.plus(8.dp))
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .clickableRole(enabled = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconButton),
            )
        }
    }
}
