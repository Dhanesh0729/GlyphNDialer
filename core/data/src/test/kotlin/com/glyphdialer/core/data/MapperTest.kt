// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data

import com.glyphdialer.core.data.db.entity.TranscriptEntity
import com.glyphdialer.core.data.db.entity.TranscriptSegmentEntity
import com.glyphdialer.core.data.db.entity.TranscriptWithSegments
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.data.mapper.toEntity
import com.glyphdialer.core.data.mapper.toSegmentEntities
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.Recording
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptSegment
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Entity<->domain mapper round-trips (CONVENTIONS.md §11). */
class MapperTest {

    @Test
    fun `recording round-trips through entity preserving tier and announced`() {
        val recording = Recording(
            id = "rec-1",
            callId = "call-1",
            number = PhoneNumber(raw = "+14155552671", normalized = "+14155552671", formatted = "+1 415-555-2671"),
            contactName = "Ada",
            startedAtMillis = 1_000L,
            durationMillis = 42_000L,
            tier = RecordingTier.LOCAL_ONE_SIDED,
            filePath = "/data/rec-1.m4a",
            transcriptId = "t-1",
            hasTranscript = true,
            announced = false,
        )

        val restored = recording.toEntity().toDomain()

        assertThat(restored.id).isEqualTo("rec-1")
        assertThat(restored.callId).isEqualTo("call-1")
        assertThat(restored.number.raw).isEqualTo("+14155552671")
        assertThat(restored.number.normalized).isEqualTo("+14155552671")
        assertThat(restored.contactName).isEqualTo("Ada")
        assertThat(restored.durationMillis).isEqualTo(42_000L)
        assertThat(restored.tier).isEqualTo(RecordingTier.LOCAL_ONE_SIDED)
        assertThat(restored.isTwoWay).isFalse()
        assertThat(restored.transcriptId).isEqualTo("t-1")
        assertThat(restored.hasTranscript).isTrue()
        assertThat(restored.announced).isFalse()
    }

    @Test
    fun `transcript maps to entity plus ordered segment entities`() {
        val transcript = Transcript(
            id = "t-1",
            recordingId = "rec-1",
            language = "en",
            fullText = "hello world",
            segments = listOf(
                TranscriptSegment(id = "s-0", startMillis = 0, endMillis = 500, text = "hello", confidence = 0.9f),
                TranscriptSegment(id = "s-1", startMillis = 500, endMillis = 1000, text = "world", speaker = "A"),
            ),
            engine = TranscriptionEngineType.ON_DEVICE_WHISPER,
            createdAtMillis = 2_000L,
            isLocalSideOnly = true,
        )

        val entity = transcript.toEntity()
        val segments = transcript.toSegmentEntities()

        assertThat(entity.id).isEqualTo("t-1")
        assertThat(entity.isLocalSideOnly).isTrue()
        assertThat(entity.engine).isEqualTo(TranscriptionEngineType.ON_DEVICE_WHISPER)
        assertThat(segments).hasSize(2)
        assertThat(segments[0].ordinal).isEqualTo(0)
        assertThat(segments[1].ordinal).isEqualTo(1)
        assertThat(segments[1].speaker).isEqualTo("A")
    }

    @Test
    fun `transcript relation maps back to domain with segments sorted by ordinal`() {
        val entity = TranscriptEntity(
            id = "t-2",
            recordingId = "rec-2",
            language = null,
            fullText = "one two",
            engine = TranscriptionEngineType.ML_KIT,
            createdAtMillis = 3_000L,
            isLocalSideOnly = false,
        )
        // Deliberately out of order to verify the mapper sorts by ordinal.
        val segments = listOf(
            TranscriptSegmentEntity("s-b", "t-2", 500, 1000, "two", null, null, ordinal = 1),
            TranscriptSegmentEntity("s-a", "t-2", 0, 500, "one", null, null, ordinal = 0),
        )

        val domain = TranscriptWithSegments(entity, segments).toDomain()

        assertThat(domain.id).isEqualTo("t-2")
        assertThat(domain.engine).isEqualTo(TranscriptionEngineType.ML_KIT)
        assertThat(domain.segments.map { it.text }).containsExactly("one", "two").inOrder()
        assertThat(domain.isLocalSideOnly).isFalse()
    }
}
