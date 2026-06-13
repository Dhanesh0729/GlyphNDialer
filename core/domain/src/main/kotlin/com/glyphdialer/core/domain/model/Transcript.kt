// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * One timestamped segment of a [Transcript] (RoomEntity: TranscriptSegmentEntity).
 *
 * [startMillis]/[endMillis] are offsets from the start of the audio, enabling
 * tap-to-seek alignment in the player. [speaker] is populated only for two-track
 * VoIP recordings where speaker diarization is possible (§13).
 */
data class TranscriptSegment(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
    val speaker: String? = null,
    /** Engine confidence 0f..1f, or null when the engine doesn't report it. */
    val confidence: Float? = null,
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
}

/**
 * A full transcript of a recording or voicemail (RoomEntity: TranscriptEntity).
 *
 * [fullText] is the concatenated, searchable body; [segments] carry the
 * timestamp alignment. [engine] records which [TranscriptionEngineType] produced
 * it so the UI can be honest about source/quality (§2.4, §13).
 */
data class Transcript(
    val id: String,
    val recordingId: String? = null,
    val language: String? = null,
    val fullText: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val engine: TranscriptionEngineType = TranscriptionEngineType.ON_DEVICE_WHISPER,
    val createdAtMillis: Long = 0L,
    /**
     * True when only the local side was transcribed (cellular without call-audio
     * access — inherits the recording limits, §2.4). UI must say so.
     */
    val isLocalSideOnly: Boolean = false,
) {
    /** Returns the segment active at [positionMillis], for tap-to-seek highlighting. */
    fun segmentAt(positionMillis: Long): TranscriptSegment? =
        segments.firstOrNull { positionMillis in it.startMillis until it.endMillis.coerceAtLeast(it.startMillis + 1) }
}
