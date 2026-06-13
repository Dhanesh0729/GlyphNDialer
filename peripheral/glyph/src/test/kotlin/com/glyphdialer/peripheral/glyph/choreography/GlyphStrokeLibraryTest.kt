// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph.choreography

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for the pure-Kotlin [GlyphStrokeLibrary] (CONVENTIONS.md §11, BUILD_SPEC
 * §17.4). No Android/GDK types involved — runs on the plain JVM under JUnit5.
 */
class GlyphStrokeLibraryTest {

    private val expectedKeys = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#')

    @Test
    fun `all twelve dialpad keys are present`() {
        assertThat(GlyphStrokeLibrary.keys).containsExactlyElementsIn(expectedKeys)
        assertThat(GlyphStrokeLibrary.keys).hasSize(12)
    }

    @Test
    fun `every key resolves to a non-empty stroke`() {
        for (key in expectedKeys) {
            val stroke = GlyphStrokeLibrary.strokeFor(key)
            assertThat(stroke).isNotNull()
            assertThat(stroke!!.frames).isNotEmpty()
            assertThat(stroke.key).isEqualTo(key)
        }
    }

    @Test
    fun `unknown keys return null`() {
        assertThat(GlyphStrokeLibrary.strokeFor('a')).isNull()
        assertThat(GlyphStrokeLibrary.strokeFor('+')).isNull()
        assertThat(GlyphStrokeLibrary.strokeFor(' ')).isNull()
    }

    @Test
    fun `strokes are distinct - no two keys share an identical frame list`() {
        val frameLists = GlyphStrokeLibrary.all().values.map { it.frames }
        val distinct = frameLists.distinct()
        assertThat(distinct).hasSize(frameLists.size)
    }

    @Test
    fun `strokes are distinct - concepts are unique`() {
        val concepts = GlyphStrokeLibrary.all().values.map { it.concept }
        assertThat(concepts.toSet()).hasSize(concepts.size)
    }

    /**
     * Periods must match the §17.4 "Period" column exactly. The double-beat strokes are
     * expressed there as `2×N` (3 = 2×90 = 180, 8 = 2×80 = 160) including the inter-beat
     * gap accounting, so the realised totals are 180 and 160 respectively.
     */
    @ParameterizedTest
    @CsvSource(
        "1, 120",
        "2, 200",
        "3, 180",
        "4, 200",
        "5, 220",
        "6, 240",
        "7, 320",
        "8, 160",
        "9, 280",
        "0, 100",
        "*, 180",
        "#, 150",
    )
    fun `stroke period matches the spec table`(key: Char, expectedPeriod: Int) {
        val stroke = requireNotNull(GlyphStrokeLibrary.strokeFor(key)) { "missing stroke for '$key'" }
        assertThat(stroke.periodMs).isEqualTo(expectedPeriod)
    }

    @Test
    fun `single short tick lights only the top-right zone`() {
        val one = requireNotNull(GlyphStrokeLibrary.strokeFor('1'))
        assertThat(one.zonesTouched).containsExactly(GlyphZone.TOP_RIGHT)
    }

    @Test
    fun `full flash lights all zones`() {
        val zero = requireNotNull(GlyphStrokeLibrary.strokeFor('0'))
        assertThat(zero.zonesTouched).contains(GlyphZone.ALL)
    }

    @Test
    fun `frame intensities are clamped to the unit interval`() {
        for (stroke in GlyphStrokeLibrary.all().values) {
            for (frame in stroke.frames) {
                assertThat(frame.fromIntensity).isAtLeast(0f)
                assertThat(frame.fromIntensity).isAtMost(1f)
                assertThat(frame.toIntensity).isAtLeast(0f)
                assertThat(frame.toIntensity).isAtMost(1f)
                assertThat(frame.durationMs).isGreaterThan(0)
            }
        }
    }
}
