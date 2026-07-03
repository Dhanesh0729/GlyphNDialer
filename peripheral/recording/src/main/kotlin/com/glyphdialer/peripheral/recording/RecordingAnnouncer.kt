// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Plays an audible "this call is being recorded" announcement at recording start (§2.2).
 *
 * Prefers on-device Text-to-Speech; if TTS can't initialize (no engine / no voice data) it
 * falls back to a distinctive double beep via [ToneGenerator]. Audio is emitted on the
 * voice-call stream, so on the SPEAKER_TWO_WAY tier (call on the loudspeaker) the OTHER
 * PARTY hears it too — the non-covert path.
 *
 * HONESTY (§2.2): this class only ever ADDS an announcement. It never suppresses one the
 * OS mandates. Callers gate it on the user's "record without announcement" preference; the
 * disclaimer that the user is responsible for local recording law lives in Settings.
 */
@Singleton
class RecordingAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Deliver the announcement and suspend until it finishes (bounded by [timeoutMs]).
     * Never throws. Returns whether an audible announcement (speech OR tone) was delivered.
     */
    suspend fun announce(
        message: String = DEFAULT_MESSAGE,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Boolean {
        val spoke = withTimeoutOrNull(timeoutMs) {
            runCatching { speak(message) }.getOrDefault(false)
        } ?: false
        if (spoke) return true
        // TTS unavailable or timed out — still give an audible cue.
        return runCatching { beep() }.getOrDefault(false)
    }

    private suspend fun speak(message: String): Boolean = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Timber.w("TTS init failed (status=%d); falling back to tone", status)
                runCatching { engine?.shutdown() }
                if (cont.isActive) cont.resume(false)
                return@TextToSpeech
            }
            runCatching { engine.language = Locale.getDefault() }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    runCatching { engine.shutdown() }
                    if (cont.isActive) cont.resume(true)
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    runCatching { engine.shutdown() }
                    if (cont.isActive) cont.resume(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    runCatching { engine.shutdown() }
                    if (cont.isActive) cont.resume(false)
                }
            })
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            }
            val queued = engine.speak(message, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
            if (queued != TextToSpeech.SUCCESS) {
                runCatching { engine.shutdown() }
                if (cont.isActive) cont.resume(false)
            }
        }
        cont.invokeOnCancellation { runCatching { tts?.stop(); tts?.shutdown() } }
    }

    private fun beep(): Boolean = runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, TONE_VOLUME)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
        // The tone plays asynchronously; release shortly after it finishes.
        Handler(Looper.getMainLooper()).postDelayed(
            { runCatching { tone.release() } },
            (BEEP_MS + TONE_RELEASE_GRACE_MS).toLong(),
        )
        true
    }.getOrElse {
        Timber.w(it, "ToneGenerator announcement failed")
        false
    }

    private companion object {
        const val DEFAULT_MESSAGE = "This call is being recorded."
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val UTTERANCE_ID = "glyph-rec-announce"
        const val TONE_VOLUME = 80
        const val BEEP_MS = 500
        const val TONE_RELEASE_GRACE_MS = 200
    }
}
