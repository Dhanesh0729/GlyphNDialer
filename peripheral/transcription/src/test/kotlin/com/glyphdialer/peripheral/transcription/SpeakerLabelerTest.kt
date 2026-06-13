// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription

import com.glyphdialer.peripheral.transcription.SpeakerLabeler.Track
import com.glyphdialer.peripheral.transcription.SpeakerLabeler.TrackSegment
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure 2-track speaker labeling + segment alignment logic
 * (CONVENTIONS.md §11). No Android, no coroutines, no mocks — segments in,
 * time-ordered + labeled transcript out.
 */
class SpeakerLabelerTest {

    private val labeler = SpeakerLabeler()

    private fun local(start: Long, end: Long, text: String, conf: Float? = null) =
        TrackSegment(Track.LOCAL, start, end, text, conf)

    private fun remote(start: Long, end: Long, text: String, conf: Float? = null) =
        TrackSegment(Track.REMOTE, start, end, text, conf)

    @Test
    fun `interleaves both tracks in start-time order`() {
        val result = labeler.align(
            localSegments = listOf(
                local(0, 1_000, "Hi there"),
                local(2_500, 3_200, "How are you"),
            ),
            remoteSegments = listOf(
                remote(1_100, 2_400, "Hey, good to hear from you"),
            ),
        )

        assertThat(result.map { it.text }).containsExactly(
            "Hi there",
            "Hey, good to hear from you",
            "How are you",
        ).inOrder()
    }

    @Test
    fun `applies correct speaker labels per track`() {
        val result = labeler.align(
            localSegments = listOf(local(0, 500, "Yes")),
            remoteSegments = listOf(remote(600, 900, "No")),
        )

        assertThat(result[0].speaker).isEqualTo(SpeakerLabeler.LABEL_LOCAL)
        assertThat(result[1].speaker).isEqualTo(SpeakerLabeler.LABEL_REMOTE)
    }

    @Test
    fun `breaks start-time ties with LOCAL before REMOTE`() {
        val result = labeler.align(
            localSegments = listOf(local(1_000, 1_500, "local")),
            remoteSegments = listOf(remote(1_000, 1_500, "remote")),
        )

        assertThat(result.map { it.speaker }).containsExactly(
            SpeakerLabeler.LABEL_LOCAL,
            SpeakerLabeler.LABEL_REMOTE,
        ).inOrder()
    }

    @Test
    fun `generates stable unique ids with prefix and index`() {
        val result = labeler.align(
            localSegments = listOf(local(0, 100, "a"), local(200, 300, "b")),
            remoteSegments = listOf(remote(100, 150, "c")),
            idPrefix = "call42",
        )

        assertThat(result.map { it.id }).containsExactly("call42_0", "call42_1", "call42_2").inOrder()
        assertThat(result.map { it.id }.toSet()).hasSize(3)
    }

    @Test
    fun `preserves confidence and clamps negative or inverted timestamps`() {
        val result = labeler.align(
            localSegments = listOf(local(-50, -10, "garbage timing", conf = 0.42f)),
            remoteSegments = emptyList(),
        )

        val seg = result.single()
        assertThat(seg.startMillis).isEqualTo(0L)            // negative start clamped to 0
        assertThat(seg.endMillis).isAtLeast(seg.startMillis) // end never before start
        assertThat(seg.confidence).isEqualTo(0.42f)
    }

    @Test
    fun `ignores segments declared on the wrong track`() {
        // A REMOTE segment smuggled into the local list must be dropped.
        val result = labeler.align(
            localSegments = listOf(local(0, 100, "ok"), remote(50, 80, "smuggled")),
            remoteSegments = listOf(remote(200, 300, "legit")),
        )

        assertThat(result.map { it.text }).containsExactly("ok", "legit").inOrder()
    }

    @Test
    fun `empty input yields empty transcript`() {
        assertThat(labeler.align(emptyList(), emptyList())).isEmpty()
    }

    @Test
    fun `renderDialog prefixes each line with its speaker label`() {
        val aligned = labeler.align(
            localSegments = listOf(local(0, 500, "Hello")),
            remoteSegments = listOf(remote(600, 1_000, "Hi back")),
        )

        val dialog = labeler.renderDialog(aligned)

        assertThat(dialog).isEqualTo("You: Hello\nCaller: Hi back")
    }

    @Test
    fun `renderDialog omits prefix when a segment has no speaker`() {
        // Single-track (no diarization) segments leave speaker null and must not get a colon.
        val noSpeaker = com.glyphdialer.core.domain.model.TranscriptSegment(
            id = "x",
            startMillis = 0,
            endMillis = 100,
            text = "bare line",
            speaker = null,
        )

        assertThat(labeler.renderDialog(listOf(noSpeaker))).isEqualTo("bare line")
    }
}
