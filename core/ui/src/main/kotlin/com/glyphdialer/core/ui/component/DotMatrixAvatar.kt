// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.LocalAccentColor
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * A deterministic dot-matrix "caller fingerprint" avatar (BUILD_SPEC §18 — "Caller
 * fingerprint: deterministic dot-matrix avatar ... from number").
 *
 * Generates a stable, symmetric dot pattern from [seed] (a phone number, contact id,
 * or display name). The same seed always yields the same pattern, and the pattern is
 * horizontally mirrored so it reads as an "identicon" rather than random noise. The
 * lit-dot color is also derived from the seed (rotating through the theme accent's
 * hue family) so different callers are visually distinguishable while staying on-brand.
 *
 * Stateless and themed. Use as a fallback when no contact photo exists (the contacts/
 * call-log feature pairs this with Coil for real photos — this module only ships the
 * generated fallback).
 *
 * @param seed the deterministic source (number/name/id). Same seed ⇒ same avatar.
 * @param modifier layout modifier.
 * @param size avatar diameter.
 * @param gridSize dots per side (the left half + center is mirrored to the right).
 * @param accent base hue; defaults to the active accent.
 */
@Composable
fun DotMatrixAvatar(
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.avatarSm,
    gridSize: Int = 7,
    accent: Color = LocalAccentColor.current,
) {
    val grid = remember(seed, gridSize) { fingerprintGrid(seed, gridSize) }
    val dimColor = MaterialTheme.colorScheme.outlineVariant
    val litColor = remember(seed, accent) { tintForSeed(seed, accent) }
    val background = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    ) {
        // Fill background so the circle reads as a chip.
        drawCircle(color = background, radius = this.size.minDimension / 2f, center = center)

        val cols = gridSize
        val rows = gridSize
        val cell = this.size.minDimension / (cols + 1) // +1 for a small inset margin
        val originX = (this.size.width - cell * cols) / 2f
        val originY = (this.size.height - cell * rows) / 2f
        val radius = cell * 0.34f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = originX + cell * c + cell / 2f
                val cy = originY + cell * r + cell / 2f
                drawCircle(
                    color = if (grid[r][c]) litColor else dimColor.copy(alpha = 0.25f),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/**
 * Build a deterministic, horizontally-mirrored boolean grid from [seed]. Pure and
 * allocation-bounded so it can be unit-tested. Uses a small xorshift PRNG seeded by
 * a stable string hash — independent of [String.hashCode], which is not guaranteed
 * stable across processes for our purposes (we want full determinism here).
 */
internal fun fingerprintGrid(seed: String, gridSize: Int): Array<BooleanArray> {
    val n = gridSize.coerceAtLeast(1)
    var state = stableHash(seed)
    fun nextBit(): Boolean {
        // xorshift32
        state = state xor (state shl 13)
        state = state xor (state ushr 17)
        state = state xor (state shl 5)
        return (state and 1) == 1
    }

    val grid = Array(n) { BooleanArray(n) }
    val half = (n + 1) / 2
    for (r in 0 until n) {
        for (c in 0 until half) {
            val on = nextBit()
            grid[r][c] = on
            grid[r][n - 1 - c] = on // mirror for symmetry
        }
    }
    return grid
}

/** A stable, process-independent 32-bit hash (FNV-1a). */
private fun stableHash(s: String): Int {
    var h = -0x7ee3623b // FNV offset basis (2166136261) as Int
    for (ch in s) {
        h = h xor ch.code
        h *= 0x01000193 // FNV prime
    }
    // Avoid a zero state (xorshift gets stuck at 0).
    return if (h == 0) 0x1A2B3C4D else h
}

/** Derive a lit color from [seed], nudging the [accent] hue deterministically. */
private fun tintForSeed(seed: String, accent: Color): Color {
    val h = stableHash(seed)
    // Blend the accent toward white/black slightly by a seed-derived factor so
    // different seeds vary in brightness while staying within the accent family.
    val factor = ((h ushr 8) and 0xFF) / 255f // 0f..1f
    val mix = 0.25f * factor // keep most of the accent identity
    return Color(
        red = accent.red + (1f - accent.red) * mix,
        green = accent.green + (1f - accent.green) * mix,
        blue = accent.blue + (1f - accent.blue) * mix,
        alpha = 1f,
    )
}

@Preview(name = "DotMatrixAvatar", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewDotMatrixAvatar() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DotMatrixAvatar(seed = "+14155550142", size = 56.dp)
            DotMatrixAvatar(seed = "Ada Lovelace", size = 56.dp)
            DotMatrixAvatar(seed = "+442071234567", size = 56.dp)
        }
    }
}
