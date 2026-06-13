// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail

import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.Voicemail

/**
 * MVVM contract for the Visual Voicemail screen (BUILD_SPEC §8; CONVENTIONS.md §5).
 *
 * The ViewModel exposes a single immutable [VoicemailUiState] via `StateFlow`,
 * receives user intent through [VoicemailEvent], and emits one-shot side effects
 * (dial-out, errors) through [VoicemailEffect].
 *
 * HONESTY PRINCIPLE (CONVENTIONS.md §9 / BUILD_SPEC §8): visual voicemail is
 * carrier/line-dependent. [VoicemailUiState.support] is the single source of truth
 * the UI consults; when VVM is unsupported the screen shows the carrier-dial
 * fallback and never pretends a message list exists.
 */

/** Whether visual voicemail is usable on the active line right now (§8/§9). */
enum class VvmSupportState {
    /** Still resolving carrier/line support. */
    UNKNOWN,

    /** Visual voicemail is available; the message list is meaningful. */
    SUPPORTED,

    /** Not available on this line — fall back to dialing the carrier voicemail. */
    UNSUPPORTED,
}

/** Playback state of the currently-selected voicemail message. */
data class PlaybackState(
    /** Id of the voicemail being played, or null when nothing is loaded. */
    val voicemailId: Long? = null,
    val isPlaying: Boolean = false,
    /** True while the audio body is being downloaded before it can play. */
    val isBuffering: Boolean = false,
    /** Current playback position in milliseconds. */
    val positionMillis: Long = 0L,
    /** Total media duration in milliseconds (0 until known). */
    val durationMillis: Long = 0L,
    /** Recent amplitude samples (0f..1f) for the dot-matrix scrubber waveform. */
    val amplitudes: List<Float> = emptyList(),
) {
    /** Fractional progress in 0f..1f, safe when duration is unknown. */
    val progress: Float
        get() = if (durationMillis > 0L) {
            (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * A voicemail paired with its richer engine [Transcript] when one exists. The
 * carrier-provided [Voicemail.transcriptionText] is used as a fallback caption when
 * no engine transcript has been generated.
 */
data class VoicemailItem(
    val voicemail: Voicemail,
    val transcript: Transcript? = null,
) {
    val id: Long get() = voicemail.id

    /** Best available caption: engine transcript, else carrier text, else null. */
    val displayTranscription: String?
        get() = transcript?.fullText?.takeIf { it.isNotBlank() }
            ?: voicemail.transcriptionText?.takeIf { it.isNotBlank() }

    /** True when the transcription, if any, only covers the local side (§2.4). */
    val transcriptionIsLocalSideOnly: Boolean
        get() = transcript?.isLocalSideOnly == true
}

/** Immutable UI state for the voicemail screen. */
data class VoicemailUiState(
    val support: VvmSupportState = VvmSupportState.UNKNOWN,
    val isLoading: Boolean = true,
    val items: List<VoicemailItem> = emptyList(),
    val playback: PlaybackState = PlaybackState(),
    /** Carrier voicemail dial number for the fallback shortcut (null if unknown). */
    val carrierVoicemailNumber: String? = null,
    /** Id of a voicemail whose transcription is currently being generated. */
    val transcribingId: Long? = null,
    /** A presentable, transient error message (cleared on the next successful op). */
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val unreadCount: Int get() = items.count { !it.voicemail.isRead }
}

/** User intents from the voicemail screen. */
sealed interface VoicemailEvent {
    /** (Re)resolve VVM support and reload the list (pull-to-refresh / first load). */
    data object Refresh : VoicemailEvent

    /** Tap a row: select + start (or download-then-start) playback. */
    data class Play(val id: Long) : VoicemailEvent

    /** Pause the active playback. */
    data object Pause : VoicemailEvent

    /** Resume the paused playback. */
    data object Resume : VoicemailEvent

    /** Scrub to [fraction] (0f..1f) of the current message. */
    data class SeekTo(val fraction: Float) : VoicemailEvent

    /** Generate/refresh an engine transcript for [id] (§13 reuse). */
    data class Transcribe(val id: Long) : VoicemailEvent

    /** Mark [id] read/unread. */
    data class SetRead(val id: Long, val read: Boolean) : VoicemailEvent

    /** Call the message's sender back (handled by :app via TelecomManager). */
    data class CallBack(val id: Long) : VoicemailEvent

    /** Delete the voicemail [id]. */
    data class Delete(val id: Long) : VoicemailEvent

    /** Dial the carrier voicemail number (the unsupported-VVM fallback shortcut). */
    data object DialCarrierVoicemail : VoicemailEvent

    /** Dismiss the current error banner. */
    data object DismissError : VoicemailEvent
}

/**
 * One-shot effects. Dialing is delegated to :app (which owns TelecomManager /
 * Intents) so this feature stays within the allowed dependency graph (§3) and never
 * imports telecom directly.
 */
sealed interface VoicemailEffect {
    /** Ask the host to place a call to [number] (call-back or carrier voicemail). */
    data class Dial(val number: String) : VoicemailEffect

    /** Show a transient message (snackbar/toast) to the user. */
    data class ShowMessage(val message: String) : VoicemailEffect
}
