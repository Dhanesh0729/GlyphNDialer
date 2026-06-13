// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.Recording
import com.glyphdialer.core.domain.model.RecordingTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates call recording (§12). The impl lives in `:core:data` and drives the
 * [CallRecorder] peripheral, persisting [Recording] metadata in Room.
 *
 * HONESTY PRINCIPLE (§2.1/§12): [resolveTier] reports the highest tier achievable
 * for a given call; the UI MUST display the active tier and never present a
 * one-sided recording as a full two-way call.
 */
interface RecordingRepository {

    /** Whether a recording is currently in progress (mirrors [CallRecorder.isRecording]). */
    val isRecording: StateFlow<Boolean>

    /** The recording tier currently in effect, or UNAVAILABLE when not recording. */
    val activeTier: StateFlow<RecordingTier>

    /** Resolve the best tier achievable for [call] right now (honest, per §12). */
    suspend fun resolveTier(call: CallModel): RecordingTier

    /** Start recording [call]; returns the new [Recording] (with its tier). */
    suspend fun startRecording(call: CallModel): AppResult<Recording>

    /** Stop the in-progress recording; returns the finalized [Recording]. */
    suspend fun stopRecording(): AppResult<Recording?>

    /** Observe all stored recordings, newest first. */
    fun observeRecordings(): Flow<List<Recording>>

    /** One-shot fetch of a single recording by id. */
    suspend fun getRecording(id: String): AppResult<Recording?>

    /** Delete a recording and its audio body. */
    suspend fun deleteRecording(id: String): AppResult<Unit>

    /**
     * Export [id] to shared storage (MediaStore) after explicit user consent (§12).
     * Returns the exported content URI.
     */
    suspend fun export(id: String): AppResult<String>

    /** Purge recordings (and bodies) older than [cutoffMillis] for retention (§12). */
    suspend fun purgeOlderThan(cutoffMillis: Long): AppResult<Int>
}
