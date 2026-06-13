// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.domain.model.AccentColor
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * @Preview swatches for the Glyph Dialer design system. These render the palette,
 * typography scale, and accent set so the look can be eyeballed in the IDE without
 * wiring a full screen. Preview-only — not part of the shipped public API.
 */

@Composable
private fun ColorSwatch(name: String, color: Color, onColor: Color) {
    Column(
        modifier = Modifier
            .size(width = 96.dp, height = 64.dp)
            .background(color, RoundedCornerShape(8.dp))
            .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(Dimens.spaceSm),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = name,
            color = onColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PaletteRow() {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        ColorSwatch("surface", scheme.surface, scheme.onSurface)
        ColorSwatch("variant", scheme.surfaceVariant, scheme.onSurfaceVariant)
        ColorSwatch("primary", scheme.primary, scheme.onPrimary)
    }
}

@Composable
private fun TypeSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text("Glyph Dialer", style = MaterialTheme.typography.headlineSmall)
        Text("0123456789", style = MaterialTheme.typography.bodyLarge.merge(NumberStyle))
        Text(
            "+1 (415) 555-0142",
            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
            fontWeight = FontWeight.Medium,
        )
        Text("the quick brown fox", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AccentStrip() {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        AccentColor.entries.forEach { accent ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(GlyphColors.accentColorOf(accent), RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun SwatchBoard() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            PaletteRow()
            TypeSpecimen()
            AccentStrip()
        }
    }
}

@Preview(name = "Dark · OG dot-matrix", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDarkOg() {
    GlyphTheme(themeMode = ThemeMode.DARK, appFont = AppFont.OG_DOT_MATRIX) {
        SwatchBoard()
    }
}

@Preview(name = "Light · New grotesque", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun PreviewLightNew() {
    GlyphTheme(themeMode = ThemeMode.LIGHT, appFont = AppFont.NEW_GROTESQUE) {
        SwatchBoard()
    }
}

@Preview(name = "Dark · accent variants", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewAccents() {
    val accents = AccentColor.entries
    GlyphTheme(
        themeMode = ThemeMode.DARK,
        appFont = AppFont.OG_DOT_MATRIX,
        accent = accents.getOrElse(1) { accents.first() },
    ) {
        SwatchBoard()
    }
}
