// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Transcript
import kotlinx.coroutines.flow.Flow

/**
 * Coordinates transcription (§13). The impl lives in `:core:data` and drives the
 * selected [TranscriptionEngine], persisting [Transcript]s in Room.
 *
 * HONESTY PRINCIPLE (§2.4): transcribing the remote party on cellular inherits the
 * recording limits; the produced [Transcript.isLocalSideOnly] flag must be honored
 * by the UI.
 */
interface TranscriptRepository {

    /** Observe all stored transcripts, newest first. */
    fun observeTranscripts(): Flow<List<Transcript>>

    /** Full-text search of stored transcripts for [query] (§13). */
    suspend fun search(query: String): AppResult<List<Transcript>>

    /** Fetch a transcript by id. */
    suspend fun getTranscript(id: String): AppResult<Transcript?>

    /** Fetch the transcript for a given recording, if one exists. */
    suspend fun getForRecording(recordingId: String): AppResult<Transcript?>

    /** Transcribe the recording's audio file and persist the result (§13). */
    suspend fun transcribeRecording(recordingId: String): AppResult<Transcript>

    /** Live caption stream from the active call/engine (where audio access permits). */
    fun liveCaptions(): Flow<String>

    /** Delete a transcript by id. */
    suspend fun deleteTranscript(id: String): AppResult<Unit>

    /** Purge transcripts older than [cutoffMillis] for retention (§12). */
    suspend fun purgeOlderThan(cutoffMillis: Long): AppResult<Int>
}
