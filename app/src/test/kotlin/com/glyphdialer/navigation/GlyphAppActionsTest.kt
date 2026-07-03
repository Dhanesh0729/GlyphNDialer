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
        val videoDialed = mutableListOf<String>()
        val messaged = mutableListOf<String>()
        val added = mutableListOf<String>()
        val details = mutableListOf<String>()

        val actions = GlyphAppActions(
            dial = { dialed += it },
            videoDial = { videoDialed += it },
            message = { messaged += it },
            addContact = { added += it },
            openNumberDetails = { details += it },
        )

        actions.dial("+15551234567")
        actions.videoDial("+15550001111")
        actions.message("+15557654321")
        actions.addContact("100")
        actions.openNumberDetails("200")

        assertThat(dialed).containsExactly("+15551234567")
        assertThat(videoDialed).containsExactly("+15550001111")
        assertThat(messaged).containsExactly("+15557654321")
        assertThat(added).containsExactly("100")
        assertThat(details).containsExactly("200")
    }
}
