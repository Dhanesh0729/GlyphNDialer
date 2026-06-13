// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall

import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.RecordingTier

/**
 * Immutable UI state for the AOD-minimal in-call screen (BUILD_SPEC §9/§18).
 *
 * Built by [InCallViewModel] from [com.glyphdialer.core.domain.repository.TelecomRepository]
 * (calls + audio), the [com.glyphdialer.core.domain.repository.RecordingRepository] (tier +
 * active), the [com.glyphdialer.core.domain.repository.CapabilityRepository] (honest
 * availability — §9), and the user's [com.glyphdialer.core.domain.repository.SettingsRepository]
 * preferences (captions enabled, etc.).
 *
 * HONESTY PRINCIPLE (§9): every capability the UI offers is gated by an explicit flag
 * here — recording tier, live-caption availability, video-upgrade availability — so a
 * control is never shown when the platform can't honestly deliver it.
 *
 * Stateless rendering: [InCallScreen] reads only this object + lambdas. [nowMillis] is
 * the wall-clock the screen uses to compute the live timer; the ViewModel ticks it.
 */
data class InCallUiState(
    /** The call to foreground (active / ringing / dialing / the conference parent). */
    val primaryCall: CallModel? = null,
    /** A second, backgrounded call (held) for the swap/merge affordances, when any. */
    val heldCall: CallModel? = null,
    /** Children of [primaryCall] when it is a conference parent (§10). */
    val conferenceParticipants: List<CallModel> = emptyList(),
    /** Live system audio state — drives the route picker and the mute control. */
    val audioState: AudioState = AudioState(),
    /** Microphone mute mirror (also present on [AudioState] — surfaced for convenience). */
    val isMuted: Boolean = false,
    // --- Recording (honest, §2.1/§12) ---------------------------------------
    /** True while a recording is in progress. */
    val isRecording: Boolean = false,
    /** The tier actually achievable for this call right now (never faked — §12). */
    val recordingTier: RecordingTier = RecordingTier.UNAVAILABLE,
    /** The tier of the in-progress recording, or [RecordingTier.UNAVAILABLE] when idle. */
    val activeRecordingTier: RecordingTier = RecordingTier.UNAVAILABLE,
    // --- Live captions (honest, §2.4/§13) -----------------------------------
    /** Whether the engine + user preference allow live captions at all. */
    val captionsAvailable: Boolean = false,
    /** Whether captions are currently toggled on by the user. */
    val captionsEnabled: Boolean = false,
    /** Newest caption text; empty until the engine emits. */
    val captionsText: String = "",
    /** True when captions cover only the local side (cellular limit, §2.4). */
    val captionsLocalSideOnly: Boolean = false,
    // --- Video (in-app VoIP only, §2.3/§11) ---------------------------------
    /** Whether a "switch to video" affordance may be shown (VoIP + camera + peer caps). */
    val videoUpgradeAvailable: Boolean = false,
    /** True once a video track is negotiated on the primary call (§11). */
    val isVideo: Boolean = false,
    // --- Waveform (honest "no signal" when empty, §18) ----------------------
    /**
     * Per-frame audio amplitudes (0f..1f) for the live [com.glyphdialer.core.ui.component.GlyphWaveform].
     * Empty when no real amplitude source is wired (the component then shows an honest
     * idle center row rather than fake motion). Populated only where a recorder/VoIP
     * level stream is actually available.
     */
    val amplitudes: List<Float> = emptyList(),
    // --- Misc ----------------------------------------------------------------
    /** Wall-clock millis the screen uses to compute the elapsed timer (ViewModel-ticked). */
    val nowMillis: Long = 0L,
    /** True for the very first frame before the first telecom emission arrives. */
    val isLoading: Boolean = true,
    /** A transient, user-presentable error (e.g. a control action failed). */
    val errorMessage: String? = null,
) {
    /** Whether the primary call is an incoming call still ringing (answer/reject UI). */
    val isIncomingRinging: Boolean
        get() = primaryCall?.isIncomingRinging == true

    /** Whether the primary call models a conference parent with children. */
    val isConference: Boolean
        get() = primaryCall?.isConference == true && conferenceParticipants.isNotEmpty()

    /** Elapsed connected duration for the primary call given [nowMillis]. */
    val primaryDurationMillis: Long
        get() = primaryCall?.durationMillis(nowMillis) ?: 0L

    /** Whether there is any call to render (otherwise the host should dismiss). */
    val hasCall: Boolean
        get() = primaryCall != null

    /** Whether the in-call controls (mute/hold/route/dtmf) are actionable. */
    val controlsEnabled: Boolean
        get() = primaryCall?.state?.isLive == true && primaryCall.state != CallState.RINGING

    /** Whether the route picker can offer [target]. */
    fun routeSupported(target: AudioRoute): Boolean = audioState.supports(target)
}
