// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.feature.contacts.list.FavoriteGridItem

/**
 * The favorites photo grid (BUILD_SPEC §8 — "FavoritesGrid (photos)"; §18 dot-matrix
 * fallback for any favorite without a photo).
 *
 * Renders favorites as a fixed-column grid of circular avatars with a name caption.
 * Tap dials the favorite's default number; long-press opens the contact detail
 * (where the default number + speed-dial can be managed). Stateless and themed.
 *
 * Kept inside the parent scroll: the grid is height-bounded and non-scrolling so it
 * composes cleanly above the alphabetised list in the same [LazyVerticalGrid]-free
 * column. For large favorite sets it caps height and scrolls internally.
 *
 * @param favorites resolved favorite tiles (empty → caller should hide the section).
 * @param onCall dial a favorite's default number.
 * @param onOpen open a favorite's contact detail (long-press / manage).
 * @param modifier layout modifier.
 * @param columns tiles per row.
 */
@Composable
fun FavoritesGrid(
    favorites: List<FavoriteGridItem>,
    onCall: (FavoriteGridItem) -> Unit,
    onOpen: (FavoriteGridItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    if (favorites.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "FAVORITES",
            modifier = Modifier.padding(
                start = Dimens.spaceXs,
                bottom = Dimens.spaceSm,
            ),
            style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            // Bounded so the grid sits within the parent column without stealing all
            // the height; it scrolls internally only when there are many favorites.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            items(favorites, key = { it.lookupKey }) { item ->
                FavoriteTile(
                    item = item,
                    onCall = { onCall(item) },
                    onOpen = { onOpen(item) },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(
    item: FavoriteGridItem,
    onCall: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onCall, onLongClick = onOpen)
            .padding(vertical = Dimens.spaceSm, horizontal = Dimens.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        ContactAvatar(
            seed = item.avatarSeed,
            photoUri = item.photoUri,
            size = Dimens.avatarLg,
        )
        Text(
            text = item.displayName ?: item.number,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "FavoritesGrid", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewFavoritesGrid() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        FavoritesGrid(
            favorites = listOf(
                FavoriteGridItem("k1", "Ada Lovelace", null, "+14155550142"),
                FavoriteGridItem("k2", "Alan Turing", null, "+442071234567"),
                FavoriteGridItem("k3", "Grace Hopper", null, "+12025550173"),
                FavoriteGridItem("k4", null, null, "+15105550199"),
            ),
            onCall = {},
            onOpen = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
