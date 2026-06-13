// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription.di

import com.glyphdialer.core.domain.repository.TranscriptionEngine
import com.glyphdialer.peripheral.transcription.TranscriptionEngineSelector
import com.glyphdialer.peripheral.transcription.engine.AndroidSpeechRecognizerEngine
import com.glyphdialer.peripheral.transcription.engine.ReflectiveWhisperNative
import com.glyphdialer.peripheral.transcription.engine.WhisperEngine
import com.glyphdialer.peripheral.transcription.engine.WhisperNative
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the app-wide DEFAULT [TranscriptionEngine] (§13). Consumers that just
 * want "the sensible engine for this device" inject `@DefaultTranscriptionEngine
 * TranscriptionEngine`; consumers honoring a live user preference should inject the
 * [TranscriptionEngineSelector] instead and pass the chosen [com.glyphdialer.core.domain.model.TranscriptionEngineType].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultTranscriptionEngine

/**
 * Hilt wiring for `:peripheral:transcription`.
 *
 * Provides:
 *  - the [TranscriptionEngineSelector] (it is `@Inject`-constructable, so this is just
 *    the documented home for the binding; Hilt would supply it regardless), and
 *  - the DEFAULT [TranscriptionEngine]: Whisper when its model is bundled + the native
 *    runtime loaded (privacy-first, §13), otherwise the Android SpeechRecognizer for
 *    low-latency captions. This decision is HONEST — it inspects real availability and
 *    never returns an engine that pretends to work (§2.4).
 */
@Module
@InstallIn(SingletonComponent::class)
object TranscriptionModule {

    /**
     * The JNI/TFLite boundary for Whisper. Defaults to [ReflectiveWhisperNative],
     * which probes for `libwhisper.so` via a guarded `System.loadLibrary` and reports
     * `isLibraryLoaded == false` honestly when the binary isn't bundled (§2.4). Swap
     * this provider for a real `external fun` bridge once the runtime is on the JNI path.
     */
    @Provides
    @Singleton
    fun provideWhisperNative(): WhisperNative = ReflectiveWhisperNative

    @Provides
    @Singleton
    @DefaultTranscriptionEngine
    fun provideDefaultTranscriptionEngine(
        whisper: WhisperEngine,
        speechRecognizer: AndroidSpeechRecognizerEngine,
    ): TranscriptionEngine =
        if (whisper.isAvailable) {
            Timber.i("Default transcription engine: on-device Whisper (model bundled)")
            whisper
        } else {
            Timber.i(
                "Default transcription engine: SpeechRecognizer (Whisper model not " +
                    "bundled; available=%b)",
                speechRecognizer.isAvailable,
            )
            speechRecognizer
        }

    /**
     * UNQUALIFIED [TranscriptionEngine] binding for the common case (§13). Consumers
     * that just need "the device's transcription engine" — [com.glyphdialer.core.data.repository.TranscriptRepositoryImpl],
     * [com.glyphdialer.core.data.repository.CapabilityRepositoryImpl], and the in-call
     * captions ViewModel — inject an unqualified [TranscriptionEngine]; without this
     * binding Hilt has only the [DefaultTranscriptionEngine]-qualified one and fails to
     * resolve them. Delegates to the same honest default selection.
     */
    @Provides
    @Singleton
    fun provideTranscriptionEngine(
        @DefaultTranscriptionEngine default: TranscriptionEngine,
    ): TranscriptionEngine = default
}
