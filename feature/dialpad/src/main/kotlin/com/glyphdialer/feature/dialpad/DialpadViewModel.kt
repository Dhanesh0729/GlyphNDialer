// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.fold
import com.glyphdialer.core.common.getOrDefault
import com.glyphdialer.core.common.getOrNull
import com.glyphdialer.core.common.onFailure
import com.glyphdialer.core.common.onSuccess
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CapabilityFlags
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.domain.model.SpeedDialSlot
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.CallLogRepository
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import com.glyphdialer.core.domain.repository.SpeedDialRepository
import com.glyphdialer.core.domain.repository.VoicemailRepository
import com.glyphdialer.core.domain.usecase.ObservePreferencesUseCase
import com.glyphdialer.core.domain.usecase.PlaceCallUseCase
import com.glyphdialer.core.domain.usecase.T9Match
import com.glyphdialer.core.domain.usecase.T9SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The T9 dialpad ViewModel (BUILD_SPEC §8 + §17.4/§18 ★; CONVENTIONS.md §5).
 *
 * Holds the entered number, drives live T9 smart-search over contacts (the data
 * layer's snapshot) debounced as the user types, exposes honest capability flags,
 * and routes key presses to BOTH the physical Glyph stroke
 * ([GlyphController.playDigitStroke]) and the on-screen mirrored dot animation
 * (via [DialpadUiState.lastStroke]).
 *
 * All construction is via constructor injection; dispatchers are injected, never
 * hard-coded. Fallible operations use the shared [com.glyphdialer.core.common.AppResult].
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialpadViewModel @Inject constructor(
    private val t9Search: T9SearchUseCase,
    private val placeCall: PlaceCallUseCase,
    private val contactsRepository: ContactsRepository,
    private val callLogRepository: CallLogRepository,
    private val speedDialRepository: SpeedDialRepository,
    private val voicemailRepository: VoicemailRepository,
    private val capabilityRepository: CapabilityRepository,
    private val formatter: PhoneNumberFormatter,
    private val glyphController: GlyphController,
    observePreferences: ObservePreferencesUseCase,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private var toneGenerator: ToneGenerator? = null

    private val vibrator = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull() ?: runCatching {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }.getOrNull()

    /** Raw entered characters; the source of truth the rest of the state derives from. */
    private val entered = MutableStateFlow("")

    /** Monotonic counter so repeated identical digits still re-trigger the mirror. */
    private var strokeSeq = 0L
    private val lastStroke = MutableStateFlow<StrokeTrigger?>(null)

    private val effectChannel = Channel<DialpadEffect>(Channel.BUFFERED)

    /** One-shot effects (navigation / toasts) per CONVENTIONS.md §5. */
    val effects = effectChannel.receiveAsFlow()

    // Live preferences (Glyph toggles) and capabilities (honest availability).
    private val preferences: StateFlow<UserPreferences> =
        observePreferences()
            .stateInEagerly(UserPreferences())

    private val capabilities: StateFlow<CapabilityFlags> =
        capabilityRepository.capabilities
            .stateInEagerly(CapabilityFlags())

    // T9 candidate set: device contacts MERGED with call-log numbers that aren't tied to
    // a contact (BUILD_SPEC §8 — "smart search filtering contacts + call log"). Call-log
    // numbers become lightweight synthetic [Contact]s so the pure [T9SearchUseCase] can
    // rank them uniformly. Shared (stateIn) so typing doesn't re-subscribe the
    // ContentObservers on every keystroke.
    private val contactsSnapshot: StateFlow<List<Contact>> =
        combine(
            contactsRepository.observeContacts(),
            callLogRepository.observeCallLog(),
        ) { contacts, callLog ->
            mergeCandidates(contacts, callLog)
        }
            .flowOn(defaultDispatcher)
            .stateInEagerly(emptyList())

    /**
     * Ranked T9 results, recomputed (debounced + off the main thread) whenever the
     * entered digits OR the contact snapshot changes (§8).
     */
    private val t9Results: StateFlow<List<T9Match>> =
        combine(entered.debounce(T9_DEBOUNCE_MS), contactsSnapshot) { query, contacts ->
            query to contacts
        }
            .distinctUntilChanged()
            .flatMapLatest { (query, contacts) ->
                if (query.none { it.isDigit() }) {
                    flowOf(emptyList())
                } else {
                    flowOf(t9Search(query, contacts))
                }
            }
            .flowOn(defaultDispatcher)
            .stateInEagerly(emptyList())

    /** The single immutable UI state the View renders. */
    val uiState: StateFlow<DialpadUiState> =
        combine(
            entered,
            t9Results,
            preferences,
            capabilities,
            lastStroke,
        ) { number, results, prefs, caps, stroke ->
            DialpadUiState(
                entered = number,
                formattedNumber = formatNumber(number),
                t9Results = results,
                capabilities = DialpadCapabilities(
                    glyphAvailable = caps.glyphAvailable && glyphController.isAvailable,
                    isDefaultDialer = caps.isDefaultDialer,
                ),
                // On-screen mirror plays whenever the user enabled dialpad strokes; the
                // *physical* Glyph stroke is additionally gated on real availability (§9).
                glyphMirrorEnabled = prefs.glyphMasterEnabled && prefs.glyphDialpadStrokes,
                lastStroke = stroke,
            )
        }.stateInEagerly(DialpadUiState())

    init {
        // Refresh capabilities once on entry (role/permission may have changed).
        viewModelScope.launch {
            capabilityRepository.refresh().onFailure {
                Timber.w(it.error, "Capability refresh failed; using cached/default flags")
            }
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 80)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ToneGenerator")
        }
    }

    /** Single entry point for user intent (CONVENTIONS.md §5). */
    fun onEvent(event: DialpadEvent) {
        when (event) {
            is DialpadEvent.KeyPress -> onKeyPress(event.char)
            is DialpadEvent.LongPress -> onLongPress(event.char)
            DialpadEvent.Backspace -> backspace()
            DialpadEvent.ClearAll -> entered.value = ""
            is DialpadEvent.Paste -> paste(event.text)
            DialpadEvent.Call -> call(entered.value)
            is DialpadEvent.CallNumber -> call(event.number)
            is DialpadEvent.FillFromMatch -> fillFromMatch(event.match)
            DialpadEvent.AddContact -> addContact()
        }
    }

    // ---- Key handling --------------------------------------------------------

    private fun onKeyPress(char: Char) {
        if (char !in DIALABLE) return
        entered.update { it + char }
        fireStroke(char)
        playLocalFeedback(char)
    }

    /**
     * Long-press shortcuts (BUILD_SPEC §8):
     *  - `0` → `+` (international prefix), only sensible as the leading character.
     *  - `1` → voicemail (visual VVM where supported, else dial the carrier number).
     *  - `2`–`9` → speed-dial (place the assigned call, or prompt to assign).
     *  - backspace long-press is delivered as [DialpadEvent.ClearAll] by the View.
     */
    private fun onLongPress(char: Char) {
        when (char) {
            '0' -> {
                // Replace a trailing '0' (just typed by the press) with '+', else append.
                entered.update { current ->
                    if (current.endsWith('0')) current.dropLast(1) + '+' else current + '+'
                }
                fireStroke('0')
                playLocalFeedback('+')
            }
            '1' -> dialVoicemail()
            in '2'..'9' -> triggerSpeedDial(char - '0')
            else -> Unit
        }
    }

    private fun playLocalFeedback(digit: Char) {
        val tone = when (digit) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> -1
        }
        if (tone != -1) {
            runCatching {
                toneGenerator?.startTone(tone, 120)
            }.onFailure {
                Timber.w(it, "Failed to play DTMF tone for %s", digit)
            }
        }
        vibrator?.let { v ->
            if (v.hasVibrator()) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(50)
                    }
                }.onFailure {
                    Timber.w(it, "Failed to vibrate for dialpad click")
                }
            }
        }
    }

    private fun backspace() {
        entered.update { if (it.isEmpty()) it else it.dropLast(1) }
    }

    private fun paste(text: String) {
        val cleaned = text.filter { it in DIALABLE }
        if (cleaned.isEmpty()) {
            emit(DialpadEffect.ShowMessage("Nothing dialable to paste"))
            return
        }
        entered.update { it + cleaned }
    }

    private fun fillFromMatch(match: T9Match) {
        val number = match.matchedNumber
            ?: match.contact.primaryNumber?.dialValue
            ?: return
        entered.value = number
    }

    // ---- Actions -------------------------------------------------------------

    private fun call(number: String) {
        val target = number.ifBlank { entered.value }
        if (target.none { it.isDigit() }) {
            emit(DialpadEffect.ShowMessage("Enter a number to call"))
            return
        }
        viewModelScope.launch {
            placeCall(target)
                .onSuccess {
                    Timber.d("Placed call to %s", target)
                    emit(DialpadEffect.CallPlaced)
                }
                .onFailure { failure ->
                    Timber.e(failure.error, "Failed to place call")
                    emit(DialpadEffect.ShowMessage(failure.message ?: "Couldn't place the call"))
                }
        }
    }

    private fun addContact() {
        val number = entered.value
        if (number.isBlank()) {
            emit(DialpadEffect.ShowMessage("Enter a number first"))
            return
        }
        emit(DialpadEffect.AddToContacts(number))
    }

    private fun dialVoicemail() {
        viewModelScope.launch {
            // Honesty principle (§9, §8): prefer carrier VM number; the host opens
            // visual voicemail when supported.
            val supported = voicemailRepository.isSupported().getOrDefault(false)
            val number = voicemailRepository.carrierVoicemailNumber().getOrNull()
            when {
                number != null -> call(number)
                supported -> emit(DialpadEffect.ShowMessage("Open Voicemail from the menu"))
                else -> emit(DialpadEffect.ShowMessage("Voicemail isn't available on this line"))
            }
        }
    }

    private fun triggerSpeedDial(slot: Int) {
        viewModelScope.launch {
            speedDialRepository.getSlot(slot).fold(
                onSuccess = { assignment: SpeedDialSlot? ->
                    if (assignment != null) {
                        call(assignment.number)
                    } else {
                        // Unassigned: prompt the host to set it (BUILD_SPEC §8 speed-dial).
                        emit(DialpadEffect.AssignSpeedDial(slot))
                    }
                },
                onFailure = { failure ->
                    Timber.w(failure.error, "Speed-dial lookup failed for slot %d", slot)
                    emit(DialpadEffect.ShowMessage("Speed dial unavailable"))
                },
            )
        }
    }

    /**
     * Assign the current entry (or a picked number) to [slot] — invoked by the host
     * after it resolves [DialpadEffect.AssignSpeedDial].
     */
    fun assignSpeedDial(slot: Int, number: String, contactLookupKey: String? = null) {
        viewModelScope.launch {
            speedDialRepository.assign(slot, contactLookupKey, number)
                .onFailure { emit(DialpadEffect.ShowMessage("Couldn't save speed dial")) }
        }
    }

    // ---- Glyph (physical + on-screen mirror) ---------------------------------

    /**
     * Fire both halves of the §17.4 differentiator: the on-screen mirrored dot stroke
     * (always, when the user enabled it) and the physical Glyph stroke (only when the
     * controller reports availability — honesty principle §9). The controller is safe
     * to call unconditionally and no-ops when unavailable, but we additionally honor
     * the user's master/dialpad toggles.
     */
    private fun fireStroke(digit: Char) {
        val prefs = preferences.value
        if (!(prefs.glyphMasterEnabled && prefs.glyphDialpadStrokes)) return

        // On-screen mirror — tagged with a unique id so repeats re-animate.
        lastStroke.value = StrokeTrigger(digit = digit, id = strokeSeq++)

        // Physical Glyph stroke — no-op on non-Nothing hardware.
        if (glyphController.isAvailable) {
            runCatching { glyphController.playDigitStroke(digit) }
                .onFailure { Timber.w(it, "Glyph stroke failed for %s", digit) }
        }
    }

    // ---- Helpers -------------------------------------------------------------

    private fun formatNumber(raw: String): String =
        runCatching { formatter.formatAsYouType(raw) }
            .getOrElse { raw }
            .ifBlank { raw }

    /**
     * Merge device [contacts] with [callLog] entries, synthesizing a lightweight
     * [Contact] for each distinct call-log number NOT already represented by a real
     * contact, so the pure T9 ranker searches both sources (BUILD_SPEC §8). Pure and
     * deterministic; runs on the default dispatcher.
     */
    private fun mergeCandidates(
        contacts: List<Contact>,
        callLog: List<CallLogEntry>,
    ): List<Contact> {
        if (callLog.isEmpty()) return contacts

        // Numbers already covered by a real contact (compared on digits only).
        val knownDigits = HashSet<String>()
        for (contact in contacts) {
            for (number in contact.numbers) {
                knownDigits += number.dialValue.filter { it.isDigit() }
            }
        }

        val synthetic = LinkedHashMap<String, Contact>()
        for (entry in callLog) {
            val digits = entry.number.dialValue.filter { it.isDigit() }
            if (digits.isEmpty() || digits in knownDigits || digits in synthetic) continue
            // Negative ids keep synthetic candidates from colliding with real contact ids.
            synthetic[digits] = Contact(
                id = -(synthetic.size + 1L),
                lookupKey = "calllog:$digits",
                displayName = entry.displayName ?: entry.number.formatted,
                numbers = listOf(entry.number),
            )
        }
        return if (synthetic.isEmpty()) contacts else contacts + synthetic.values
    }

    private fun emit(effect: DialpadEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    /** Share an upstream flow as hot state for the VM's lifetime. */
    private fun <T> Flow<T>.stateInEagerly(initial: T): StateFlow<T> =
        stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = initial,
        )

    override fun onCleared() {
        super.onCleared()
        // Release the Glyph session this VM may have driven (safe to call repeatedly).
        runCatching { glyphController.release() }
        runCatching { toneGenerator?.release() }
    }

    private companion object {
        /** Debounce so T9 recompute waits a beat after the last keystroke (§8). */
        const val T9_DEBOUNCE_MS = 120L

        /** Keep upstream flows warm briefly across config changes. */
        const val STOP_TIMEOUT_MS = 5_000L

        /** Characters accepted from key presses / paste / clipboard. */
        val DIALABLE: Set<Char> = buildSet {
            addAll('0'..'9')
            add('*'); add('#'); add('+'); add(','); add(';')
        }
    }
}
