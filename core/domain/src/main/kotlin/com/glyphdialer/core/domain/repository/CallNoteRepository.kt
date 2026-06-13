// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallNote
import kotlinx.coroutines.flow.Flow

/** In-call/post-call notes attached to call records (RoomEntity: CallNoteEntity, §18). */
interface CallNoteRepository {

    /** Observe notes for a given [callId]. */
    fun observeForCall(callId: String): Flow<List<CallNote>>

    /** Observe notes attached to any call with [number] (history view). */
    fun observeForNumber(number: String): Flow<List<CallNote>>

    /** Create a note and return its new id. */
    suspend fun addNote(callId: String, number: String, text: String): AppResult<String>

    /** Update an existing note's text. */
    suspend fun updateNote(id: String, text: String): AppResult<Unit>

    /** Delete the note [id]. */
    suspend fun deleteNote(id: String): AppResult<Unit>
}
