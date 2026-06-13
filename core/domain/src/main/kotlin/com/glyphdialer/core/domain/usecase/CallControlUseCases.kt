// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.repository.TelecomRepository
import javax.inject.Inject

/**
 * In-call control use cases (§7.6/§9). Each is a thin, intention-revealing wrapper
 * over [TelecomRepository] so ViewModels depend on verbs, not the repo surface.
 * They are trivial delegations and therefore not separately unit-tested (the
 * repository is the unit under test in `:telecom`).
 */

/** Place an outgoing call (§7.3). Pass [isVoip]=true to route via self-managed VoIP. */
class PlaceCallUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(number: String, isVoip: Boolean = false): AppResult<Unit> =
        telecom.placeCall(number, isVoip)
}

/** Answer a ringing call. */
class AnswerCallUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String): AppResult<Unit> = telecom.answerCall(callId)
}

/** End/disconnect a call. */
class EndCallUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String): AppResult<Unit> = telecom.endCall(callId)
}

/** Toggle hold/resume for [callId]; pass the desired [hold] state explicitly. */
class ToggleHoldUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String, hold: Boolean): AppResult<Unit> =
        telecom.setHold(callId, hold)
}

/** Toggle the microphone mute for the active call session. */
class ToggleMuteUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(muted: Boolean): AppResult<Unit> = telecom.setMuted(muted)
}

/** Route call audio to [route] (earpiece/speaker/BT/wired). */
class SetAudioRouteUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(route: AudioRoute): AppResult<Unit> = telecom.setAudioRoute(route)
}

/** Send a DTMF [digit] on [callId]. Set [press]=false to release a held tone. */
class SendDtmfUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String, digit: Char, press: Boolean = true): AppResult<Unit> =
        if (press) telecom.playDtmf(callId, digit) else telecom.stopDtmf(callId)
}

/** Merge two calls into a conference (§10). */
class MergeConferenceUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String, otherCallId: String): AppResult<Unit> =
        telecom.merge(callId, otherCallId)
}

/** Swap the active and held calls (§10). */
class SwapCallUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String): AppResult<Unit> = telecom.swap(callId)
}

/** Split [callId] out of its parent conference (§10). */
class SplitFromConferenceUseCase @Inject constructor(
    private val telecom: TelecomRepository,
) {
    suspend operator fun invoke(callId: String): AppResult<Unit> =
        telecom.splitFromConference(callId)
}
