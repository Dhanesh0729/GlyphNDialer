// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.list

import com.glyphdialer.core.domain.model.Contact

/**
 * Pure, allocation-bounded sectioning of a flat contact list into the A–Z (+ "#")
 * buckets the fast-scroll index renders (BUILD_SPEC §8 — "fast-scroll alphabet
 * index"). Kept side-effect-free so it can run off the main thread and be unit-tested
 * with no Android dependency.
 *
 * Letters always cover the full Latin alphabet ('A'..'Z'); the trailing '#' bucket
 * collects names that don't start with a letter (digits, symbols, scripts the
 * uppercase mapping leaves non-Latin). Empty buckets are dropped from the result so
 * the list never renders a header with nothing under it — the UI dims the missing
 * letters on the scroll rail using [ALPHABET] as the canonical full rail.
 */
internal object ContactSectioner {

    /** The canonical full fast-scroll rail (A–Z then '#'). */
    val ALPHABET: List<Char> = ('A'..'Z').toList() + '#'

    /**
     * Bucket [contacts] by [Contact.sortIndexChar], sort within each bucket by
     * display name (case-insensitive), and return only non-empty sections ordered
     * A–Z then '#'.
     */
    fun section(contacts: List<Contact>): List<ContactSection> {
        if (contacts.isEmpty()) return emptyList()

        val grouped = contacts.groupBy { it.sortIndexChar }
        return ALPHABET.mapNotNull { letter ->
            val bucket = grouped[letter] ?: return@mapNotNull null
            if (bucket.isEmpty()) {
                null
            } else {
                ContactSection(
                    letter = letter,
                    contacts = bucket.sortedBy { it.displayName.trim().lowercase() },
                )
            }
        }
    }

    /** The ordered set of letters that actually have contacts (for the scroll rail). */
    fun presentLetters(sections: List<ContactSection>): List<Char> =
        sections.map { it.letter }
}
