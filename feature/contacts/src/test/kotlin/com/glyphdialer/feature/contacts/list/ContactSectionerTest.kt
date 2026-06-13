// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.list

import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure A–Z (+ "#") sectioning that backs the fast-scroll index
 * (CONVENTIONS.md §11 — pure logic gets JUnit5 + Truth). No Android dependency.
 */
class ContactSectionerTest {

    private fun contact(name: String): Contact = Contact(
        id = name.hashCode().toLong(),
        lookupKey = "lk_$name",
        displayName = name,
        numbers = listOf(PhoneNumber(raw = "+100000")),
    )

    @Test
    fun `empty input yields no sections`() {
        assertThat(ContactSectioner.section(emptyList())).isEmpty()
    }

    @Test
    fun `groups by uppercase first letter and sorts within bucket`() {
        val sections = ContactSectioner.section(
            listOf(contact("alan"), contact("Ada"), contact("Grace")),
        )

        assertThat(sections.map { it.letter }).containsExactly('A', 'G').inOrder()
        val aBucket = sections.first { it.letter == 'A' }.contacts.map { it.displayName }
        // Case-insensitive sort: "Ada" before "alan".
        assertThat(aBucket).containsExactly("Ada", "alan").inOrder()
    }

    @Test
    fun `non-letter names fall into the hash bucket, ordered last`() {
        val sections = ContactSectioner.section(
            listOf(contact("8-Ball"), contact("Zoe"), contact("+Service")),
        )

        assertThat(sections.map { it.letter }).containsExactly('Z', '#').inOrder()
        assertThat(sections.last().contacts).hasSize(2)
    }

    @Test
    fun `present letters reflect only non-empty buckets`() {
        val sections = ContactSectioner.section(listOf(contact("Bob"), contact("Bea"), contact("Mona")))
        assertThat(ContactSectioner.presentLetters(sections)).containsExactly('B', 'M').inOrder()
    }

    @Test
    fun `canonical alphabet covers A through Z then hash`() {
        assertThat(ContactSectioner.ALPHABET).hasSize(27)
        assertThat(ContactSectioner.ALPHABET.first()).isEqualTo('A')
        assertThat(ContactSectioner.ALPHABET.last()).isEqualTo('#')
    }
}
