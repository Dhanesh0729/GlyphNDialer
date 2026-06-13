// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription.engine

import android.content.Context
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptSegment
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Whisper transcription engine — the privacy-first default (§13).
 *
 * The actual inference runs in native code: either the whisper.cpp JNI bridge or a
 * TFLite interpreter. That binary is NOT bundled in this repo, so this class defines
 * the BOUNDARY ([WhisperNative]) and degrades HONESTLY: when the native library
 * and/or the model file are absent, [isAvailable] is false and [transcribeFile]
 * returns an [AppResult.Failure] with concrete guidance instead of pretending (§2.4).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TODO(whisper-model): To enable real on-device transcription:
 *   1. Build/obtain a Whisper runtime and place it on the JNI path:
 *        • whisper.cpp:   compile libwhisper.so for each ABI →
 *          peripheral/transcription/src/main/jniLibs/<abi>/libwhisper.so
 *          and add a thin JNI wrapper exposing the `external fun`s declared in
 *          [WhisperNative] (System.loadLibrary("whisper")).
 *        • OR TFLite:     add the TFLite interpreter dependency and adapt
 *          [WhisperNative] to load/run the .tflite graph instead.
 *   2. Drop the quantized model (e.g. ggml-small-q5_1.bin / whisper.tflite) into
 *        peripheral/transcription/src/main/assets/whisper/<MODEL_ASSET_NAME>
 *      It is copied to internal storage on first use (assets aren't real files).
 *   3. Wire the bundled flag in TranscriptionModule (it already prefers Whisper
 *      when [isAvailable] is true, else falls back to SpeechRecognizer).
 * Keep the model OUT of git LFS-less history if large; document the download in
 * the app's FETCH/build script. Until then this engine is a graceful no-op.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    // Provided by TranscriptionModule (defaults to ReflectiveWhisperNative). Injected
    // rather than defaulted so Hilt is happy and tests can substitute a fake.
    private val native: WhisperNative,
) : TranscriptionEngine {

    override val type: TranscriptionEngineType = TranscriptionEngineType.ON_DEVICE_WHISPER

    private val nativeReady = AtomicBoolean(false)

    /** True only when BOTH the native runtime loaded AND the model asset is present. */
    override val isAvailable: Boolean
        get() = native.isLibraryLoaded && modelAssetExists()

    override suspend fun transcribeFile(path: String): AppResult<Transcript> {
        if (!native.isLibraryLoaded) {
            return modelUnavailable(
                "The on-device Whisper runtime (libwhisper.so / TFLite) is not bundled " +
                    "in this build. See WhisperEngine TODO(whisper-model) for the JNI/TFLite hookup.",
            )
        }
        val modelPath = ensureModelOnDisk()
            ?: return modelUnavailable(
                "No Whisper model is bundled. Drop a quantized model into " +
                    "assets/whisper/$MODEL_ASSET_NAME (see WhisperEngine TODO(whisper-model)).",
            )

        val audio = File(path)
        if (!audio.exists()) {
            return AppResult.Failure(
                error = java.io.FileNotFoundException(path),
                message = "Audio file to transcribe was not found: $path",
            )
        }

        // Inference is CPU-heavy → default dispatcher; model load touches disk → IO.
        return appResultOfSuspend {
            if (!nativeReady.getAndSet(true)) {
                withContext(ioDispatcher) { native.loadModel(modelPath) }
            }
            val raw = withContext(defaultDispatcher) {
                native.transcribe(audioPath = audio.absolutePath, languageTag = null)
            }
            raw.toTranscript(recordingId = null)
        }
    }

    /**
     * Live captions via Whisper are POSSIBLE (streaming chunks through the model) but
     * require the native streaming entry point. Until the runtime is bundled we honestly
     * emit nothing rather than block. SpeechRecognizer is the low-latency live path.
     */
    override fun liveCaptions(): Flow<String> {
        if (!isAvailable) {
            Timber.i("WhisperEngine.liveCaptions: runtime/model absent — no captions emitted")
        }
        // TODO(whisper-stream): when libwhisper exposes a streaming transcribe, bridge
        // it here as a callbackFlow chunking PCM frames. Use SpeechRecognizer meanwhile.
        return emptyFlow()
    }

    override fun release() {
        if (nativeReady.getAndSet(false)) {
            runCatching { native.release() }
                .onFailure { Timber.w(it, "WhisperNative.release failed") }
        }
    }

    private fun modelUnavailable(message: String): AppResult.Failure {
        Timber.w("Whisper unavailable: %s", message)
        return AppResult.Failure(error = WhisperModelUnavailableException(message), message = message)
    }

    private fun modelAssetExists(): Boolean = try {
        context.assets.list(MODEL_ASSET_DIR)?.contains(MODEL_ASSET_NAME) == true
    } catch (t: Throwable) {
        Timber.v(t, "Failed to list whisper assets")
        false
    }

    /** Copies the bundled model asset to internal storage once; returns its path or null. */
    private fun ensureModelOnDisk(): String? {
        if (!modelAssetExists()) return null
        val outFile = File(context.filesDir, "whisper/$MODEL_ASSET_NAME")
        if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath
        return try {
            outFile.parentFile?.mkdirs()
            context.assets.open("$MODEL_ASSET_DIR/$MODEL_ASSET_NAME").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
        } catch (t: Throwable) {
            Timber.w(t, "Failed to stage Whisper model on disk")
            null
        }
    }

    companion object {
        const val MODEL_ASSET_DIR = "whisper"

        /** Expected bundled model filename; change to match the model you ship. */
        const val MODEL_ASSET_NAME = "ggml-small-q5_1.bin"
    }
}

/** Thrown when Whisper inference is requested but the runtime/model isn't bundled. */
class WhisperModelUnavailableException(message: String) : Exception(message)

/**
 * The JNI/TFLite BOUNDARY for on-device Whisper. Implementations bridge to native
 * code. Kept as an interface so tests can substitute a fake and the production path
 * can be swapped (whisper.cpp ↔ TFLite) without touching [WhisperEngine].
 */
interface WhisperNative {
    /** Whether the underlying native library successfully loaded at process start. */
    val isLibraryLoaded: Boolean

    /** Load/initialize the model from an on-disk path. Throws on failure. */
    fun loadModel(modelPath: String)

    /** Run inference over the audio file, returning timestamped segments. */
    fun transcribe(audioPath: String, languageTag: String?): WhisperResult

    /** Free the model + any native context. */
    fun release()
}

/** Raw native inference output, decoupled from the domain [Transcript]. */
data class WhisperResult(
    val language: String?,
    val segments: List<WhisperSegment>,
) {
    fun toTranscript(recordingId: String?): Transcript {
        val domainSegments = segments.mapIndexed { i, s ->
            TranscriptSegment(
                id = "wseg_${i}_${UUID.randomUUID().toString().take(8)}",
                startMillis = s.startMillis,
                endMillis = s.endMillis,
                text = s.text.trim(),
                speaker = null, // single-file Whisper has no diarization; see SpeakerLabeler.
                confidence = s.confidence,
            )
        }
        return Transcript(
            id = "tr_${UUID.randomUUID()}",
            recordingId = recordingId,
            language = language,
            fullText = domainSegments.joinToString(" ") { it.text }.trim(),
            segments = domainSegments,
            engine = TranscriptionEngineType.ON_DEVICE_WHISPER,
            createdAtMillis = System.currentTimeMillis(),
        )
    }
}

/** One Whisper segment in the native result. */
data class WhisperSegment(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
    val confidence: Float? = null,
)

/**
 * Default [WhisperNative] that probes for the native library WITHOUT a hard link
 * error: it attempts `System.loadLibrary("whisper")` in a guarded block. Because the
 * `.so` is not bundled, [isLibraryLoaded] is false here and every inference path is
 * short-circuited upstream by [WhisperEngine]. Replace/augment with real
 * `external fun` declarations once libwhisper is on the JNI path (see TODO above).
 */
object ReflectiveWhisperNative : WhisperNative {

    override val isLibraryLoaded: Boolean = tryLoadLibrary()

    override fun loadModel(modelPath: String) {
        check(isLibraryLoaded) { "libwhisper not loaded" }
        // TODO(whisper-model): call into the native model-init entry point, e.g.
        //   external fun nativeInit(modelPath: String): Long  // returns a context ptr
        throw WhisperModelUnavailableException(
            "Whisper native bridge present but model-init JNI is not yet implemented.",
        )
    }

    override fun transcribe(audioPath: String, languageTag: String?): WhisperResult {
        // TODO(whisper-model): call native streaming/full transcribe; map to WhisperResult.
        throw WhisperModelUnavailableException("Whisper native transcribe is not yet implemented.")
    }

    override fun release() { /* no-op until native context exists */ }

    private fun tryLoadLibrary(): Boolean = try {
        System.loadLibrary("whisper")
        true
    } catch (_: UnsatisfiedLinkError) {
        // Expected when the .so isn't bundled — this is the honest "not available" path.
        false
    } catch (t: Throwable) {
        Timber.v(t, "Unexpected error probing libwhisper")
        false
    }
}
