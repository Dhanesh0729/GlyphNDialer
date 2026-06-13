// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall

import com.glyphdialer.core.domain.model.AudioRoute

/**
 * User intents from the in-call screen (CONVENTIONS.md §5 — single `onEvent` sink).
 * The screen is fully stateless; it raises these and the [InCallViewModel] reduces
 * them against the use cases.
 */
sealed interface InCallEvent {

    // --- Primary call lifecycle ---------------------------------------------
    /** Answer the ringing incoming call. */
    data object Answer : InCallEvent

    /** End/disconnect the primary call. */
    data object EndCall : InCallEvent

    /** Reject the ringing incoming call (optionally with a quick-reply [message]). */
    data class Reject(val message: String? = null) : InCallEvent

    // --- Audio / mute --------------------------------------------------------
    /** Toggle the microphone mute. */
    data object ToggleMute : InCallEvent

    /** Route call audio to [route] (earpiece / speaker / BT / wired). */
    data class SelectAudioRoute(val route: AudioRoute) : InCallEvent

    // --- Hold ----------------------------------------------------------------
    /** Toggle hold/resume for the primary call. */
    data object ToggleHold : InCallEvent

    // --- DTMF ----------------------------------------------------------------
    /** Open/close the in-call DTMF keypad overlay. */
    data class ShowDtmf(val show: Boolean) : InCallEvent

    /** Send a DTMF [digit] on the primary call. */
    data class Dtmf(val digit: Char) : InCallEvent

    // --- Multi-call / conference (§10) --------------------------------------
    /** Navigate to the dialpad to place a second call. */
    data object AddCall : InCallEvent

    /** Merge the primary and held calls into a conference. */
    data object Merge : InCallEvent

    /** Swap the active and held calls. */
    data object Swap : InCallEvent

    /** Split a conference participant [callId] back out into a standalone call. */
    data class SplitParticipant(val callId: String) : InCallEvent

    /** Hold/resume an individual conference participant [callId]. */
    data class HoldParticipant(val callId: String, val hold: Boolean) : InCallEvent

    /** Disconnect an individual conference participant [callId]. */
    data class DisconnectParticipant(val callId: String) : InCallEvent

    // --- Recording (§12) -----------------------------------------------------
    /** Toggle call recording at the highest honestly-supported tier. */
    data object ToggleRecording : InCallEvent

    // --- Live captions (§13) -------------------------------------------------
    /** Toggle the live-caption overlay. */
    data object ToggleCaptions : InCallEvent

    // --- Video (in-app VoIP only, §11) --------------------------------------
    /** Request/clear an in-app video upgrade (VoIP only). */
    data object ToggleVideo : InCallEvent

    // --- Misc ----------------------------------------------------------------
    /** Acknowledge/clear a transient error message. */
    data object ClearError : InCallEvent
}

/**
 * One-shot side effects the screen consumes once (navigation, dismissal). Delivered
 * via a `Channel`/`SharedFlow` so they never replay on rotation (CONVENTIONS.md §5).
 */
sealed interface InCallEffect {
    /** No live calls remain — the host should finish the full-screen in-call surface. */
    data object Dismiss : InCallEffect

    /** Navigate to the dialpad to add a second call (assembled in :app). */
    data object NavigateToDialpad : InCallEffect

    /** Show a transient message (snackbar/toast) — e.g. a control failed. */
    data class ShowMessage(val message: String) : InCallEffect
}
