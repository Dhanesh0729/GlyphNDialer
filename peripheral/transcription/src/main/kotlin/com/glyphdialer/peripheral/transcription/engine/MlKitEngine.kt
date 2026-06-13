// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription.engine

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Optional Google ML Kit on-device STT engine (§13), gated behind a flag.
 *
 * ML Kit does not (at time of writing) ship a general on-device speech-to-text
 * artifact via the public SDK, and we deliberately do NOT add a hard dependency on a
 * speculative one. Instead this engine REFLECTION-GUARDS the expected ML Kit STT
 * entry point: if the class is on the classpath at runtime it could be wired up; if
 * not, [isAvailable] is honestly false and both transcription paths return a clear
 * [AppResult.Failure] (§2.4) rather than faking output.
 *
 * To enable: add the real ML Kit STT dependency to this module's build file and
 * implement the [reflectivelyTranscribe] body against its API.
 */
class MlKitEngine @Inject constructor() : TranscriptionEngine {

    override val type: TranscriptionEngineType = TranscriptionEngineType.ML_KIT

    /** True only if the ML Kit STT class is actually present on the classpath. */
    override val isAvailable: Boolean
        get() = mlKitSttClassPresent()

    override suspend fun transcribeFile(path: String): AppResult<Transcript> {
        if (!isAvailable) return notBundled()
        // TODO(mlkit): implement against the real ML Kit STT API once the dependency
        // is added. Until then we never reach here because isAvailable gates it.
        return notBundled()
    }

    override fun liveCaptions(): Flow<String> {
        if (!isAvailable) {
            Timber.i("MlKitEngine.liveCaptions: ML Kit STT not on classpath — no captions")
        }
        return emptyFlow()
    }

    override fun release() = Unit

    private fun notBundled(): AppResult.Failure {
        val msg = "The ML Kit speech-to-text engine is not bundled in this build. " +
            "Add the ML Kit STT dependency and enable it in Settings, or use the " +
            "on-device Whisper / Android SpeechRecognizer engines."
        Timber.w(msg)
        return AppResult.Failure(error = MlKitUnavailableException(msg), message = msg)
    }

    private fun mlKitSttClassPresent(): Boolean = try {
        // Speculative FQN — adjust to the real artifact when it is added.
        Class.forName("com.google.mlkit.nl.speech.SpeechRecognizer")
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (t: Throwable) {
        Timber.v(t, "ML Kit STT presence probe failed")
        false
    }
}

/** Thrown when ML Kit STT is requested but the artifact isn't on the classpath. */
class MlKitUnavailableException(message: String) : Exception(message)
