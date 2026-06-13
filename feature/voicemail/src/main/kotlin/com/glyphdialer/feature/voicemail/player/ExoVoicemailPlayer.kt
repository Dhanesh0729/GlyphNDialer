// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.sin

/**
 * Media3 [ExoPlayer]-backed implementation of [VoicemailPlayer].
 *
 * Owns a single reusable ExoPlayer (one voicemail plays at a time). A position
 * poller running on [mainScope] pumps the current position into [state] while
 * playing, and synthesizes a light amplitude envelope so the dot-matrix scrubber
 * waveform reads as "playing" without us decoding PCM for short VVM clips.
 *
 * HONESTY (CONVENTIONS.md §9): the amplitude envelope is explicitly synthetic — it
 * mirrors the playhead, not real audio levels — and is only used for the scrubber
 * decoration, never presented as a true voiceprint. The transcription panel (real
 * data) is what conveys content.
 *
 * Threading: ExoPlayer must be created and driven on a single application looper
 * thread. We build it on the application main looper and require callers to invoke
 * methods from the main dispatcher (the ViewModel does).
 */
class ExoVoicemailPlayer(
    private val context: Context,
    /** Main-affine scope that drives the position poller (app-lifetime singleton). */
    private val mainScope: CoroutineScope,
) : VoicemailPlayer {

    private val _state = MutableStateFlow(PlayerSnapshot())
    override val state: StateFlow<PlayerSnapshot> = _state.asStateFlow()

    private var exo: ExoPlayer? = null
    private var positionJob: Job? = null

    /** URI cache so resuming the same item doesn't rebuild the media source. */
    private var loadedUri: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionPump() else stopPositionPump()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exo ?: return
            val buffering = playbackState == Player.STATE_BUFFERING
            val duration = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
            _state.update {
                it.copy(
                    isBuffering = buffering,
                    durationMillis = duration,
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                // Reset to the start, paused, so the row can be replayed.
                player.seekTo(0L)
                player.playWhenReady = false
                _state.update { it.copy(isPlaying = false, positionMillis = 0L) }
                stopPositionPump()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Voicemail playback error")
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Playback failed",
                )
            }
            stopPositionPump()
        }
    }

    private fun ensurePlayer(): ExoPlayer =
        exo ?: ExoPlayer.Builder(context).build().also {
            it.addListener(listener)
            exo = it
        }

    override fun play(id: Long, audioUri: String) {
        val player = ensurePlayer()
        val sameItem = _state.value.voicemailId == id && loadedUri == audioUri
        if (!sameItem) {
            player.setMediaItem(MediaItem.fromUri(audioUri))
            player.prepare()
            loadedUri = audioUri
            _state.update {
                PlayerSnapshot(
                    voicemailId = id,
                    isBuffering = true,
                )
            }
        }
        player.playWhenReady = true
    }

    override fun pause() {
        exo?.playWhenReady = false
    }

    override fun resume() {
        exo?.let { if (it.mediaItemCount > 0) it.playWhenReady = true }
    }

    override fun seekTo(positionMillis: Long) {
        val player = exo ?: return
        val clamped = positionMillis.coerceAtLeast(0L)
        player.seekTo(clamped)
        _state.update { it.copy(positionMillis = clamped) }
    }

    override fun stop() {
        exo?.let {
            it.stop()
            it.clearMediaItems()
        }
        loadedUri = null
        stopPositionPump()
        _state.value = PlayerSnapshot()
    }

    override fun release() {
        stopPositionPump()
        exo?.removeListener(listener)
        exo?.release()
        exo = null
        loadedUri = null
        _state.value = PlayerSnapshot()
    }

    private fun startPositionPump() {
        if (positionJob?.isActive == true) return
        positionJob = mainScope.launch {
            while (true) {
                val player = exo ?: break
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
                _state.update {
                    it.copy(
                        positionMillis = pos,
                        durationMillis = if (dur > 0L) dur else it.durationMillis,
                        amplitudes = syntheticEnvelope(pos),
                    )
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun stopPositionPump() {
        positionJob?.cancel()
        positionJob = null
    }

    /**
     * A cheap, deterministic 0f..1f envelope keyed off the playhead so the scrubber
     * waveform animates while playing. Honest: it is decorative, not the real signal.
     */
    private fun syntheticEnvelope(positionMillis: Long): List<Float> {
        val phase = positionMillis / 90.0
        return List(WAVEFORM_COLUMNS) { i ->
            val v = sin(phase + i * 0.55) * 0.5 + 0.5
            (v * 0.85 + 0.1).toFloat().coerceIn(0f, 1f)
        }
    }

    private companion object {
        const val POSITION_POLL_MS = 100L
        const val WAVEFORM_COLUMNS = 24
    }
}
