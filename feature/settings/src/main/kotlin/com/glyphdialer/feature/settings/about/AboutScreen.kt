// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DottedDivider
import com.glyphdialer.feature.settings.LegalText

/**
 * About / legal sub-screen (BUILD_SPEC §21).
 *
 * Static, scrollable presentation of the reviewed legal and attribution copy from
 * [LegalText]: the recording-law disclaimer (§2/§9), open-font attribution (§10/§16),
 * and the Glyph SDK / brand attribution (§9/§17). Open-source licenses are delegated
 * to the host platform screen via [onOpenOpenSourceLicenses].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(
    onNavigateUp: () -> Unit,
    onOpenOpenSourceLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ABOUT & LEGAL",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            Text(
                text = "GLYPH DIALER",
                style = MaterialTheme.typography.headlineSmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = LegalText.APP_TAGLINE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LegalSection(title = "Call recording", body = LegalText.RECORDING_LAW_DISCLAIMER)
            LegalSection(title = "No-announcement recording", body = LegalText.NO_ANNOUNCEMENT_DISCLAIMER)
            LegalSection(title = "Fonts", body = LegalText.FONT_ATTRIBUTION)
            LegalSection(title = "Glyph & brand", body = LegalText.GLYPH_SDK_ATTRIBUTION)

            DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceSm))

            OutlinedButton(
                onClick = onOpenOpenSourceLicenses,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "OPEN-SOURCE LICENSES",
                    style = MaterialTheme.typography.labelLarge.merge(NumberStyle),
                )
            }
        }
    }
}

@Composable
private fun LegalSection(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Dimens.spaceSm)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
            color = MaterialTheme.colorScheme.primary,
        )
        DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceXs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Preview -----------------------------------------------------------------------

@Preview(name = "About & legal", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAbout() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        AboutRoute(onNavigateUp = {}, onOpenOpenSourceLicenses = {})
    }
}
