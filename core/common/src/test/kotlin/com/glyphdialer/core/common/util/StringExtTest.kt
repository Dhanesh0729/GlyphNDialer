// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Representative JUnit5 + Truth unit test for the string extensions
 * (CONVENTIONS.md §11). Covers the dialer-critical [digitsOnly] / [dialableChars]
 * plus the display helpers [ellipsize] and [initials].
 */
class StringExtTest {

    @Test
    fun `digitsOnly strips all non-digit characters`() {
        assertThat("+1 (415) 555-0132".digitsOnly()).isEqualTo("14155550132")
    }

    @Test
    fun `digitsOnly on an empty string returns empty`() {
        assertThat("".digitsOnly()).isEqualTo("")
    }

    @ParameterizedTest
    @CsvSource(
        "'+1 (415) 555-0132', '+14155550132'",
        "'(800) 555 *0#', '800555*0#'",
        "'call +44 then', '44'", // '+' kept only when first surviving char
    )
    fun `dialableChars keeps digits leading-plus and DTMF symbols`(input: String, expected: String) {
        assertThat(input.dialableChars()).isEqualTo(expected)
    }

    @Test
    fun `dialableChars drops a non-leading plus`() {
        // The '+' is not the first character, so it must be stripped.
        assertThat("1+2".dialableChars()).isEqualTo("12")
    }

    @Test
    fun `ellipsize leaves short strings untouched`() {
        assertThat("hi".ellipsize(maxLength = 10)).isEqualTo("hi")
    }

    @Test
    fun `ellipsize never exceeds maxLength including the ellipsis`() {
        val result = "transcription ticker".ellipsize(maxLength = 8)
        assertThat(result.length).isAtMost(8)
        assertThat(result).endsWith("…")
    }

    @Test
    fun `initials derives two letters from a full name`() {
        assertThat("Ada Lovelace".initials()).isEqualTo("AL")
    }

    @Test
    fun `initials returns placeholder for blank input`() {
        assertThat("   ".initials(placeholder = "#")).isEqualTo("#")
    }

    @Test
    fun `orFallback uses fallback for blank input`() {
        assertThat("   ".orFallback("Unknown")).isEqualTo("Unknown")
        assertThat("Bob".orFallback("Unknown")).isEqualTo("Bob")
    }
}
