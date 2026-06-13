// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.mapper

import com.glyphdialer.core.data.db.entity.RecordingEntity
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.Recording

/**
 * Mappers between [RecordingEntity] and the domain [Recording].
 *
 * The domain [PhoneNumber] is decomposed into raw + normalized columns; [formatted]
 * is a display concern recomputed by the formatter and is NOT persisted, so it is
 * rehydrated to [PhoneNumber.raw] here (callers re-format for display).
 */
fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    callId = callId,
    number = PhoneNumber(raw = rawNumber, normalized = normalizedNumber, formatted = rawNumber),
    contactName = contactName,
    startedAtMillis = startedAtMillis,
    durationMillis = durationMillis,
    tier = tier,
    filePath = filePath,
    transcriptId = transcriptId,
    hasTranscript = hasTranscript,
    announced = announced,
)

fun Recording.toEntity(): RecordingEntity = RecordingEntity(
    id = id,
    callId = callId,
    rawNumber = number.raw,
    normalizedNumber = number.normalized,
    contactName = contactName,
    startedAtMillis = startedAtMillis,
    durationMillis = durationMillis,
    tier = tier,
    filePath = filePath,
    transcriptId = transcriptId,
    hasTranscript = hasTranscript,
    announced = announced,
)
