// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.domain.model.Contact
import javax.inject.Inject

/**
 * A contact matched by a T9 query, carrying the match metadata used for ranking
 * and (optionally) UI highlighting.
 */
data class T9Match(
    val contact: Contact,
    /** Which number on the contact matched (its raw form), or null for a name-only match. */
    val matchedNumber: String?,
    val kind: T9MatchKind,
    /** Lower is better; computed from [kind] + match position. */
    val rank: Int,
)

/** How a contact matched the T9 digits, ordered best-to-worst by ordinal. */
enum class T9MatchKind {
    /** The digits map to the start of the full name (e.g. "43" → "GE" → "George"). */
    NAME_PREFIX,

    /** The digits map to the initials of name words (e.g. "526" → "J A K" → "Jane A. King"). */
    NAME_INITIALS,

    /** The digits map to the start of a non-first name word (e.g. matches "Smith" in "Jane Smith"). */
    NAME_WORD_PREFIX,

    /** The digits map to a substring somewhere inside the name. */
    NAME_SUBSTRING,

    /** The digits are a prefix of one of the contact's phone numbers. */
    NUMBER_PREFIX,

    /** The digits appear as a substring inside a phone number. */
    NUMBER_SUBSTRING,
}

/**
 * REAL T9 smart-search over contacts (§8). Pure, deterministic, and fully
 * unit-testable — no Android, no IO, no coroutines. The data layer feeds it a
 * contact snapshot; the dialpad feature ranks the result.
 *
 * Matching strategy for a digit string like "5283":
 *  - Map each name letter to its T9 digit ('a'/'b'/'c' → '2', etc.). Then test, in
 *    priority order: full-name prefix, word initials, any-word prefix, name
 *    substring.
 *  - Independently, match the digits against each phone number (prefix, then
 *    substring) after stripping formatting.
 *  - Keep the single best (lowest-rank) match per contact and return results sorted
 *    by rank, then name.
 */
class T9SearchUseCase @Inject constructor() {

    /**
     * @param query the raw dialpad input. Non-digit characters (e.g. as-you-type
     *   formatting "+", spaces, "(" ) are ignored.
     * @param contacts the candidate set to search.
     */
    operator fun invoke(query: String, contacts: List<Contact>): List<T9Match> {
        val digits = query.filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()

        val matches = ArrayList<T9Match>(contacts.size)
        for (contact in contacts) {
            bestMatch(digits, contact)?.let(matches::add)
        }
        return matches.sortedWith(
            compareBy({ it.rank }, { it.contact.displayName.lowercase() }, { it.contact.id }),
        )
    }

    /** The best (lowest-rank) match for one contact, or null if none. */
    private fun bestMatch(digits: String, contact: Contact): T9Match? {
        var best: T9Match? = null

        // ---- Name-based matching ----
        val name = contact.displayName
        val nameDigits = lettersToDigits(name)
        if (nameDigits.isNotEmpty()) {
            // 1) Full-name prefix (skipping leading separators that map to nothing).
            val compactNameDigits = lettersToDigits(name, keepSeparators = false)
            if (compactNameDigits.startsWith(digits)) {
                best = best.betterOf(T9Match(contact, null, T9MatchKind.NAME_PREFIX, rank(T9MatchKind.NAME_PREFIX, 0)))
            }

            // 2) Word initials (e.g. "Jane A. King" → initials "j a k" → "526").
            val initials = wordInitialsToDigits(name)
            if (initials.length >= digits.length && initials.startsWith(digits)) {
                best = best.betterOf(T9Match(contact, null, T9MatchKind.NAME_INITIALS, rank(T9MatchKind.NAME_INITIALS, 0)))
            }

            // 3) Any-word prefix (match the start of a later word, e.g. surname).
            val wordPrefixPos = wordPrefixPosition(name, digits)
            if (wordPrefixPos >= 0) {
                best = best.betterOf(
                    T9Match(contact, null, T9MatchKind.NAME_WORD_PREFIX, rank(T9MatchKind.NAME_WORD_PREFIX, wordPrefixPos)),
                )
            }

            // 4) Name substring anywhere.
            val subPos = compactNameDigits.indexOf(digits)
            if (subPos >= 0) {
                best = best.betterOf(
                    T9Match(contact, null, T9MatchKind.NAME_SUBSTRING, rank(T9MatchKind.NAME_SUBSTRING, subPos)),
                )
            }
        }

        // ---- Number-based matching ----
        for (phone in contact.numbers) {
            val numDigits = phone.raw.filter { it.isDigit() }
            if (numDigits.isEmpty()) continue
            when {
                numDigits.startsWith(digits) ->
                    best = best.betterOf(
                        T9Match(contact, phone.raw, T9MatchKind.NUMBER_PREFIX, rank(T9MatchKind.NUMBER_PREFIX, 0)),
                    )
                else -> {
                    val pos = numDigits.indexOf(digits)
                    if (pos >= 0) {
                        best = best.betterOf(
                            T9Match(contact, phone.raw, T9MatchKind.NUMBER_SUBSTRING, rank(T9MatchKind.NUMBER_SUBSTRING, pos)),
                        )
                    }
                }
            }
        }
        return best
    }

    /** Lower-rank (better) of the two matches; null-safe. */
    private fun T9Match?.betterOf(other: T9Match): T9Match =
        if (this == null || other.rank < this.rank) other else this

    /** Rank = kind weight (dominant) + small positional penalty. */
    private fun rank(kind: T9MatchKind, position: Int): Int =
        kind.ordinal * KIND_WEIGHT + position.coerceIn(0, KIND_WEIGHT - 1)

    /**
     * Find the earliest WORD (by character offset) whose own letters, mapped to
     * digits, start with [digits]. Returns that word's start offset, or -1.
     */
    private fun wordPrefixPosition(name: String, digits: String): Int {
        var i = 0
        val n = name.length
        while (i < n) {
            // Skip separators to the start of a word.
            while (i < n && !name[i].isLetterOrDigit()) i++
            if (i >= n) break
            val wordStart = i
            // The word body.
            while (i < n && name[i].isLetterOrDigit()) i++
            val wordDigits = lettersToDigits(name.substring(wordStart, i), keepSeparators = false)
            if (wordDigits.startsWith(digits)) return wordStart
        }
        return -1
    }

    companion object {
        /** Weight separating match kinds so kind always dominates position in [rank]. */
        const val KIND_WEIGHT = 1_000

        /**
         * The standard ITU T9 letter→digit keypad mapping. Index by the keypad digit.
         * 0 and 1 carry no letters on a phone keypad.
         */
        private val DIGIT_LETTERS = arrayOf(
            "",      // 0
            "",      // 1
            "abc",   // 2
            "def",   // 3
            "ghi",   // 4
            "jkl",   // 5
            "mno",   // 6
            "pqrs",  // 7
            "tuv",   // 8
            "wxyz",  // 9
        )

        /** Reverse lookup: lowercase letter → its keypad digit char. */
        private val LETTER_TO_DIGIT: Map<Char, Char> = buildMap {
            for (digit in DIGIT_LETTERS.indices) {
                for (letter in DIGIT_LETTERS[digit]) put(letter, '0' + digit)
            }
        }

        /**
         * Map a single character to its T9 digit. Returns null for characters that
         * aren't keypad letters (separators, punctuation, accented letters not folded).
         * Pure helper exposed for unit tests.
         */
        fun letterToDigit(c: Char): Char? = LETTER_TO_DIGIT[c.lowercaseChar()]

        /**
         * Map a whole string of name letters to T9 digits.
         *
         * @param keepSeparators when true, non-letter chars are kept as-is in the
         *   output (rarely needed); when false (default) they are dropped, producing a
         *   compact digit string suitable for prefix/substring tests.
         */
        fun lettersToDigits(text: String, keepSeparators: Boolean = false): String {
            val sb = StringBuilder(text.length)
            for (c in text) {
                val d = letterToDigit(c)
                when {
                    d != null -> sb.append(d)
                    keepSeparators -> sb.append(c)
                    // else: drop the separator
                }
            }
            return sb.toString()
        }

        /**
         * Map the INITIALS of each whitespace/punctuation-delimited word to digits.
         * E.g. "Jane A. King" → "jak" → "526". Pure helper exposed for unit tests.
         */
        fun wordInitialsToDigits(name: String): String {
            val sb = StringBuilder()
            for (word in name.split(WORD_DELIMITERS)) {
                val firstLetter = word.firstOrNull { it.isLetter() } ?: continue
                letterToDigit(firstLetter)?.let(sb::append)
            }
            return sb.toString()
        }

        private val WORD_DELIMITERS = Regex("[\\s.,'\\-_/]+")
    }
}
