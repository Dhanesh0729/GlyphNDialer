// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.usecase.T9Match
import com.glyphdialer.core.domain.usecase.T9MatchKind
import com.glyphdialer.core.ui.component.DotMatrixAvatar

/**
 * A single T9 smart-search result row (BUILD_SPEC §8 — "render results list; tap to
 * fill/call"). Stateless and themed.
 *
 * Tapping the row body [onFill]s the dialpad from the match; the trailing call button
 * dials [match]'s number directly ([onCall]).
 *
 * @param match the ranked T9 result to render.
 * @param onFill invoked when the row body is tapped (fill the input).
 * @param onCall invoked when the trailing call button is tapped (dial the number).
 * @param modifier layout modifier.
 */
@Composable
fun T9ResultRow(
    match: T9Match,
    onFill: (T9Match) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contact = match.contact
    val number = match.matchedNumber ?: contact.primaryNumber?.formatted ?: ""
    val rowDescription = buildString {
        append(contact.displayName)
        if (number.isNotEmpty()) append(", ").append(number)
    }

    Row(
        modifier = modifier
            .heightIn(min = Dimens.rowHeight)
            .clickable { onFill(match) }
            .semantics { contentDescription = rowDescription }
            .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        DotMatrixAvatar(
            seed = contact.lookupKey.ifEmpty { contact.displayName },
            modifier = Modifier.size(Dimens.avatarSm),
        )

        Column(
            // Take the remaining width between the avatar and the trailing call button.
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs),
        ) {
            Text(
                text = contact.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (number.isNotEmpty()) {
                Text(
                    text = number,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            kindLabel(match.kind)?.let { label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        IconButton(onClick = { onCall(number.ifEmpty { contact.primaryNumber?.dialValue.orEmpty() }) }) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Call ${contact.displayName}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun kindLabel(kind: T9MatchKind): String? = when (kind) {
    T9MatchKind.NUMBER_PREFIX, T9MatchKind.NUMBER_SUBSTRING -> "matches number"
    T9MatchKind.NAME_INITIALS -> "matches initials"
    else -> null
}

@Preview(name = "T9ResultRow", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewT9ResultRow() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        T9ResultRow(
            match = T9Match(
                contact = Contact(
                    id = 1L,
                    lookupKey = "lk1",
                    displayName = "Ada Lovelace",
                    numbers = listOf(PhoneNumber(raw = "+14155550142", formatted = "+1 415-555-0142")),
                ),
                matchedNumber = "+1 415-555-0142",
                kind = T9MatchKind.NAME_PREFIX,
                rank = 0,
            ),
            onFill = {},
            onCall = {},
        )
    }
}
