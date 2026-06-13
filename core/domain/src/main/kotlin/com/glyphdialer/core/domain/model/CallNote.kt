// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * A free-text note attached to a call (RoomEntity: CallNoteEntity), shown in the
 * call history (§18 "In-call notes").
 */
data class CallNote(
    val id: String,
    /** The [CallModel.id] / call-log id this note belongs to. */
    val callId: String,
    val number: PhoneNumber,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
)
