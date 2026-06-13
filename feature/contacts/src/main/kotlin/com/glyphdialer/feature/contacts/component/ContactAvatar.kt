// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.ui.component.DotMatrixAvatar

/**
 * A contact avatar that renders the real photo when one exists and falls back to the
 * deterministic [DotMatrixAvatar] "caller fingerprint" otherwise (BUILD_SPEC §18 — the
 * dot-matrix fallback ships in :core:ui; the feature pairs it with Coil for photos,
 * exactly as the avatar's own KDoc describes).
 *
 * The dot-matrix fallback is rendered UNDER the [AsyncImage] so it shows instantly
 * during load and stays visible if the photo errors — there is never a blank circle.
 *
 * Stateless and themed.
 *
 * @param seed deterministic source for the fallback (name/number); same seed ⇒ same avatar.
 * @param photoUri content:// URI of the contact photo, or null to use the fallback.
 * @param modifier layout modifier.
 * @param size avatar diameter.
 */
@Composable
fun ContactAvatar(
    seed: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.avatarSm,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Always draw the dot-matrix fingerprint first as the base layer.
        DotMatrixAvatar(seed = seed, size = size)

        if (!photoUri.isNullOrBlank()) {
            // A failed/empty photo simply leaves the dot-matrix fingerprint showing
            // underneath — there is never a blank circle (the §18 fallback contract).
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
