// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.mapper

import com.glyphdialer.core.data.db.entity.TranscriptEntity
import com.glyphdialer.core.data.db.entity.TranscriptSegmentEntity
import com.glyphdialer.core.data.db.entity.TranscriptWithSegments
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptSegment

/** Mappers between the Room [TranscriptWithSegments] relation and the domain [Transcript]. */
fun TranscriptWithSegments.toDomain(): Transcript = Transcript(
    id = transcript.id,
    recordingId = transcript.recordingId,
    language = transcript.language,
    fullText = transcript.fullText,
    segments = segments
        .sortedBy { it.ordinal }
        .map { it.toDomain() },
    engine = transcript.engine,
    createdAtMillis = transcript.createdAtMillis,
    isLocalSideOnly = transcript.isLocalSideOnly,
)

fun TranscriptSegmentEntity.toDomain(): TranscriptSegment = TranscriptSegment(
    id = id,
    startMillis = startMillis,
    endMillis = endMillis,
    text = text,
    speaker = speaker,
    confidence = confidence,
)

fun Transcript.toEntity(): TranscriptEntity = TranscriptEntity(
    id = id,
    recordingId = recordingId,
    language = language,
    fullText = fullText,
    engine = engine,
    createdAtMillis = createdAtMillis,
    isLocalSideOnly = isLocalSideOnly,
)

fun Transcript.toSegmentEntities(): List<TranscriptSegmentEntity> =
    segments.mapIndexed { index, segment ->
        TranscriptSegmentEntity(
            id = segment.id,
            transcriptId = id,
            startMillis = segment.startMillis,
            endMillis = segment.endMillis,
            text = segment.text,
            speaker = segment.speaker,
            confidence = segment.confidence,
            ordinal = index,
        )
    }
