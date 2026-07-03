// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import androidx.compose.runtime.Immutable
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.PhoneNumber

/**
 * The single immutable UI state for the Contact Detail screen (CONVENTIONS.md §5;
 * BUILD_SPEC §8 — numbers, email-style metadata, photo, recent interactions,
 * set-default-number, favorite toggle, speed-dial assignment).
 *
 * @property isLoading true until the contact has been resolved at least once.
 * @property contact the resolved contact, or null when not found / still loading.
 * @property isFavorite whether this contact is currently a favorite.
 * @property defaultNumber the number marked default for dialing/favoriting, if any.
 * @property recentInteractions the recent call history for this contact (newest first).
 * @property assignedSpeedDialSlots slot → number map for any of this contact's numbers
 *   assigned to a dialpad speed-dial key (2..9), surfaced so the UI shows them.
 * @property speedDialSheet the number for which the slot-picker sheet is open, or null.
 * @property occupiedSlots the speed-dial slots currently in use app-wide (to disable
 *   already-taken keys in the picker).
 * @property errorMessage a transient human-readable error to surface, or null.
 */
@Immutable
data class ContactDetailUiState(
    val isLoading: Boolean = true,
    val contact: Contact? = null,
    val isFavorite: Boolean = false,
    val defaultNumber: String? = null,
    val recentInteractions: List<RecentInteraction> = emptyList(),
    val videoCallAvailable: Boolean = false,
    val assignedSpeedDialSlots: Map<Int, String> = emptyMap(),
    val speedDialSheet: SpeedDialSheetTarget? = null,
    val occupiedSlots: Set<Int> = emptySet(),
    val errorMessage: String? = null,
) {
    /** True once loading finished and no contact was found (deleted/removed). */
    val isMissing: Boolean get() = !isLoading && contact == null

    /** The number used for favoriting / quick-dial: the explicit default, else primary. */
    val effectiveDefaultNumber: String?
        get() = defaultNumber ?: contact?.primaryNumber?.dialValue
}

/**
 * One row of the "recent interactions" timeline (BUILD_SPEC §8). Pre-formatted for
 * rendering so the screen stays dumb.
 *
 * @property entryId stable call-log id.
 * @property number the formatted counterpart number for this interaction.
 * @property type the call type (drives the icon/tint, mirrors the call-log feature).
 * @property relativeTime a "5m ago" / "Yesterday" label.
 * @property durationSeconds duration (0 for missed/rejected).
 * @property isVoip whether the interaction was an in-app VoIP call (§11 honesty).
 */
@Immutable
data class RecentInteraction(
    val entryId: Long,
    val number: String,
    val type: com.glyphdialer.core.domain.model.CallType,
    val relativeTime: String,
    val durationSeconds: Long,
    val isVoip: Boolean,
) {
    companion object {
        /** Build a [RecentInteraction] from a domain [CallLogEntry] + a relative-time label. */
        fun from(entry: CallLogEntry, relativeTime: String): RecentInteraction = RecentInteraction(
            entryId = entry.id,
            number = entry.number.formatted,
            type = entry.type,
            relativeTime = relativeTime,
            durationSeconds = entry.durationSeconds,
            isVoip = entry.isVoip,
        )
    }
}

/** The number a speed-dial slot-picker sheet was opened for. */
@Immutable
data class SpeedDialSheetTarget(
    val number: PhoneNumber,
)

/**
 * User intents flowing UP from the Detail screen into [ContactDetailViewModel.onEvent]
 * (CONVENTIONS.md §5).
 */
sealed interface ContactDetailEvent {
    /** Tap a number to call it. */
    data class CallNumber(val number: String) : ContactDetailEvent

    /** Tap the video affordance for an in-app WebRTC call. */
    data class VideoCallNumber(val number: String) : ContactDetailEvent

    /** Tap the message affordance for a number. */
    data class MessageNumber(val number: String) : ContactDetailEvent

    /** Toggle this contact's favorite status (uses [ContactDetailUiState.effectiveDefaultNumber]). */
    data object ToggleFavorite : ContactDetailEvent

    /** Mark [number] as the default number for this contact (and favorite, if pinned). */
    data class SetDefaultNumber(val number: String) : ContactDetailEvent

    /** Open the speed-dial slot picker for [number]. */
    data class OpenSpeedDial(val number: PhoneNumber) : ContactDetailEvent

    /** Assign the open sheet's number to dialpad speed-dial [slot] (2..9). */
    data class AssignSpeedDial(val slot: Int) : ContactDetailEvent

    /** Clear a speed-dial [slot] currently mapped to one of this contact's numbers. */
    data class ClearSpeedDial(val slot: Int) : ContactDetailEvent

    /** Dismiss the speed-dial picker sheet. */
    data object DismissSpeedDial : ContactDetailEvent

    /** Navigate to the edit screen for this contact. */
    data object Edit : ContactDetailEvent

    /** Delete this contact (with host-side confirmation). */
    data object Delete : ContactDetailEvent

    /** Dismiss the currently-shown error message. */
    data object DismissError : ContactDetailEvent
}

/** One-shot side effects the Detail ViewModel asks the host to perform. */
sealed interface ContactDetailEffect {
    /** Place a call to [number] (delegated up to :app). */
    data class PlaceCall(val number: String) : ContactDetailEffect

    /** Place an in-app WebRTC video call to [number] (delegated up to :app). */
    data class PlaceVideoCall(val number: String) : ContactDetailEffect

    /** Open the SMS composer for [number]. */
    data class ComposeMessage(val number: String) : ContactDetailEffect

    /** Navigate to the edit destination for [lookupKey]. */
    data class NavigateToEdit(val lookupKey: String) : ContactDetailEffect

    /** Pop back after a successful delete. */
    data object NavigateUpAfterDelete : ContactDetailEffect

    /** Show a transient confirmation message. */
    data class ShowMessage(val message: String) : ContactDetailEffect
}
