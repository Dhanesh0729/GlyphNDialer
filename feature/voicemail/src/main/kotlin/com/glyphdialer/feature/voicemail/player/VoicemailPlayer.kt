// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.player

import kotlinx.coroutines.flow.StateFlow

/**
 * A small playback abstraction over Media3 ExoPlayer for the voicemail mini-player
 * (BUILD_SPEC §8 — "play/pause/scrub"). Kept as an interface so the ViewModel is
 * unit-testable with a fake and so all ExoPlayer/threading concerns live in one
 * place ([ExoVoicemailPlayer]).
 *
 * All methods are main-thread affine (ExoPlayer requires it); callers invoke them
 * from the main dispatcher.
 */
interface VoicemailPlayer {

    /** Observable snapshot of the current playback (id, playing, position, duration). */
    val state: StateFlow<PlayerSnapshot>

    /**
     * Prepare and start playing [audioUri] for voicemail [id]. If a different item is
     * already loaded it is replaced; if the same item is loaded it resumes.
     */
    fun play(id: Long, audioUri: String)

    /** Pause without releasing the current media. */
    fun pause()

    /** Resume the currently-loaded media. */
    fun resume()

    /** Seek to [positionMillis] within the current media. */
    fun seekTo(positionMillis: Long)

    /** Stop playback and clear the loaded media (keeps the player alive). */
    fun stop()

    /** Release all native resources. Call from the owner's onCleared/onDestroy. */
    fun release()
}

/**
 * Immutable playback snapshot emitted by [VoicemailPlayer.state]. Mirrors the
 * subset of ExoPlayer state the UI needs; amplitudes are a lightweight synthetic
 * envelope for the dot-matrix scrubber (we do not decode PCM for VVM clips).
 */
data class PlayerSnapshot(
    val voicemailId: Long? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    /** Synthetic 0f..1f envelope sampled while playing, for the waveform scrubber. */
    val amplitudes: List<Float> = emptyList(),
    /** Set when playback failed; presentable message for the UI. */
    val errorMessage: String? = null,
)
