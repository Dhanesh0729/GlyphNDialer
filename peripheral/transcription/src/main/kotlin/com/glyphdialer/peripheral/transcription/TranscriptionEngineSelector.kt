// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription

import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import com.glyphdialer.peripheral.transcription.engine.AndroidSpeechRecognizerEngine
import com.glyphdialer.peripheral.transcription.engine.CloudSttEngine
import com.glyphdialer.peripheral.transcription.engine.MlKitEngine
import com.glyphdialer.peripheral.transcription.engine.WhisperEngine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Maps a user-selected [TranscriptionEngineType] to its [TranscriptionEngine] impl
 * (§13). Engines are injected as [Provider]s so heavy ones (Whisper) are only
 * instantiated when actually requested.
 *
 * The selector NEVER fabricates capability: if the requested engine reports
 * `isAvailable == false`, callers can detect it via [engineFor] returning an engine
 * whose `isAvailable` is false, or use [bestAvailableOrNull] to fall back honestly.
 */
@Singleton
class TranscriptionEngineSelector @Inject constructor(
    private val whisper: Provider<WhisperEngine>,
    private val speechRecognizer: Provider<AndroidSpeechRecognizerEngine>,
    private val mlKit: Provider<MlKitEngine>,
    private val cloud: Provider<CloudSttEngine>,
) {

    /**
     * Returns the engine for [type], or null for [TranscriptionEngineType.NONE]
     * (transcription disabled). Does NOT check availability — that is the engine's own
     * honest `isAvailable` flag the caller/UI should consult.
     */
    fun engineFor(type: TranscriptionEngineType): TranscriptionEngine? = when (type) {
        TranscriptionEngineType.ON_DEVICE_WHISPER -> whisper.get()
        TranscriptionEngineType.ANDROID_SPEECH_RECOGNIZER -> speechRecognizer.get()
        TranscriptionEngineType.ML_KIT -> mlKit.get()
        TranscriptionEngineType.CLOUD -> cloud.get()
        TranscriptionEngineType.NONE -> null
    }

    /**
     * Returns the engine for [preferred] if it is available; otherwise falls back, in
     * priority order, to the first other available engine. Returns null only when
     * nothing is available (UI must then say transcription is unavailable, §2.4).
     */
    fun resolveAvailable(preferred: TranscriptionEngineType): TranscriptionEngine? {
        engineFor(preferred)?.takeIf { it.isAvailable }?.let { return it }
        Timber.i("Preferred engine %s unavailable; searching for a fallback", preferred)
        return FALLBACK_ORDER
            .asSequence()
            .mapNotNull { engineFor(it) }
            .firstOrNull { it.isAvailable }
            ?.also { Timber.i("Falling back to %s", it.type) }
    }

    /** The single best available engine regardless of preference, or null if none. */
    fun bestAvailableOrNull(): TranscriptionEngine? =
        FALLBACK_ORDER.asSequence().mapNotNull { engineFor(it) }.firstOrNull { it.isAvailable }

    companion object {
        /**
         * Privacy-first fallback order: on-device Whisper, then on-device
         * SpeechRecognizer, then ML Kit (on-device), then cloud last (sends audio off
         * device). NONE is intentionally excluded.
         */
        val FALLBACK_ORDER: List<TranscriptionEngineType> = listOf(
            TranscriptionEngineType.ON_DEVICE_WHISPER,
            TranscriptionEngineType.ANDROID_SPEECH_RECOGNIZER,
            TranscriptionEngineType.ML_KIT,
            TranscriptionEngineType.CLOUD,
        )
    }
}
