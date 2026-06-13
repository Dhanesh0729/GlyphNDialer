// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad

/**
 * One-shot side effects the dialpad emits to its host (CONVENTIONS.md §5 — "One-shot
 * effects (navigation, toasts) via a Channel/SharedFlow"). Delivered through a
 * [kotlinx.coroutines.channels.Channel] so they fire exactly once and survive
 * configuration changes without re-triggering.
 */
sealed interface DialpadEffect {

    /** Launch the platform "insert/add contact" intent prefilled with [number]. */
    data class AddToContacts(val number: String) : DialpadEffect

    /**
     * Prompt the host to assign speed-dial [slot] (2..9) because a long-press hit an
     * unassigned key. The host opens a contact picker and calls back via the VM.
     */
    data class AssignSpeedDial(val slot: Int) : DialpadEffect

    /** A user-presentable, transient message (e.g. "Couldn't place the call"). */
    data class ShowMessage(val message: String) : DialpadEffect

    /**
     * The call was placed successfully (the system in-call UI takes over). The host
     * may clear/reset the dialpad or navigate as it sees fit.
     */
    data object CallPlaced : DialpadEffect
}
