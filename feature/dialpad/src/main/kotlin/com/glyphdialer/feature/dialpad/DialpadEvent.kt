// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad

import com.glyphdialer.core.domain.usecase.T9Match

/**
 * User intents the dialpad UI sends up to [DialpadViewModel] (CONVENTIONS.md §5 —
 * MVVM, "receive user intent through fun onEvent(event)"). The View is otherwise
 * stateless: it renders [DialpadUiState] and emits these.
 */
sealed interface DialpadEvent {

    /** A normal tap on key [char] (`0`–`9`, `*`, `#`). Appends to the entered digits. */
    data class KeyPress(val char: Char) : DialpadEvent

    /**
     * A long-press on key [char]. Shortcuts (BUILD_SPEC §8): `0`→`+`, `1`→voicemail,
     * `2`–`9`→speed-dial. Long-press on backspace clears all (sent as [ClearAll]).
     */
    data class LongPress(val char: Char) : DialpadEvent

    /** Delete the last entered character (no-op when empty). */
    data object Backspace : DialpadEvent

    /** Clear the whole entered number (also fired by long-pressing backspace). */
    data object ClearAll : DialpadEvent

    /** Paste [text] from the clipboard, keeping only dialable characters. */
    data class Paste(val text: String) : DialpadEvent

    /** Place a call to the currently entered number (the prominent call button). */
    data object Call : DialpadEvent

    /** Place a call to a specific [number] (e.g. a tapped T9 result). */
    data class CallNumber(val number: String) : DialpadEvent

    /** Fill the input from a tapped T9 [match] (does not dial). */
    data class FillFromMatch(val match: T9Match) : DialpadEvent

    /** Open "add to contacts" with the entered number prefilled. */
    data object AddContact : DialpadEvent
}
