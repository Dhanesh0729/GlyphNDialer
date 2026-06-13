// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * One contact list row: avatar (photo or dot-matrix fingerprint) + name + a hint of
 * the primary number, with a favorite star when applicable (BUILD_SPEC §8/§18).
 * Stateless and themed; tapping the row opens the contact detail.
 *
 * @param contact the contact to render.
 * @param onClick open the contact's detail.
 * @param modifier layout modifier.
 */
@Composable
fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowHeight)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        ContactAvatar(
            seed = contact.displayName.ifBlank { contact.primaryNumber?.dialValue ?: contact.lookupKey },
            photoUri = contact.thumbnailUri ?: contact.photoUri,
            size = Dimens.avatarSm,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName.ifBlank { contact.primaryNumber?.formatted ?: "(unknown)" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val primary = contact.primaryNumber
            if (primary != null && contact.displayName.isNotBlank()) {
                Text(
                    text = primary.formatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (contact.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favorite",
                tint = LocalAccentColor.current,
                modifier = Modifier.padding(end = Dimens.spaceSm),
            )
        }
    }
}

@Preview(name = "ContactRow", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewContactRow() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Column {
            ContactRow(
                contact = Contact(
                    id = 1,
                    lookupKey = "k1",
                    displayName = "Ada Lovelace",
                    numbers = listOf(PhoneNumber(raw = "+14155550142", formatted = "(415) 555-0142")),
                    isFavorite = true,
                ),
                onClick = {},
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ContactRow(
                contact = Contact(
                    id = 2,
                    lookupKey = "k2",
                    displayName = "Alan Turing",
                    numbers = listOf(PhoneNumber(raw = "+442071234567", formatted = "+44 20 7123 4567")),
                ),
                onClick = {},
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
