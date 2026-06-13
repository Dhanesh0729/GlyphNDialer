// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import kotlinx.coroutines.flow.Flow

/**
 * Peripheral contract for a pluggable speech-to-text engine (§13). Multiple impls
 * (on-device Whisper, Android SpeechRecognizer, ML Kit, cloud) live in
 * `:peripheral:transcription`; the user picks one in Settings.
 *
 * HONESTY PRINCIPLE (§2.4): [liveCaptions] reflects only the audio the engine can
 * legally/technically access — full for VoIP, local-mic-only on cellular without
 * call-audio. Engines must not fabricate the remote side. [isAvailable] reports
 * runtime readiness (model present, recognizer installed).
 */
interface TranscriptionEngine {

    /** Which engine this is, for honest UI labeling and the user's preference match. */
    val type: TranscriptionEngineType

    /** Whether this engine is usable right now (model downloaded / recognizer present). */
    val isAvailable: Boolean

    /** Transcribe the audio file at [path] and return a timestamp-aligned [Transcript]. */
    suspend fun transcribeFile(path: String): AppResult<Transcript>

    /**
     * Live caption stream while a call is active. Emits incremental partial text.
     * Cold flow: starts capturing when collected, stops on cancellation.
     */
    fun liveCaptions(): Flow<String>

    /** Release any held resources / unload the model. Safe to call when idle. */
    fun release()
}
