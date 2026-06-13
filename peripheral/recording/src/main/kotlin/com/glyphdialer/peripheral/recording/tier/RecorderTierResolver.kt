// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.tier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.RecordingTier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the highest [RecordingTier] the device/call can HONESTLY deliver (§2.1, §12).
 *
 * The whole point of this class is to be conservative: on stock Android 10+ a
 * third-party app simply cannot capture the remote party, so we must never claim
 * [RecordingTier.SYSTEM_TWO_WAY] unless we have a concrete, positive signal that the
 * OEM/system build exposes call audio AND we are the default dialer. Otherwise we fall
 * to [RecordingTier.VOIP_TWO_WAY] for in-app calls (we own both media tracks),
 * [RecordingTier.LOCAL_ONE_SIDED] when only the mic is usable, or
 * [RecordingTier.UNAVAILABLE] when RECORD_AUDIO is denied.
 *
 * Detection strategy (documented so reviewers can audit the honesty claim):
 *  - RECORD_AUDIO denied                       -> UNAVAILABLE (nothing is possible).
 *  - In-app VoIP call ([CallModel.isVoip])     -> VOIP_TWO_WAY (we own the tracks).
 *  - System call audio detectable AND default  -> SYSTEM_TWO_WAY (Tier A, OEM-gated).
 *    dialer
 *  - Otherwise                                 -> LOCAL_ONE_SIDED ("my side only").
 *
 * "System call audio detectable" is deliberately hard to satisfy. We require ALL of:
 *  1. We hold the default-dialer role (otherwise we have no call session at all).
 *  2. A device-level signal that call-audio sources are honored — supplied by the
 *     [SystemCallAudioProbe] (default impl is the conservative one and returns false;
 *     an OEM/system/rooted build can provide a probe that returns true after actually
 *     opening an [android.media.AudioRecord] on a VOICE_CALL source).
 *
 * We never *assume* Tier A from the API surface alone, because the API existing
 * (MediaRecorder.AudioSource.VOICE_CALL) does NOT mean a third-party app may use it —
 * on stock builds it throws SecurityException or yields silence.
 */
@Singleton
class RecorderTierResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemCallAudioProbe: SystemCallAudioProbe,
) {

    /**
     * The device BASELINE tier, independent of any specific call (matches
     * [com.glyphdialer.core.domain.repository.CallRecorder.supportedTier]). VoIP is a
     * per-call property so the baseline never reports VOIP_TWO_WAY.
     */
    fun resolveBaseline(): RecordingTier {
        if (!hasRecordAudioPermission()) {
            Timber.d("Tier resolve: RECORD_AUDIO denied -> UNAVAILABLE")
            return RecordingTier.UNAVAILABLE
        }
        if (isSystemCallAudioAvailable()) {
            Timber.d("Tier resolve (baseline): system call audio detected -> SYSTEM_TWO_WAY")
            return RecordingTier.SYSTEM_TWO_WAY
        }
        Timber.d("Tier resolve (baseline): stock device -> LOCAL_ONE_SIDED")
        return RecordingTier.LOCAL_ONE_SIDED
    }

    /**
     * The highest tier achievable for a SPECIFIC [call] right now (matches
     * [com.glyphdialer.core.domain.repository.CallRecorder.supportedTierFor]).
     *
     * Refines [resolveBaseline] with call context: an in-app VoIP call can reach
     * [RecordingTier.VOIP_TWO_WAY] even when the device baseline is local-only, because
     * for VoIP we own both WebRTC media tracks.
     */
    fun resolveForCall(call: CallModel): RecordingTier {
        if (!hasRecordAudioPermission()) {
            Timber.d("Tier resolve for call %s: RECORD_AUDIO denied -> UNAVAILABLE", call.id)
            return RecordingTier.UNAVAILABLE
        }

        // In-app VoIP: we own both tracks -> honest two-way. This is independent of any
        // system call-audio gating; it does not require Tier A.
        if (call.isVoip) {
            Timber.d("Tier resolve for call %s: in-app VoIP -> VOIP_TWO_WAY", call.id)
            return RecordingTier.VOIP_TWO_WAY
        }

        // Cellular call. Tier A only when the OEM/system build genuinely exposes call audio.
        if (isSystemCallAudioAvailable()) {
            Timber.d("Tier resolve for call %s: system call audio -> SYSTEM_TWO_WAY", call.id)
            return RecordingTier.SYSTEM_TWO_WAY
        }

        Timber.d("Tier resolve for call %s: stock cellular -> LOCAL_ONE_SIDED", call.id)
        return RecordingTier.LOCAL_ONE_SIDED
    }

    /** RECORD_AUDIO is the precondition for ANY tier (even local-side). */
    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Tier A gate. Conservative by construction: requires BOTH the default-dialer role
     * (we need a real call session) and a positive device probe. See class docs.
     */
    private fun isSystemCallAudioAvailable(): Boolean {
        if (!isDefaultDialer()) return false
        return systemCallAudioProbe.canCaptureCallAudio()
    }

    /** Whether this app currently holds the default-dialer role. */
    private fun isDefaultDialer(): Boolean {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return false
        return try {
            tm.defaultDialerPackage == context.packageName
        } catch (se: SecurityException) {
            Timber.w(se, "Could not read defaultDialerPackage; assuming not default dialer")
            false
        }
    }

    private companion object {
        // Documented for clarity: VOICE_CALL/VOICE_DOWNLINK/VOICE_UPLINK only became
        // even theoretically usable on certain builds; the API level alone proves nothing.
        @Suppress("unused")
        val MIN_VOICE_CALL_API = Build.VERSION_CODES.Q
    }
}
