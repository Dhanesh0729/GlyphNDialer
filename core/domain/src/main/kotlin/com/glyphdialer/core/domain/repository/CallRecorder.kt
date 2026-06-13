// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.RecordingTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Peripheral contract for the actual audio capture (§12). Impl lives in
 * `:peripheral:recording`; coordinated by [RecordingRepository].
 *
 * HONESTY PRINCIPLE (§2.1): [supportedTier] reports the highest tier the recorder
 * can actually deliver on this device/call. It returns
 * [RecordingTier.LOCAL_ONE_SIDED] or [RecordingTier.UNAVAILABLE] on stock Android,
 * never falsely claiming two-way capture.
 */
interface CallRecorder {

    /** True while capture is in progress. */
    val isRecording: StateFlow<Boolean>

    /** Live amplitude (0f..1f) of the captured audio, for waveform/Glyph mirroring. */
    val amplitude: Flow<Float>

    /**
     * The highest recording tier this DEVICE can deliver as a baseline, independent
     * of any specific call (honest, §12). On stock Android this is at best
     * [RecordingTier.LOCAL_ONE_SIDED].
     */
    fun supportedTier(): RecordingTier

    /**
     * The highest tier achievable for a SPECIFIC [call] right now — refines
     * [supportedTier] using call context (e.g. an in-app VoIP call can reach
     * [RecordingTier.VOIP_TWO_WAY] even when the device baseline is local-only).
     */
    fun supportedTierFor(call: CallModel): RecordingTier

    /**
     * Begin capturing [call]. Returns the absolute path of the file being written,
     * or a [AppResult.Failure] if recording is unavailable.
     */
    suspend fun start(call: CallModel): AppResult<String>

    /** Stop capture and finalize the file. */
    suspend fun stop(): AppResult<Unit>

    /** Release any held audio resources. Safe to call when idle. */
    fun release()
}
