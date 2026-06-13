// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the dependency-light helpers behind the :core:ui components
 * (CONVENTIONS.md §11 — pure logic via JUnit5 + Truth). No Compose/Android types.
 */
class UiHelpersTest {

    // ---- MonoTimer.formatElapsed ---------------------------------------------

    @Test
    fun `formatElapsed under a minute is MM SS`() {
        assertThat(formatElapsed(7_000L)).isEqualTo("00:07")
    }

    @Test
    fun `formatElapsed minutes and seconds`() {
        assertThat(formatElapsed((3 * 60 + 9) * 1000L)).isEqualTo("03:09")
    }

    @Test
    fun `formatElapsed crosses an hour to H MM SS`() {
        val millis = (1L * 3600 + 23 * 60 + 7) * 1000L
        assertThat(formatElapsed(millis)).isEqualTo("1:23:07")
    }

    @Test
    fun `formatElapsed clamps negatives to zero`() {
        assertThat(formatElapsed(-5_000L)).isEqualTo("00:00")
    }

    @Test
    fun `formatElapsed honors alwaysShowHours`() {
        assertThat(formatElapsed(5_000L, alwaysShowHours = true)).isEqualTo("0:00:05")
    }

    // ---- DotMatrixAvatar.fingerprintGrid -------------------------------------

    @Test
    fun `fingerprint is deterministic for same seed`() {
        val a = fingerprintGrid("+14155550142", 7)
        val b = fingerprintGrid("+14155550142", 7)
        for (r in a.indices) {
            assertThat(b[r].toList()).isEqualTo(a[r].toList())
        }
    }

    @Test
    fun `fingerprint differs for different seeds`() {
        val a = fingerprintGrid("alice", 7).joinToString { it.joinToString("") }
        val b = fingerprintGrid("bob", 7).joinToString { it.joinToString("") }
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `fingerprint is horizontally mirrored`() {
        val n = 7
        val grid = fingerprintGrid("mirror-check", n)
        for (r in 0 until n) {
            for (c in 0 until n) {
                assertThat(grid[r][c]).isEqualTo(grid[r][n - 1 - c])
            }
        }
    }

    // ---- GlyphWaveform.sampleAmplitudes --------------------------------------

    @Test
    fun `sampleAmplitudes returns all zeros for empty input`() {
        val out = sampleAmplitudes(emptyList(), 8)
        assertThat(out.toList()).containsExactly(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f).inOrder()
    }

    @Test
    fun `sampleAmplitudes keeps the most recent samples when oversized`() {
        val source = (1..10).map { it / 10f }
        val out = sampleAmplitudes(source, 3)
        // Tail of the stream: 0.8, 0.9, 1.0
        assertThat(out[0]).isWithin(1e-6f).of(0.8f)
        assertThat(out[1]).isWithin(1e-6f).of(0.9f)
        assertThat(out[2]).isWithin(1e-6f).of(1.0f)
    }

    @Test
    fun `sampleAmplitudes left-pads a short stream with first value`() {
        val out = sampleAmplitudes(listOf(0.5f, 0.6f), 4)
        assertThat(out[0]).isWithin(1e-6f).of(0.5f) // pad with first
        assertThat(out[1]).isWithin(1e-6f).of(0.5f)
        assertThat(out[2]).isWithin(1e-6f).of(0.5f)
        assertThat(out[3]).isWithin(1e-6f).of(0.6f)
    }

    @Test
    fun `sampleAmplitudes coerces values into unit range`() {
        val out = sampleAmplitudes(listOf(-2f, 5f), 2)
        assertThat(out[0]).isWithin(1e-6f).of(0f)
        assertThat(out[1]).isWithin(1e-6f).of(1f)
    }

    // ---- DotMatrixFont -------------------------------------------------------

    @Test
    fun `dotmatrix font has digit coverage`() {
        for (d in '0'..'9') {
            val anyOn = (0 until DotMatrixFont.ROWS).any { r ->
                (0 until DotMatrixFont.COLS).any { c -> DotMatrixFont.isDotOn(d, r, c) }
            }
            assertThat(anyOn).isTrue()
        }
    }

    @Test
    fun `dotmatrix space is blank`() {
        val anyOn = (0 until DotMatrixFont.ROWS).any { r ->
            (0 until DotMatrixFont.COLS).any { c -> DotMatrixFont.isDotOn(' ', r, c) }
        }
        assertThat(anyOn).isFalse()
    }
}
