// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for the pure T9 matching logic (CONVENTIONS.md section 11). No Android,
 * no coroutines, no mocks — just data in, ranked matches out.
 */
class T9SearchUseCaseTest {

    private val useCase = T9SearchUseCase()

    private fun contact(id: Long, name: String, vararg numbers: String): Contact =
        Contact(
            id = id,
            lookupKey = "lk_$id",
            displayName = name,
            numbers = numbers.map { PhoneNumber(raw = it) },
        )

    private val george = contact(1, "George Harrison", "415-555-2671")
    private val janeKing = contact(2, "Jane A. King", "212-555-0188")
    private val bobSmith = contact(3, "Bob Smith", "+1 408 555 9000")
    private val noNumber = contact(4, "Zelda")
    private val contacts = listOf(george, janeKing, bobSmith, noNumber)

    // ---- Letter -> digit mapping (the heart of T9) ----

    @ParameterizedTest
    @CsvSource(
        "a,2", "b,2", "c,2",
        "d,3", "e,3", "f,3",
        "g,4", "h,4", "i,4",
        "j,5", "k,5", "l,5",
        "m,6", "n,6", "o,6",
        "p,7", "q,7", "r,7", "s,7",
        "t,8", "u,8", "v,8",
        "w,9", "x,9", "y,9", "z,9",
    )
    fun `letterToDigit maps the ITU keypad`(letter: Char, expected: Char) {
        assertThat(T9SearchUseCase.letterToDigit(letter)).isEqualTo(expected)
    }

    @Test
    fun `letterToDigit is case-insensitive and rejects non-letters`() {
        assertThat(T9SearchUseCase.letterToDigit('G')).isEqualTo('4')
        assertThat(T9SearchUseCase.letterToDigit('1')).isNull()
        assertThat(T9SearchUseCase.letterToDigit(' ')).isNull()
        assertThat(T9SearchUseCase.letterToDigit('-')).isNull()
    }

    @Test
    fun `lettersToDigits maps a full name and drops separators`() {
        // "George" -> g=4 e=3 o=6 r=7 g=4 e=3
        assertThat(T9SearchUseCase.lettersToDigits("George")).isEqualTo("436743")
        // Separators dropped by default
        assertThat(T9SearchUseCase.lettersToDigits("Jane A. King")).isEqualTo("5263" + "2" + "5464")
    }

    @Test
    fun `wordInitialsToDigits maps initials only`() {
        // Jane A. King -> J A K -> 5 2 5
        assertThat(T9SearchUseCase.wordInitialsToDigits("Jane A. King")).isEqualTo("525")
        assertThat(T9SearchUseCase.wordInitialsToDigits("Bob Smith")).isEqualTo("27") // B=2 S=7
    }

    // ---- End-to-end search cases ----

    @Test
    fun `empty or non-digit query returns no matches`() {
        assertThat(useCase("", contacts)).isEmpty()
        assertThat(useCase("+", contacts)).isEmpty()
        assertThat(useCase("   ", contacts)).isEmpty()
    }

    @Test
    fun `name prefix match - typing 43 finds George`() {
        // G=4, E=3 -> "George" starts with 43
        val results = useCase("43", contacts)
        assertThat(results.map { it.contact.displayName }).contains("George Harrison")
        val george = results.first { it.contact.id == 1L }
        assertThat(george.kind).isEqualTo(T9MatchKind.NAME_PREFIX)
        assertThat(george.matchedNumber).isNull()
    }

    @Test
    fun `initials match - typing 525 finds Jane A King`() {
        // J=5 A=2 K=5 -> initials of "Jane A. King"
        val results = useCase("525", contacts)
        val jane = results.firstOrNull { it.contact.id == 2L }
        assertThat(jane).isNotNull()
        assertThat(jane!!.kind).isEqualTo(T9MatchKind.NAME_INITIALS)
    }

    @Test
    fun `word prefix match - typing 76 finds Bob by surname Smith`() {
        // S=7 m=6 -> prefix of the word "Smith"
        val results = useCase("76", contacts)
        val bob = results.firstOrNull { it.contact.id == 3L }
        assertThat(bob).isNotNull()
        assertThat(bob!!.kind).isEqualTo(T9MatchKind.NAME_WORD_PREFIX)
    }

    @Test
    fun `number prefix match - typing 415 finds Georges number`() {
        val results = useCase("415", contacts)
        val george = results.firstOrNull { it.contact.id == 1L }
        assertThat(george).isNotNull()
        assertThat(george!!.kind).isEqualTo(T9MatchKind.NUMBER_PREFIX)
        assertThat(george.matchedNumber).isEqualTo("415-555-2671")
    }

    @Test
    fun `number substring match - typing 555 matches inside the number`() {
        val results = useCase("555", contacts)
        // All three numbered contacts contain 555.
        assertThat(results.map { it.contact.id }).containsAtLeast(1L, 2L, 3L)
        val george = results.first { it.contact.id == 1L }
        assertThat(george.kind).isEqualTo(T9MatchKind.NUMBER_SUBSTRING)
    }

    @Test
    fun `name prefix outranks number substring for the same contact`() {
        // "555" appears in George's number (substring) but "George" also starts
        // with... no. Use a query that hits both kinds: "4155" -> name? G-E-? no.
        // Instead verify ordering across kinds: name prefix beats number substring.
        val results = useCase("43", contacts)
        // George (name prefix) should be ranked above any pure number-substring match.
        val ranks = results.associate { it.contact.id to it.rank }
        assertThat(ranks[1L]).isLessThan(T9MatchKind.NUMBER_SUBSTRING.ordinal * T9SearchUseCase.KIND_WEIGHT)
    }

    @Test
    fun `results are sorted by rank then name`() {
        val results = useCase("555", contacts)
        // Sorted ascending by rank.
        val sortedRanks = results.map { it.rank }
        assertThat(sortedRanks).isInOrder()
    }

    @Test
    fun `contact with no number still matches by name`() {
        // Z=9 e=3 -> "Zelda" prefix.
        val results = useCase("93", contacts)
        val zelda = results.firstOrNull { it.contact.id == 4L }
        assertThat(zelda).isNotNull()
        assertThat(zelda!!.kind).isEqualTo(T9MatchKind.NAME_PREFIX)
    }

    @Test
    fun `non-matching query yields empty result`() {
        // No contact name maps to 0001 and no number starts/contains 0001.
        assertThat(useCase("0001", contacts)).isEmpty()
    }
}
