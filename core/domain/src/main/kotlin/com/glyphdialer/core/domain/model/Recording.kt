// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * The recording capability TIER actually achievable for a given call (§2.1, §12).
 *
 * This enum is the heart of the honesty principle for recording: the app resolves
 * the highest supported tier at call start and the UI MUST surface it truthfully.
 * It never claims two-way capture when only one side is available.
 *
 * Ordering (by `quality`) goes from best to none so callers can pick the highest
 * available tier with a simple `maxByOrNull { it.quality }`.
 */
enum class RecordingTier(val quality: Int) {
    /** Best: default dialer on an OEM/system/rooted build that exposes call audio. */
    SYSTEM_TWO_WAY(3),

    /** In-app WebRTC VoIP: we own both media tracks, full two-way quality. */
    VOIP_TWO_WAY(2),

    /** Stock-Android fallback: local mic only. Label "my side only" — NOT full call. */
    LOCAL_ONE_SIDED(1),

    /** Recording is not possible on this device/call at all. */
    UNAVAILABLE(0);

    /** Whether this tier captures the remote party (honest two-way). */
    val isTwoWay: Boolean get() = this == SYSTEM_TWO_WAY || this == VOIP_TWO_WAY

    val isAvailable: Boolean get() = this != UNAVAILABLE
}

/**
 * Metadata for a captured call recording (RoomEntity: RecordingEntity). The audio
 * body lives encrypted in app-private storage (or MediaStore on explicit export).
 *
 * [tier] records the tier used so history can honestly display "two-way" vs
 * "my side only" per recording (§12).
 */
data class Recording(
    val id: String,
    /** The [CallModel.id] / call-log id this recording belongs to, when known. */
    val callId: String? = null,
    val number: PhoneNumber,
    val contactName: String? = null,
    val startedAtMillis: Long,
    val durationMillis: Long = 0L,
    val tier: RecordingTier,
    /** Absolute path / URI of the (encrypted) audio file. */
    val filePath: String,
    val transcriptId: String? = null,
    val hasTranscript: Boolean = false,
    /** True when the other party was given an audible announcement (§2.2). */
    val announced: Boolean = true,
) {
    val isTwoWay: Boolean get() = tier.isTwoWay
}
