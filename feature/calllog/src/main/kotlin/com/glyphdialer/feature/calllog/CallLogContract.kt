// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import androidx.compose.runtime.Immutable
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CallType

/**
 * The single immutable UI state for the Recents / Call-log screen (CONVENTIONS.md
 * §5 — ViewModels expose one immutable [CallLogUiState] via `StateFlow`).
 *
 * The state is intentionally pre-shaped for rendering: [groups] is the already
 * grouped-and-formatted list the screen draws directly, so the composable stays
 * dumb. The raw repository entries never reach the UI.
 *
 * @property isLoading true during the very first load before any data arrives.
 * @property groups the grouped, display-ready rows (newest first).
 * @property filter which entries are shown (all vs missed-only, BUILD_SPEC §8).
 * @property searchQuery the live search field text; blank means "not searching".
 * @property isSearchActive whether the search field is expanded/focused.
 * @property selectionMode whether the user is in bulk-select mode (BUILD_SPEC §8).
 * @property selectedIds ids currently selected for bulk delete.
 * @property quickActions the long-pressed row's radial menu target, or null.
 * @property errorMessage a transient human-readable error to surface, or null.
 * @property missedBadgeCount count of unread missed-like calls (for the filter chip).
 */
@Immutable
data class CallLogUiState(
    val isLoading: Boolean = true,
    val groups: List<CallLogGroup> = emptyList(),
    val filter: CallLogFilter = CallLogFilter.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val quickActions: QuickActionsTarget? = null,
    val errorMessage: String? = null,
    val missedBadgeCount: Int = 0,
) {
    /** True when there is nothing to show after loading (drives the empty state). */
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()

    /** Number of currently selected rows (for the contextual app bar title). */
    val selectedCount: Int get() = selectedIds.size
}

/** Top-level filter for the call log (BUILD_SPEC §8 missed-only filter). */
enum class CallLogFilter { ALL, MISSED }

/**
 * One display row: a contact (or bare number) plus the run of consecutive calls
 * that were collapsed into it. Grouping mirrors Google Dialer — successive calls
 * with the same counterpart and compatible direction merge, carrying a count.
 *
 * @property id stable id of the most-recent entry in the group (selection key).
 * @property entryIds every platform-log id collapsed into this group (for delete).
 * @property displayName resolved contact name, or null when only a number is known.
 * @property number the raw/best number for the counterpart.
 * @property formattedNumber region-formatted number for display (libphonenumber).
 * @property photoUri contact photo URI, or null → falls back to [DotMatrixAvatar].
 * @property dominantType the call-type icon to show (the latest call's type).
 * @property timestampMillis the most-recent call's timestamp.
 * @property relativeTime pre-computed "5m ago" / "Yesterday" style label.
 * @property count number of collapsed calls (shown as "(3)" when > 1).
 * @property durationSeconds duration of the most-recent call (0 for missed).
 * @property isVoip whether the latest call was an in-app VoIP call.
 * @property isVideo whether the latest call was video.
 * @property spamLabel caller-ID / spam label resolved at call time, if any.
 */
@Immutable
data class CallLogGroup(
    val id: Long,
    val entryIds: List<Long>,
    val displayName: String?,
    val number: String,
    val formattedNumber: String,
    val photoUri: String?,
    val dominantType: CallType,
    val timestampMillis: Long,
    val relativeTime: String,
    val count: Int,
    val durationSeconds: Long,
    val isVoip: Boolean,
    val isVideo: Boolean,
    val spamLabel: String?,
) {
    /** Title shown in the row: name if known, else the formatted number. */
    val title: String get() = displayName ?: formattedNumber

    /** Deterministic seed for the dot-matrix avatar fallback. */
    val avatarSeed: String get() = displayName ?: number

    /** True when the dominant call counts toward the missed badge/filter. */
    val isMissed: Boolean get() = dominantType.isMissedLike
}

/** The row a long-press opened the radial/strip quick-actions menu for. */
@Immutable
data class QuickActionsTarget(
    val groupId: Long,
    val displayName: String?,
    val number: String,
)

/**
 * The discrete quick actions on the Nothing-style radial menu (BUILD_SPEC §18):
 * call, message, record-the-next-call, copy number, block.
 */
enum class CallLogQuickAction { CALL, MESSAGE, RECORD_NEXT, COPY, BLOCK }

/**
 * User intents flowing UP from the screen into [CallLogViewModel.onEvent]
 * (CONVENTIONS.md §5 — UI emits events up). Kept exhaustive so the reducer is too.
 */
sealed interface CallLogEvent {
    /** Tap a row to call the counterpart back (BUILD_SPEC §8). */
    data class CallBack(val group: CallLogGroup) : CallLogEvent

    /** Open the per-number detail/history view (swipe or tap-through). */
    data class OpenDetails(val group: CallLogGroup) : CallLogEvent

    /** Swipe a row toward "message" → compose an SMS. */
    data class Message(val group: CallLogGroup) : CallLogEvent

    /** Switch the all/missed filter. */
    data class SetFilter(val filter: CallLogFilter) : CallLogEvent

    /** Update the live search query. */
    data class Search(val query: String) : CallLogEvent

    /** Expand/collapse the search field. */
    data class SetSearchActive(val active: Boolean) : CallLogEvent

    /** Long-press a row to open the radial quick-actions menu. */
    data class OpenQuickActions(val group: CallLogGroup) : CallLogEvent

    /** Dismiss the radial quick-actions menu. */
    data object DismissQuickActions : CallLogEvent

    /** Pick a quick action from the radial menu. */
    data class QuickAction(
        val action: CallLogQuickAction,
        val target: QuickActionsTarget,
    ) : CallLogEvent

    /** Enter bulk-selection mode, optionally pre-selecting a row. */
    data class EnterSelection(val initialId: Long?) : CallLogEvent

    /** Toggle a row's membership in the current selection. */
    data class ToggleSelected(val group: CallLogGroup) : CallLogEvent

    /** Leave selection mode, clearing the selection. */
    data object ExitSelection : CallLogEvent

    /** Delete the currently-selected rows (bulk delete, BUILD_SPEC §8). */
    data object DeleteSelected : CallLogEvent

    /** Mark all missed calls as read (clears the badge). */
    data object MarkAllRead : CallLogEvent

    /** Dismiss the currently-shown error message. */
    data object DismissError : CallLogEvent
}

/**
 * One-shot side effects the ViewModel asks the host to perform (CONVENTIONS.md §5
 * — navigation/toasts via a `Channel`/`SharedFlow`). The screen consumes these
 * and delegates to platform intents / navigation it owns.
 */
sealed interface CallLogEffect {
    /** Place a call to [number] (host issues TelecomManager.placeCall via :app). */
    data class PlaceCall(val number: String) : CallLogEffect

    /** Open the SMS composer for [number]. */
    data class ComposeMessage(val number: String) : CallLogEffect

    /** Navigate to the per-number history/detail destination. */
    data class NavigateToDetails(val number: String) : CallLogEffect

    /** Copy [number] to the clipboard. */
    data class CopyToClipboard(val number: String) : CallLogEffect

    /** Show a transient confirmation message (e.g. "Number blocked"). */
    data class ShowMessage(val message: String) : CallLogEffect
}
