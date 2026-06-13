// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * A dot-matrix-flavored empty state (BUILD_SPEC §18 — "Dot-matrix everything:
 * ... empty states").
 *
 * Centers a small dot-matrix flourish over a [title] and optional [message], with an
 * optional call-to-action. Used for empty call logs, no-contacts, no-voicemail, etc.
 * Stateless and themed.
 *
 * @param title the primary line (rendered as a dot-matrix caption flourish).
 * @param modifier layout modifier.
 * @param message optional supporting copy.
 * @param icon optional Material icon shown above the title (instead of, or with, the
 *   dot-matrix flourish).
 * @param actionLabel optional CTA button label; shown only when [onAction] is set.
 * @param onAction optional CTA handler.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd, Alignment.CenterVertically),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Title as a dot-matrix flourish — the signature "dot-matrix everything" look.
        DotMatrixText(
            text = title.uppercase(),
            dotColor = MaterialTheme.colorScheme.onSurface,
            dotSize = 3.dp,
            dotSpacing = 4.dp,
        )

        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                modifier = Modifier.widthIn(max = 320.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }

        if (actionLabel != null && onAction != null) {
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.padding(top = Dimens.spaceSm),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(name = "EmptyState", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewEmptyState() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        EmptyState(
            title = "No recents",
            message = "Calls you make and receive will show up here.",
            actionLabel = "OPEN DIALPAD",
            onAction = {},
        )
    }
}
