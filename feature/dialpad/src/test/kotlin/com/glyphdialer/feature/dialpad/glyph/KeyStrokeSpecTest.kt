// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad.glyph

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pure-logic tests for the §17.4 per-digit stroke mapping and its on-screen mirror
 * intensity (CONVENTIONS.md §11 — "Glyph stroke timing ... reducer/state" unit tests).
 * No Android, no Compose.
 */
class KeyStrokeSpecTest {

    @ParameterizedTest(name = "key {0} → {1} @ {2}ms")
    @CsvSource(
        "1, TICK, 120, 1",
        "2, SWEEP, 200, 1",
        "3, DOUBLE_PULSE, 180, 2",
        "4, SWEEP, 200, 1",
        "5, BLOOM, 220, 1",
        "6, ARC, 240, 1",
        "7, FADE, 320, 1",
        "8, FLICKER, 160, 2",
        "9, SPIRAL, 280, 1",
        "0, FLASH, 100, 1",
        "*, SPARKLE, 180, 1",
        "#, HASH, 150, 1",
    )
    fun forDigit_matchesSpecTable(digit: Char, shape: StrokeShape, periodMs: Int, beats: Int) {
        val spec = KeyStrokeSpec.forDigit(digit)
        assertThat(spec.shape).isEqualTo(shape)
        assertThat(spec.periodMillis).isEqualTo(periodMs)
        assertThat(spec.beats).isEqualTo(beats)
    }

    @Test
    fun sweepDirections_areOppositeFor2And4() {
        assertThat(KeyStrokeSpec.forDigit('2').direction).isEqualTo(StrokeDirection.LEFT_TO_RIGHT)
        assertThat(KeyStrokeSpec.forDigit('4').direction).isEqualTo(StrokeDirection.RIGHT_TO_LEFT)
    }

    @Test
    fun unknownChar_fallsBackToTick_neverThrows() {
        val spec = KeyStrokeSpec.forDigit('Z')
        assertThat(spec.shape).isEqualTo(StrokeShape.TICK)
        assertThat(spec.periodMillis).isGreaterThan(0)
    }

    @Test
    fun dotIntensity_isWithinUnitRange_acrossSweep() {
        val specs = "1234567890*#".map { KeyStrokeSpec.forDigit(it) }
        for (spec in specs) {
            for (step in 0..10) {
                val p = step / 10f
                for (i in 0 until 16) {
                    val v = dotIntensity(spec, index = i, count = 16, progress = p)
                    assertThat(v).isAtLeast(0f)
                    assertThat(v).isAtMost(1f)
                }
            }
        }
    }

    @Test
    fun bloom_lightsCenterFirst() {
        val spec = KeyStrokeSpec.forDigit('5') // BLOOM
        // Near the start of the sweep, the center dot should be lit while the edges aren't.
        val center = dotIntensity(spec, index = 8, count = 16, progress = 0.05f)
        val edge = dotIntensity(spec, index = 0, count = 16, progress = 0.05f)
        assertThat(center).isGreaterThan(edge)
    }
}
