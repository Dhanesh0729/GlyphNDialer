// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

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
 * Representative Compose UI test for [GlyphKey] (CONVENTIONS.md §11 — "a couple of
 * representative Compose UI tests"). Verifies the key is described for a11y and that
 * a tap fires both [onPress] and the Glyph stroke callback with the correct digit.
 */
class GlyphKeyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tap_firesOnPress_andDigitStroke_withDigit() {
        var pressed: Char? = null
        var stroked: Char? = null

        composeRule.setContent {
            GlyphTheme(themeMode = ThemeMode.DARK) {
                GlyphKey(
                    digit = '5',
                    letters = "JKL",
                    onPress = { pressed = it },
                    onDigitStroke = { stroked = it },
                )
            }
        }

        // Content description includes the digit and its letters.
        composeRule.onNodeWithContentDescription("5, JKL").performClick()

        composeRule.runOnIdle {
            assertEquals('5', pressed)
            assertEquals('5', stroked)
        }
    }

    @Test
    fun key_isExposedForAccessibility() {
        composeRule.setContent {
            GlyphTheme(themeMode = ThemeMode.DARK) {
                GlyphKey(digit = '0', letters = "+", onPress = {})
            }
        }
        composeRule.onNodeWithContentDescription("0, +").assertExists()
        assertTrue(true)
    }
}
