// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * Type of a historical call-log entry, mirroring android.provider.CallLog.Calls
 * type constants (with our additional BLOCKED/VOICEMAIL distinctions).
 */
enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL;

    /** Entries that count toward the "missed calls" badge/filter. */
    val isMissedLike: Boolean get() = this == MISSED || this == REJECTED || this == BLOCKED
}

/**
 * A single row from the platform call log ([android.provider.CallLog.Calls]).
 *
 * Entries that share the same [number] and [type] in close succession are grouped
 * for display by the call-log feature; [groupCount] carries that multiplicity when
 * pre-grouped, else 1.
 */
data class CallLogEntry(
    val id: Long,
    val number: PhoneNumber,
    val contactLookupKey: String? = null,
    val displayName: String? = null,
    val photoUri: String? = null,
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long = 0L,
    /** True when this call was placed/received over in-app WebRTC VoIP. */
    val isVoip: Boolean = false,
    val isVideo: Boolean = false,
    /** Spam/caller-ID label resolved at the time of the call, if any. */
    val spamLabel: String? = null,
    /** Number of consecutive calls collapsed into this group (>= 1). */
    val groupCount: Int = 1,
) {
    val isMissed: Boolean get() = type.isMissedLike
}
