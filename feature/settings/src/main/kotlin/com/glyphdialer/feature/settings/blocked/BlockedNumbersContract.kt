// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.blocked

import com.glyphdialer.core.domain.model.BlockedNumber

/**
 * MVVM contract for the blocked-numbers sub-screen (BUILD_SPEC §8/§21).
 *
 * Backed by [com.glyphdialer.core.domain.repository.BlockedNumberRepository], which
 * itself mirrors `BlockedNumberContract` when the app is the default dialer. When the
 * app is NOT the default dialer the platform restricts blocklist writes; the UI shows
 * that state honestly via [BlockedNumbersUiState.isDefaultDialer] (§9).
 */
data class BlockedNumbersUiState(
    val isLoading: Boolean = true,
    val blocked: List<BlockedNumber> = emptyList(),
    /** Whether the app currently holds the default-dialer role (gates blocklist writes). */
    val isDefaultDialer: Boolean = true,
    /** The draft number in the "add" field. */
    val draftNumber: String = "",
    /** Whether the "add" submission is in flight. */
    val isAdding: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = blocked.isEmpty()

    /** A normalized, non-blank draft can be submitted. */
    val canAdd: Boolean get() = draftNumber.isNotBlank() && !isAdding
}

/** User intents from the blocked-numbers screen. */
sealed interface BlockedNumbersEvent {
    data class DraftChanged(val value: String) : BlockedNumbersEvent

    /** Block the current draft; [reportAsSpam] for "block & report". */
    data class Add(val reportAsSpam: Boolean = false) : BlockedNumbersEvent

    data class Unblock(val number: String) : BlockedNumbersEvent

    data object DismissError : BlockedNumbersEvent
}
