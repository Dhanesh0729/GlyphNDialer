// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.mapper

import android.os.Build
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.VideoProfile
import com.glyphdialer.core.domain.model.CallCapability
import com.glyphdialer.core.domain.model.CallDirection
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.NumberLabel
import com.glyphdialer.core.domain.model.PhoneNumber

/**
 * Maps the framework [android.telecom.Call] world into the framework-free domain
 * [CallModel] (CONVENTIONS.md §6, BUILD_SPEC §7). This is the ONLY place that reads
 * [Call.Details] capability/property/state bits so the rest of the app — and the
 * in-call UI — never touches `android.telecom.*`.
 */
internal object CallMapper {

    /**
     * Build a [CallModel] for [call] with the stable session [id] assigned by the
     * [com.glyphdialer.telecom.CallRegistry]. [childIds]/[parentId] are resolved by
     * the registry (which owns the Call->id mapping) and threaded in here.
     *
     * [isMuted] reflects the *session-wide* mute state (telecom mute is not
     * per-call) supplied by the registry from the current CallAudioState.
     */
    fun toModel(
        id: String,
        call: Call,
        parentId: String?,
        childIds: List<String>,
        isMuted: Boolean,
    ): CallModel {
        val details: Call.Details = call.details
        val state = toState(call)
        val direction = toDirection(details)
        // A parent conference call: it has children and is not itself a child.
        val isConference = parentId == null && childIds.isNotEmpty()

        return CallModel(
            id = id,
            number = toPhoneNumber(details),
            displayName = resolveDisplayName(details),
            photoUri = null, // Resolved by :feature:incall via ContactsRepository (avoids a contacts dep here).
            state = state,
            direction = direction,
            connectTimeMillis = details.connectTimeMillis.takeIf { it > 0L },
            createdAtMillis = details.creationTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
            isMuted = isMuted,
            isOnHold = state == CallState.HOLDING,
            isConference = isConference,
            parentCallId = parentId,
            childCallIds = childIds,
            capability = toCapability(details, isSelfManaged(details)),
            disconnectCause = toDisconnectMessage(details.disconnectCause),
            isVoip = isSelfManaged(details),
            isVideo = isVideoActive(details),
            spamLabel = null, // Set by the CallScreeningService path, not the in-call mapper.
        )
    }

    /** Map the framework call state to the domain [CallState]. */
    fun toState(call: Call): CallState =
        when (legacyState(call)) {
            Call.STATE_NEW -> CallState.NEW
            Call.STATE_CONNECTING -> CallState.CONNECTING
            Call.STATE_DIALING -> CallState.DIALING
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_ACTIVE -> if (call.children.isNotEmpty()) CallState.CONFERENCE else CallState.ACTIVE
            Call.STATE_HOLDING -> CallState.HOLDING
            Call.STATE_DISCONNECTING -> CallState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallState.DISCONNECTED
            Call.STATE_SELECT_PHONE_ACCOUNT -> CallState.CONNECTING
            Call.STATE_PULLING_CALL -> CallState.DIALING
            Call.STATE_AUDIO_PROCESSING -> CallState.CONNECTING
            Call.STATE_SIMULATED_RINGING -> CallState.RINGING
            else -> CallState.NEW
        }

    /**
     * Call.getState() was deprecated in API 31 in favour of getDetails().getState();
     * read whichever the platform offers so we stay correct across SDKs.
     */
    @Suppress("DEPRECATION")
    private fun legacyState(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state

    private fun toDirection(details: Call.Details): CallDirection =
        when (details.callDirection) {
            Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            else -> CallDirection.INCOMING
        }

    /**
     * Best-effort display name from the framework. [Call.Details.getContactDisplayName]
     * is only available on API 30+, so we fall back to the caller-provided display name
     * on API 29 (the real contacts lookup is done by :feature:incall via ContactsRepository).
     */
    private fun resolveDisplayName(details: Call.Details): String? {
        val caller = details.callerDisplayName?.takeIf { it.isNotBlank() }
        if (caller != null) return caller
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            details.contactDisplayName?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    /** Extract the dialable number from the handle (tel: / sip: URI). */
    private fun toPhoneNumber(details: Call.Details): PhoneNumber {
        val raw = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: ""
        // Region-aware formatting is applied downstream by PhoneNumberFormatter
        // (:core:data); here we only carry the raw handle so :telecom has no
        // libphonenumber dependency.
        return PhoneNumber(raw = raw, formatted = raw, label = NumberLabel.OTHER)
    }

    /** Translate framework capability/property bits into the domain [CallCapability]. */
    fun toCapability(details: Call.Details, selfManaged: Boolean): CallCapability =
        CallCapability(
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            canMute = details.can(Call.Details.CAPABILITY_MUTE),
            canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE),
            canSwap = details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            canManageConference = details.can(Call.Details.CAPABILITY_MANAGE_CONFERENCE),
            canAddCall = details.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            supportsDtmf = true,
            // §2.3 HONESTY: carrier video (ViLTE) is NEVER available to a third-party
            // dialer. We only ever offer the in-app WebRTC upgrade, and only for
            // self-managed VoIP calls — never for cellular calls.
            canUpgradeToVideo = selfManaged,
            isSelfManaged = selfManaged,
        )

    private fun isSelfManaged(details: Call.Details): Boolean =
        details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)

    /**
     * Whether a video track is currently active. For a *carrier* call this would be
     * the framework video state, but per §2.3 we never expose carrier video, so a
     * cellular call always maps to false; VoIP video is tracked by the WebRtcClient
     * and reconciled by the ConnectionService bridge, not read from here.
     */
    private fun isVideoActive(details: Call.Details): Boolean =
        isSelfManaged(details) && details.videoState != VideoProfile.STATE_AUDIO_ONLY

    private fun toDisconnectMessage(cause: DisconnectCause?): String? {
        cause ?: return null
        if (cause.code == DisconnectCause.UNKNOWN) return null
        return cause.label?.toString()
            ?: cause.description?.toString()
            ?: cause.reason
    }
}
