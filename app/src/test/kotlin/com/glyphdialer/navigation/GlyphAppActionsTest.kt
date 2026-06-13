// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-Kotlin smoke test for the host action holder (CONVENTIONS.md §11). Verifies the
 * action lambdas are wired through to the right callbacks — a regression guard for the
 * cross-feature navigation contract assembled in :app.
 */
class GlyphAppActionsTest {

    @Test
    fun `actions forward their arguments to the supplied lambdas`() {
        val dialed = mutableListOf<String>()
        val messaged = mutableListOf<String>()
        val added = mutableListOf<String>()
        val details = mutableListOf<String>()
        var storageOpened = 0
        var licensesOpened = 0

        val actions = GlyphAppActions(
            dial = { dialed += it },
            message = { messaged += it },
            addContact = { added += it },
            openNumberDetails = { details += it },
            openStorageExport = { storageOpened++ },
            openOpenSourceLicenses = { licensesOpened++ },
        )

        actions.dial("+15551234567")
        actions.message("+15557654321")
        actions.addContact("100")
        actions.openNumberDetails("200")
        actions.openStorageExport()
        actions.openOpenSourceLicenses()

        assertThat(dialed).containsExactly("+15551234567")
        assertThat(messaged).containsExactly("+15557654321")
        assertThat(added).containsExactly("100")
        assertThat(details).containsExactly("200")
        assertThat(storageOpened).isEqualTo(1)
        assertThat(licensesOpened).isEqualTo(1)
    }
}
