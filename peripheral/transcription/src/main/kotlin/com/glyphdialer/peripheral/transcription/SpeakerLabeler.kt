// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription

import com.glyphdialer.core.domain.model.TranscriptSegment
import javax.inject.Inject

/**
 * Simple, deterministic 2-track speaker labeling for VoIP recordings (§13).
 *
 * For an in-app WebRTC VoIP call (the only honest two-way capture path, §2.4) we own
 * both media tracks, so we can transcribe each side independently and then *align*
 * the two timelines back into a single, time-ordered transcript with stable speaker
 * labels. There is no acoustic diarization here — the separation comes for free from
 * having two physical tracks. For single-track captures (cellular local-mic-only) we
 * cannot tell the speakers apart and intentionally leave [TranscriptSegment.speaker]
 * null rather than fabricate one.
 *
 * Everything in this class is PURE (no Android, no coroutines, no I/O) so the
 * alignment + labeling logic is fully unit-testable (CONVENTIONS.md §11).
 */
class SpeakerLabeler @Inject constructor() {

    /** The two logical tracks of a VoIP call. */
    enum class Track {
        /** The local user (our microphone / our outgoing media track). */
        LOCAL,

        /** The remote party (their incoming media track). */
        REMOTE,
    }

    /** The presentable label written into [TranscriptSegment.speaker] for each track. */
    fun labelFor(track: Track): String = when (track) {
        Track.LOCAL -> LABEL_LOCAL
        Track.REMOTE -> LABEL_REMOTE
    }

    /**
     * A raw, per-track segment produced by transcribing one VoIP track in isolation.
     * Carries the same timestamp space (offsets from the start of the call) as every
     * other track so the timelines can be interleaved.
     */
    data class TrackSegment(
        val track: Track,
        val startMillis: Long,
        val endMillis: Long,
        val text: String,
        val confidence: Float? = null,
    )

    /**
     * Aligns and labels the segments from both tracks into a single, time-ordered
     * list of [TranscriptSegment]s with speaker labels applied.
     *
     * Ordering is by [TrackSegment.startMillis], then by [TrackSegment.endMillis], then
     * with [Track.LOCAL] before [Track.REMOTE] so the result is fully deterministic
     * even when both parties start talking at the exact same instant. Ids are
     * generated with [idPrefix] + a monotonic index so callers get stable, unique ids
     * suitable for a Room primary key.
     *
     * This is a pure function: same input always yields the same output.
     */
    fun align(
        localSegments: List<TrackSegment>,
        remoteSegments: List<TrackSegment>,
        idPrefix: String = "seg",
    ): List<TranscriptSegment> {
        val merged = ArrayList<TrackSegment>(localSegments.size + remoteSegments.size)
        // Defensive: only keep the segments that belong to their declared track so a
        // mislabeled caller can't smuggle a REMOTE row into the LOCAL list.
        localSegments.forEach { if (it.track == Track.LOCAL) merged.add(it) }
        remoteSegments.forEach { if (it.track == Track.REMOTE) merged.add(it) }

        merged.sortWith(SEGMENT_ORDER)

        return merged.mapIndexed { index, seg ->
            TranscriptSegment(
                id = "${idPrefix}_$index",
                startMillis = seg.startMillis.coerceAtLeast(0L),
                endMillis = seg.endMillis.coerceAtLeast(seg.startMillis),
                text = seg.text,
                speaker = labelFor(seg.track),
                confidence = seg.confidence,
            )
        }
    }

    /**
     * Builds the concatenated, searchable [com.glyphdialer.core.domain.model.Transcript.fullText]
     * body from already-aligned segments, prefixing each line with its speaker label
     * so the searchable text reads like a two-column dialog. Pure + testable.
     */
    fun renderDialog(segments: List<TranscriptSegment>): String =
        segments.joinToString(separator = "\n") { seg ->
            val who = seg.speaker?.let { "$it: " } ?: ""
            "$who${seg.text}"
        }

    companion object {
        const val LABEL_LOCAL = "You"
        const val LABEL_REMOTE = "Caller"

        /**
         * Deterministic total order over track segments: start time, then end time,
         * then LOCAL before REMOTE. Exposed so the test can assert the tie-break rule
         * directly.
         */
        val SEGMENT_ORDER: Comparator<TrackSegment> =
            compareBy<TrackSegment> { it.startMillis }
                .thenBy { it.endMillis }
                .thenBy { it.track.ordinal } // LOCAL(0) before REMOTE(1)
    }
}
