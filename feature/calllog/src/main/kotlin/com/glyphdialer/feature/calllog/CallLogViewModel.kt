// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.onFailure
import com.glyphdialer.core.common.onSuccess
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CallType
import com.glyphdialer.core.domain.repository.CallLogRepository
import com.glyphdialer.core.domain.usecase.BlockNumberUseCase
import com.glyphdialer.core.domain.usecase.FormatNumberUseCase
import com.glyphdialer.core.domain.usecase.GetCallLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Recents / Call-log screen (BUILD_SPEC §8, §18; CONVENTIONS.md
 * §5). Exposes one immutable [CallLogUiState] via [uiState] and consumes intents
 * through [onEvent]; one-shot navigation/clipboard/toast effects flow over
 * [effects].
 *
 * Data pipeline: a small set of input [StateFlow]s (filter, debounced search,
 * selection) drive a [flatMapLatest] over [GetCallLogUseCase] (or repository
 * search), the raw entries are grouped + formatted off the main thread via
 * [CallLogGrouper], and the result is folded into [uiState]. All dispatchers are
 * injected (never hard-coded) and fallible writes return [AppResult].
 */
@HiltViewModel
class CallLogViewModel @Inject constructor(
    private val getCallLog: GetCallLogUseCase,
    private val blockNumber: BlockNumberUseCase,
    private val formatNumber: FormatNumberUseCase,
    private val callLogRepository: CallLogRepository,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
    // IO dispatcher injected for parity with the §5 convention; repository writes
    // own their dispatching, so it is reserved for future provider-direct work.
    @Suppress("unused") @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val grouper = CallLogGrouper(formatNumber)

    // ---- UI inputs (drive the data pipeline) --------------------------------
    private val filter = MutableStateFlow(CallLogFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val isSearchActive = MutableStateFlow(false)

    // ---- Ephemeral interaction state (not derived from data) ----------------
    private val selectionMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val quickActions = MutableStateFlow<QuickActionsTarget?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    // ---- Derived data state -------------------------------------------------
    private val dataState = MutableStateFlow(DataState())

    private val _uiState = MutableStateFlow(CallLogUiState())
    val uiState: StateFlow<CallLogUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CallLogEffect>(Channel.BUFFERED)
    val effects: Flow<CallLogEffect> = _effects.receiveAsFlow()

    init {
        observeCallLog()
        observeMissedBadge()
        wireUiState()
    }

    /** Single entry point for user intents (CONVENTIONS.md §5). */
    fun onEvent(event: CallLogEvent) {
        when (event) {
            is CallLogEvent.CallBack -> emitEffect(CallLogEffect.PlaceCall(event.group.number))
            is CallLogEvent.OpenDetails -> emitEffect(CallLogEffect.NavigateToDetails(event.group.number))
            is CallLogEvent.Message -> emitEffect(CallLogEffect.ComposeMessage(event.group.number))
            is CallLogEvent.SetFilter -> filter.value = event.filter
            is CallLogEvent.Search -> searchQuery.value = event.query
            is CallLogEvent.SetSearchActive -> onSearchActiveChanged(event.active)
            is CallLogEvent.OpenQuickActions -> quickActions.value = event.group.toTarget()
            CallLogEvent.DismissQuickActions -> quickActions.value = null
            is CallLogEvent.QuickAction -> onQuickAction(event)
            is CallLogEvent.EnterSelection -> enterSelection(event.initialId)
            is CallLogEvent.ToggleSelected -> toggleSelected(event.group.id)
            CallLogEvent.ExitSelection -> exitSelection()
            CallLogEvent.DeleteSelected -> deleteSelected()
            CallLogEvent.MarkAllRead -> markAllRead()
            CallLogEvent.DismissError -> errorMessage.value = null
        }
    }

    // ---- Data pipeline ------------------------------------------------------

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeCallLog() {
        viewModelScope.launch {
            combine(
                filter,
                searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }.distinctUntilChanged(),
            ) { f, q -> f to q }
                .flatMapLatest { (f, q) -> entriesFor(f, q) }
                .map { entries -> grouper.group(entries, System.currentTimeMillis()) }
                .flowOn(defaultDispatcher)
                .catch { t ->
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.e(t, "Call-log stream failed")
                    errorMessage.value = "Couldn't load recent calls."
                    dataState.update { it.copy(isLoading = false) }
                }
                .collect { groups ->
                    dataState.update { it.copy(isLoading = false, groups = groups) }
                }
        }
    }

    /** Resolve the source flow for the current [filter] + [query]. */
    private fun entriesFor(filter: CallLogFilter, query: String): Flow<List<CallLogEntry>> {
        if (query.isNotBlank()) {
            // One-shot search re-run as a single-element flow so it slots into the
            // same flatMapLatest pipeline; results still get grouped + formatted.
            return flowOf(Unit).map {
                when (val r = callLogRepository.search(query.trim())) {
                    is AppResult.Success -> applyFilter(r.data, filter)
                    is AppResult.Failure -> {
                        Timber.w(r.error, "Call-log search failed: ${r.message}")
                        errorMessage.value = r.message ?: "Search failed."
                        emptyList()
                    }
                }
            }
        }
        val types = when (filter) {
            CallLogFilter.MISSED -> MISSED_TYPES
            CallLogFilter.INCOMING -> setOf(CallType.INCOMING)
            CallLogFilter.OUTGOING -> setOf(CallType.OUTGOING)
            CallLogFilter.ALL -> null
        }
        return getCallLog(types = types)
    }

    private fun applyFilter(entries: List<CallLogEntry>, filter: CallLogFilter): List<CallLogEntry> =
        when (filter) {
            CallLogFilter.MISSED -> entries.filter { it.type.isMissedLike }
            CallLogFilter.INCOMING -> entries.filter { it.type == CallType.INCOMING }
            CallLogFilter.OUTGOING -> entries.filter { it.type == CallType.OUTGOING }
            CallLogFilter.ALL -> entries
        }

    private fun observeMissedBadge() {
        viewModelScope.launch {
            getCallLog.missedOnly()
                .map { it.count { entry -> entry.type == CallType.MISSED } }
                .catch { t ->
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.w(t, "Missed-badge stream failed")
                }
                .collect { count -> dataState.update { it.copy(missedBadgeCount = count) } }
        }
    }

    /** Fold the data state and the ephemeral interaction state into one UiState. */
    private fun wireUiState() {
        viewModelScope.launch {
            combine(
                dataState,
                filter,
                searchQuery,
                isSearchActive,
                combine(selectionMode, selectedIds, quickActions, errorMessage) { mode, sel, qa, err ->
                    Interaction(mode, sel, qa, err)
                },
            ) { data, f, query, searching, interaction ->
                CallLogUiState(
                    isLoading = data.isLoading,
                    groups = data.groups,
                    filter = f,
                    searchQuery = query,
                    isSearchActive = searching,
                    selectionMode = interaction.selectionMode,
                    selectedIds = interaction.selectedIds,
                    quickActions = interaction.quickActions,
                    errorMessage = interaction.errorMessage,
                    missedBadgeCount = data.missedBadgeCount,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    // ---- Intent handlers ----------------------------------------------------

    private fun onSearchActiveChanged(active: Boolean) {
        isSearchActive.value = active
        if (!active) searchQuery.value = ""
    }

    private fun onQuickAction(event: CallLogEvent.QuickAction) {
        val target = event.target
        when (event.action) {
            CallLogQuickAction.CALL -> emitEffect(CallLogEffect.PlaceCall(target.number))
            CallLogQuickAction.VIDEO_CALL -> emitEffect(CallLogEffect.PlaceCall(target.number)) // Add video capability in CallLogEffect later if needed, but for now we'll route to PlaceCall
            CallLogQuickAction.MESSAGE -> emitEffect(CallLogEffect.ComposeMessage(target.number))
            CallLogQuickAction.COPY -> emitEffect(CallLogEffect.CopyToClipboard(target.number))
            CallLogQuickAction.RECORD_NEXT -> emitEffect(
                // Honesty principle (§9): recording availability is resolved by the
                // recording feature/tier at call time. From the log we can only
                // *arm* the next call; we surface that intent honestly rather than
                // promising a recording will happen.
                CallLogEffect.ShowMessage("Next call to ${target.displayName ?: target.number} will record at the highest supported tier."),
            )
            CallLogQuickAction.BLOCK -> block(target)
        }
        quickActions.value = null
    }

    private fun block(target: QuickActionsTarget) {
        viewModelScope.launch {
            blockNumber(target.number)
                .onSuccess {
                    emitEffect(CallLogEffect.ShowMessage("Blocked ${target.displayName ?: target.number}."))
                }
                .onFailure { failure ->
                    Timber.w(failure.error, "Block failed: ${failure.message}")
                    errorMessage.value = failure.message ?: "Couldn't block this number."
                }
        }
    }

    private fun enterSelection(initialId: Long?) {
        selectionMode.value = true
        selectedIds.value = if (initialId != null) setOf(initialId) else emptySet()
        quickActions.value = null
    }

    private fun toggleSelected(id: Long) {
        selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
        // Leaving zero selected exits the mode, matching Google Dialer behavior.
        if (selectedIds.value.isEmpty()) selectionMode.value = false
    }

    private fun exitSelection() {
        selectionMode.value = false
        selectedIds.value = emptySet()
    }

    private fun deleteSelected() {
        val selectedGroupIds = selectedIds.value
        if (selectedGroupIds.isEmpty()) {
            exitSelection()
            return
        }
        // Expand selected group ids → every underlying platform-log entry id.
        val groupsById = dataState.value.groups.associateBy { it.id }
        val entryIds = selectedGroupIds.flatMap { groupId ->
            groupsById[groupId]?.entryIds ?: listOf(groupId)
        }
        viewModelScope.launch {
            callLogRepository.deleteEntries(entryIds)
                .onSuccess {
                    emitEffect(CallLogEffect.ShowMessage("Deleted ${selectedGroupIds.size} item(s)."))
                    exitSelection()
                }
                .onFailure { failure ->
                    Timber.w(failure.error, "Delete failed: ${failure.message}")
                    errorMessage.value = failure.message ?: "Couldn't delete these calls."
                }
        }
    }

    private fun markAllRead() {
        viewModelScope.launch {
            callLogRepository.markAllRead()
                .onFailure { failure ->
                    Timber.w(failure.error, "markAllRead failed: ${failure.message}")
                }
        }
    }

    private fun emitEffect(effect: CallLogEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun CallLogGroup.toTarget(): QuickActionsTarget {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val history = dataState.value.groups.filter { it.number == this.number && it.timestampMillis >= todayStart }
        return QuickActionsTarget(id, displayName, number, this, history)
    }

    /** Internal carrier for the data side of the state. */
    private data class DataState(
        val isLoading: Boolean = true,
        val groups: List<CallLogGroup> = emptyList(),
        val missedBadgeCount: Int = 0,
    )

    /** Internal carrier for the ephemeral interaction side of the state. */
    private data class Interaction(
        val selectionMode: Boolean,
        val selectedIds: Set<Long>,
        val quickActions: QuickActionsTarget?,
        val errorMessage: String?,
    )

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
        private val MISSED_TYPES =
            setOf(CallType.MISSED, CallType.REJECTED, CallType.BLOCKED)
    }
}
