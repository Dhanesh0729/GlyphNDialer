// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * Lifecycle state of a single [CallModel], mirroring android.telecom.Call states
 * plus a synthetic [CONFERENCE] marker for a parent conference call.
 */
enum class CallState {
    NEW,
    CONNECTING,
    DIALING,
    RINGING,
    ACTIVE,
    HOLDING,
    CONFERENCE,
    DISCONNECTING,
    DISCONNECTED;

    /** True while the call is in a state the user can act on (mute/hold/route/dtmf). */
    val isLive: Boolean
        get() = this == ACTIVE || this == HOLDING || this == DIALING ||
            this == RINGING || this == CONFERENCE

    val isTerminal: Boolean get() = this == DISCONNECTED || this == DISCONNECTING
}

/** Whether the call originated from us or was received. */
enum class CallDirection { INCOMING, OUTGOING }

/**
 * Telecom capability flags surfaced for the in-call UI. These are derived from
 * android.telecom.Call.Details capability bits in :telecom and reflected here so
 * the in-call ViewModel can enable/disable controls without importing framework
 * types.
 */
data class CallCapability(
    val canHold: Boolean = false,
    val canMute: Boolean = true,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val canManageConference: Boolean = false,
    val canAddCall: Boolean = false,
    val supportsDtmf: Boolean = true,
    /** In-app WebRTC VoIP only (carrier video is never available to us — §2.3). */
    val canUpgradeToVideo: Boolean = false,
    val isSelfManaged: Boolean = false,
)

/**
 * A single call tracked by the InCallService and mirrored into the
 * [com.glyphdialer.core.domain.repository.TelecomRepository].
 *
 * [id] is a stable per-session identifier assigned in :telecom (the Call object's
 * identity is not serializable). [parentCallId]/[childCallIds] model conferences.
 */
data class CallModel(
    val id: String,
    val number: PhoneNumber,
    val displayName: String? = null,
    val photoUri: String? = null,
    val state: CallState = CallState.NEW,
    val direction: CallDirection = CallDirection.INCOMING,
    /** Wall-clock millis when the call connected (ACTIVE), or null if never. */
    val connectTimeMillis: Long? = null,
    val createdAtMillis: Long = 0L,
    val isMuted: Boolean = false,
    val isOnHold: Boolean = false,
    val isConference: Boolean = false,
    val parentCallId: String? = null,
    val childCallIds: List<String> = emptyList(),
    val capability: CallCapability = CallCapability(),
    val disconnectCause: String? = null,
    /** True when this is an in-app WebRTC VoIP call (self-managed ConnectionService). */
    val isVoip: Boolean = false,
    /** True once a video track is negotiated (VoIP only — §2.3). */
    val isVideo: Boolean = false,
    /** Carrier/spam label resolved by the CallScreeningService, when any. */
    val spamLabel: String? = null,
) {
    val isIncomingRinging: Boolean
        get() = direction == CallDirection.INCOMING && state == CallState.RINGING

    /** Elapsed connected duration in millis given [now], or 0 if not connected. */
    fun durationMillis(now: Long): Long =
        connectTimeMillis?.let { (now - it).coerceAtLeast(0L) } ?: 0L
}
