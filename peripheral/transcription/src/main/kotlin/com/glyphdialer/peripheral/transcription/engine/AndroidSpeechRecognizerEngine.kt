// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription.engine

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * [TranscriptionEngine] backed by [android.speech.SpeechRecognizer] (§13).
 *
 * Strength: very low-latency LIVE captions of the **local microphone**. Weakness:
 * the platform recognizer cannot be pointed at an arbitrary audio file in a way that
 * is reliable across OEMs, and on stock Android it only hears the local side (§2.4) —
 * so [transcribeFile] is best-effort and clearly labelled, and live captions are
 * honestly local-side-only unless the caller wires in VoIP track audio elsewhere.
 *
 * The recognizer object is single-threaded and must be created/started on the main
 * thread, which is why [liveCaptions] hops to the main dispatcher inside the
 * callbackFlow producer.
 */
class AndroidSpeechRecognizerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(GlyphDispatcher.MAIN) private val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TranscriptionEngine {

    override val type: TranscriptionEngineType = TranscriptionEngineType.ANDROID_SPEECH_RECOGNIZER

    /**
     * True only when an on-device recognition service is actually installed. We check
     * [SpeechRecognizer.isRecognitionAvailable] (and the on-device variant where the
     * API exists) rather than assuming — staying honest per §2.4.
     */
    override val isAvailable: Boolean
        get() = try {
            val basic = SpeechRecognizer.isRecognitionAvailable(context)
            basic || isOnDeviceRecognitionAvailable()
        } catch (t: Throwable) {
            Timber.w(t, "isRecognitionAvailable threw; reporting recognizer unavailable")
            false
        }

    /**
     * Best-effort file transcription. SpeechRecognizer has no public, OEM-portable API
     * for transcribing a saved file, so we are HONEST and return a [AppResult.Failure]
     * with guidance rather than faking it. Use Whisper for offline file transcription.
     */
    override suspend fun transcribeFile(path: String): AppResult<Transcript> =
        withContext(ioDispatcher) {
            Timber.i("transcribeFile not supported by SpeechRecognizer (path=%s)", path)
            AppResult.Failure(
                error = UnsupportedOperationException("SpeechRecognizer file transcription"),
                message = "Android SpeechRecognizer transcribes the live microphone, not " +
                    "saved files. Select the on-device Whisper engine in Settings to " +
                    "transcribe recordings offline.",
            )
        }

    /**
     * Live captions of the local microphone as a cold [Flow]. Emits incremental
     * partial text while the recognizer runs, and auto-restarts after each end-of-
     * speech so a whole call is captioned (SpeechRecognizer stops after each utterance).
     */
    override fun liveCaptions(): Flow<String> = callbackFlow {
        if (!isAvailable) {
            Timber.w("liveCaptions requested but no recognizer is available")
            close(IllegalStateException("No on-device speech recognizer is available."))
            return@callbackFlow
        }

        // The recognizer must be created and driven on the main thread.
        val recognizer = withContext(mainDispatcher) {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = buildRecognizerIntent()

        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                emitBest(results, isPartial = false)
                // Utterance ended — restart so captions continue for the whole call.
                restart(recognizer, intent)
            }

            override fun onPartialResults(partialResults: Bundle?) =
                emitBest(partialResults, isPartial = true)

            override fun onError(error: Int) {
                // Transient errors (no match / timeout) just mean a pause; keep going.
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> {
                        Timber.v("recognizer transient error %d; restarting", error)
                        restart(recognizer, intent)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        Timber.v("recognizer busy; will retry on next utterance")
                    }
                    else -> {
                        Timber.w("recognizer fatal error %d; closing caption stream", error)
                        close(SpeechRecognitionException(error))
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            private fun emitBest(bundle: Bundle?, isPartial: Boolean) {
                val text = bundle
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) {
                    trySendBlocking(text)
                        .onFailure { Timber.v(it, "caption dropped (%s)", if (isPartial) "partial" else "final") }
                }
            }
        }

        withContext(mainDispatcher) {
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(intent)
        }
        Timber.d("live captions started (SpeechRecognizer, local-side only)")

        awaitClose {
            // Tear down on the main thread; cancel() then destroy() releases the engine.
            Timber.d("live captions stopping (SpeechRecognizer)")
            runCatching {
                recognizer.setRecognitionListener(null)
                recognizer.cancel()
                recognizer.destroy()
            }.onFailure { Timber.w(it, "error destroying recognizer") }
        }
    }.flowOn(mainDispatcher)

    /** SpeechRecognizer holds no long-lived resources here; per-stream cleanup is in [liveCaptions]. */
    override fun release() {
        Timber.v("AndroidSpeechRecognizerEngine.release() — no persistent resources")
    }

    private fun restart(recognizer: SpeechRecognizer, intent: android.content.Intent) {
        runCatching {
            recognizer.cancel()
            recognizer.startListening(intent)
        }.onFailure { Timber.v(it, "recognizer restart failed") }
    }

    private fun buildRecognizerIntent(): android.content.Intent =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prefer offline so captions stay private + work without network (§2 privacy).
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    /**
     * On API 31+ the platform exposes [SpeechRecognizer.isOnDeviceRecognitionAvailable].
     * We probe it via reflection so this module still compiles against lower API floors
     * and never crashes on devices/ROMs that omit it.
     */
    private fun isOnDeviceRecognitionAvailable(): Boolean = try {
        val method = SpeechRecognizer::class.java
            .getMethod("isOnDeviceRecognitionAvailable", Context::class.java)
        (method.invoke(null, context) as? Boolean) ?: false
    } catch (_: NoSuchMethodException) {
        false
    } catch (t: Throwable) {
        Timber.v(t, "isOnDeviceRecognitionAvailable probe failed")
        false
    }
}

/** Thrown when the platform recognizer reports a fatal [SpeechRecognizer] error code. */
class SpeechRecognitionException(val errorCode: Int) :
    Exception("SpeechRecognizer fatal error code $errorCode")
