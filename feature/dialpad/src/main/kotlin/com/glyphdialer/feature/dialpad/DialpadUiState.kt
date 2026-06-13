// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad

import com.glyphdialer.core.domain.usecase.T9Match

/**
 * The single immutable UI state for the dialpad (CONVENTIONS.md §5 — "a single
 * immutable data class XxxUiState via StateFlow"). Everything the stateless
 * [DialpadScreen] needs to render derives from here.
 *
 * @property entered the raw entered characters (digits plus `+ * #`), in input order.
 * @property formattedNumber the as-you-type, region-aware rendering of [entered]
 *   (BUILD_SPEC §8). Falls back to [entered] when it can't be parsed.
 * @property t9Results ranked T9 smart-search matches over contacts + call log (§8).
 * @property capabilities the honest capability flags (§9) the UI consults before
 *   offering Glyph mirroring / dialer-gated actions.
 * @property glyphMirrorEnabled whether the on-screen mirrored dot-stroke animation
 *   should play per key press (master Glyph toggle AND dialpad-strokes toggle, §17.4).
 * @property lastStroke the most recent key whose stroke should be mirrored on screen,
 *   tagged with a monotonically increasing [StrokeTrigger.id] so identical consecutive
 *   digits still re-trigger the animation.
 */
data class DialpadUiState(
    val entered: String = "",
    val formattedNumber: String = "",
    val t9Results: List<T9Match> = emptyList(),
    val capabilities: DialpadCapabilities = DialpadCapabilities(),
    val glyphMirrorEnabled: Boolean = false,
    val lastStroke: StrokeTrigger? = null,
) {
    /** True when there is at least one entered character to dial/edit. */
    val hasInput: Boolean get() = entered.isNotEmpty()

    /** Whether the prominent call button should be enabled. */
    val canCall: Boolean get() = entered.any { it.isDigit() }

    /** Whether to show the T9 results list instead of the (optional) empty hint. */
    val showResults: Boolean get() = t9Results.isNotEmpty()
}

/**
 * A per-key stroke request for the on-screen mirror (§17.4). [id] disambiguates
 * repeats of the same [digit] so the Compose animation restarts each press.
 */
data class StrokeTrigger(val digit: Char, val id: Long)

/**
 * The slice of [com.glyphdialer.core.domain.model.CapabilityFlags] the dialpad
 * actually consults, projected into a tiny UI-facing shape (keeps the View free of
 * the full domain flags object). Honesty principle (§9): the on-screen mirror is
 * always shown; the *physical* Glyph stroke only fires when [glyphAvailable].
 */
data class DialpadCapabilities(
    /** Real Nothing Glyph hardware is present and the session is live (§17). */
    val glyphAvailable: Boolean = false,
    /** The app is the default dialer (affects which affordances are honest to show). */
    val isDefaultDialer: Boolean = false,
)
