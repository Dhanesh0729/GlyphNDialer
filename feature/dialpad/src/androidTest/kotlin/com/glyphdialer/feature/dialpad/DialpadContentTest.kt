// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Representative Compose UI test for key entry on the stateless [DialpadContent]
 * (CONVENTIONS.md §11 / BUILD_SPEC §23 — "Compose tests for dialpad"). It drives the
 * shared [com.glyphdialer.core.ui.component.GlyphKey] cells and asserts the right
 * [DialpadEvent]s are emitted, without needing Hilt or a real ViewModel.
 */
class DialpadContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingKey_emitsKeyPress_withDigit() {
        val events = mutableListOf<DialpadEvent>()

        composeRule.setContent {
            GlyphTheme(themeMode = ThemeMode.DARK) {
                DialpadContent(
                    state = DialpadUiState(),
                    onEvent = { events += it },
                    onPasteRequested = {},
                )
            }
        }

        // GlyphKey exposes a content description of "digit, letters" (e.g. "5, JKL").
        composeRule.onNodeWithContentDescription("5, JKL").performClick()

        composeRule.runOnIdle {
            assertTrue("expected a KeyPress event", events.any { it is DialpadEvent.KeyPress })
            assertEquals('5', (events.first { it is DialpadEvent.KeyPress } as DialpadEvent.KeyPress).char)
        }
    }

    @Test
    fun callButton_emitsCall_whenTapped() {
        val events = mutableListOf<DialpadEvent>()

        composeRule.setContent {
            GlyphTheme(themeMode = ThemeMode.DARK) {
                DialpadContent(
                    state = DialpadUiState(entered = "415", formattedNumber = "415"),
                    onEvent = { events += it },
                    onPasteRequested = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Call").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertTrue("expected a Call event", events.contains(DialpadEvent.Call))
        }
    }

    @Test
    fun enteredNumber_isShownAndDescribed() {
        composeRule.setContent {
            GlyphTheme(themeMode = ThemeMode.DARK) {
                DialpadContent(
                    state = DialpadUiState(entered = "4155550142", formattedNumber = "(415) 555-0142"),
                    onEvent = {},
                    onPasteRequested = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Entered number: 4155550142").assertExists()
    }
}
