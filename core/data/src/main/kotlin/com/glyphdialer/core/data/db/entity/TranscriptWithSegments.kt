// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relation aggregating a [TranscriptEntity] with its ordered
 * [TranscriptSegmentEntity] rows. Mapped to the domain [com.glyphdialer.core.domain.model.Transcript].
 */
data class TranscriptWithSegments(
    @Embedded val transcript: TranscriptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transcriptId",
    )
    val segments: List<TranscriptSegmentEntity>,
)
