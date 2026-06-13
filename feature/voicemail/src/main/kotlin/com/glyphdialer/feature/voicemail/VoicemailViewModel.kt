// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.fold
import com.glyphdialer.core.common.getOrNull
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.Voicemail
import com.glyphdialer.core.domain.repository.TranscriptRepository
import com.glyphdialer.core.domain.repository.VoicemailRepository
import com.glyphdialer.feature.voicemail.player.VoicemailPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Visual Voicemail screen (BUILD_SPEC §8; CONVENTIONS.md §5).
 *
 * Responsibilities:
 *  - Resolve VVM support honestly and drive [VvmSupportState] (§9).
 *  - Observe stored voicemails and pair each with its engine [Transcript] (§13 reuse).
 *  - Mirror the [VoicemailPlayer] snapshot into [PlaybackState] for play/pause/scrub.
 *  - Delegate dialing (call-back, carrier-voicemail fallback) to :app via
 *    [VoicemailEffect.Dial] — staying within the feature dependency graph (§3).
 *
 * Dispatchers are injected (never hard-coded). Repository calls return [AppResult]
 * and are folded into UI state rather than thrown.
 */
@HiltViewModel
class VoicemailViewModel @Inject constructor(
    private val voicemailRepository: VoicemailRepository,
    private val transcriptRepository: TranscriptRepository,
    private val player: VoicemailPlayer,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoicemailUiState())
    val uiState: StateFlow<VoicemailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<VoicemailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Cache of the raw voicemails so transcripts can be re-merged without a reload. */
    private var latestVoicemails: List<Voicemail> = emptyList()
    private var latestTranscripts: List<Transcript> = emptyList()

    init {
        observePlayer()
        observeData()
        refresh()
    }

    fun onEvent(event: VoicemailEvent) {
        when (event) {
            VoicemailEvent.Refresh -> refresh()
            is VoicemailEvent.Play -> onPlay(event.id)
            VoicemailEvent.Pause -> player.pause()
            VoicemailEvent.Resume -> player.resume()
            is VoicemailEvent.SeekTo -> onSeek(event.fraction)
            is VoicemailEvent.Transcribe -> onTranscribe(event.id)
            is VoicemailEvent.SetRead -> onSetRead(event.id, event.read)
            is VoicemailEvent.CallBack -> onCallBack(event.id)
            is VoicemailEvent.Delete -> onDelete(event.id)
            VoicemailEvent.DialCarrierVoicemail -> onDialCarrierVoicemail()
            VoicemailEvent.DismissError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    // --- Support resolution + loading ------------------------------------------------

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val supported = voicemailRepository.isSupported().getOrNull() ?: false
            val carrierNumber = voicemailRepository.carrierVoicemailNumber().getOrNull()
            _uiState.update {
                it.copy(
                    support = if (supported) VvmSupportState.SUPPORTED else VvmSupportState.UNSUPPORTED,
                    carrierVoicemailNumber = carrierNumber,
                    // When unsupported there's no list to wait on; stop the spinner.
                    isLoading = supported,
                )
            }
        }
    }

    /** Observe voicemails and transcripts, re-merging into UI items as either changes. */
    private fun observeData() {
        voicemailRepository.observeVoicemails()
            .flowOn(ioDispatcher)
            .catch { t ->
                Timber.e(t, "Failed observing voicemails")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: "Couldn't load voicemail")
                }
            }
            .onEach { voicemails ->
                latestVoicemails = voicemails
                mergeItems()
                _uiState.update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)

        transcriptRepository.observeTranscripts()
            .flowOn(ioDispatcher)
            .catch { t -> Timber.w(t, "Failed observing transcripts") }
            .onEach { transcripts ->
                latestTranscripts = transcripts
                mergeItems()
            }
            .launchIn(viewModelScope)
    }

    /** Pair each voicemail with its transcript (by [Voicemail.transcriptId]). */
    private fun mergeItems() {
        val byId = latestTranscripts.associateBy { it.id }
        val items = latestVoicemails.map { vm ->
            VoicemailItem(
                voicemail = vm,
                transcript = vm.transcriptId?.let { byId[it] },
            )
        }
        _uiState.update { it.copy(items = items) }
    }

    // --- Playback --------------------------------------------------------------------

    private fun observePlayer() {
        player.state
            .onEach { snap ->
                snap.errorMessage?.let { msg ->
                    _uiState.update { it.copy(errorMessage = msg) }
                }
                _uiState.update {
                    it.copy(
                        playback = PlaybackState(
                            voicemailId = snap.voicemailId,
                            isPlaying = snap.isPlaying,
                            isBuffering = snap.isBuffering,
                            positionMillis = snap.positionMillis,
                            durationMillis = snap.durationMillis,
                            amplitudes = snap.amplitudes,
                        ),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun onPlay(id: Long) {
        val item = currentItem(id) ?: return
        val vm = item.voicemail

        // Already loaded → just resume.
        if (_uiState.value.playback.voicemailId == id) {
            player.resume()
            return
        }

        // Ready to play immediately.
        val readyUri = vm.audioUri
        if (vm.hasContent && readyUri != null) {
            player.play(id, readyUri)
            markReadSilently(id)
            return
        }

        // Otherwise download the audio body first (§8: "ensure downloaded").
        viewModelScope.launch {
            _uiState.update {
                it.copy(playback = it.playback.copy(voicemailId = id, isBuffering = true))
            }
            voicemailRepository.downloadAudio(id).fold(
                onSuccess = { uri ->
                    player.play(id, uri)
                    markReadSilently(id)
                },
                onFailure = { failure ->
                    Timber.e(failure.error, "Voicemail download failed")
                    _uiState.update {
                        it.copy(
                            playback = it.playback.copy(isBuffering = false),
                            errorMessage = failure.message ?: "Couldn't download this voicemail",
                        )
                    }
                },
            )
        }
    }

    private fun onSeek(fraction: Float) {
        val duration = _uiState.value.playback.durationMillis
        if (duration <= 0L) return
        val target = (fraction.coerceIn(0f, 1f) * duration).toLong()
        player.seekTo(target)
    }

    // --- Transcription (reuse of §13) ------------------------------------------------

    private fun onTranscribe(id: Long) {
        val item = currentItem(id) ?: return
        val recordingId = item.transcript?.recordingId
            // VVM messages aren't recordings; the repo impl maps the voicemail audio
            // to a transcribe job keyed by a stable id. We pass the voicemail id as the
            // recording key — the TranscriptRepository impl resolves the audio source.
            ?: id.toString()

        viewModelScope.launch {
            _uiState.update { it.copy(transcribingId = id) }
            transcriptRepository.transcribeRecording(recordingId).fold(
                onSuccess = {
                    // The observeTranscripts() stream re-merges the new transcript.
                    _uiState.update { it.copy(transcribingId = null) }
                },
                onFailure = { failure ->
                    Timber.e(failure.error, "Voicemail transcription failed")
                    _uiState.update {
                        it.copy(
                            transcribingId = null,
                            errorMessage = failure.message ?: "Transcription failed",
                        )
                    }
                },
            )
        }
    }

    // --- Read / delete ---------------------------------------------------------------

    private fun onSetRead(id: Long, read: Boolean) {
        viewModelScope.launch {
            voicemailRepository.setRead(id, read).reportFailure("Couldn't update voicemail")
        }
    }

    /** Best-effort mark-as-read on first play; failures are logged, not surfaced. */
    private fun markReadSilently(id: Long) {
        val item = currentItem(id) ?: return
        if (item.voicemail.isRead) return
        viewModelScope.launch {
            val result = voicemailRepository.setRead(id, read = true)
            if (result is AppResult.Failure) {
                Timber.w(result.error, "Silent mark-read failed for voicemail %d", id)
            }
        }
    }

    private fun onDelete(id: Long) {
        viewModelScope.launch {
            // Stop playback if we're deleting the playing message.
            if (_uiState.value.playback.voicemailId == id) player.stop()
            voicemailRepository.delete(id).fold(
                onSuccess = { _effects.send(VoicemailEffect.ShowMessage("Voicemail deleted")) },
                onFailure = { failure ->
                    _uiState.update {
                        it.copy(errorMessage = failure.message ?: "Couldn't delete voicemail")
                    }
                },
            )
        }
    }

    // --- Dialing (delegated to :app) -------------------------------------------------

    private fun onCallBack(id: Long) {
        val number = currentItem(id)?.voicemail?.number?.dialValue ?: return
        viewModelScope.launch { _effects.send(VoicemailEffect.Dial(number)) }
    }

    private fun onDialCarrierVoicemail() {
        viewModelScope.launch {
            val number = _uiState.value.carrierVoicemailNumber
                ?: voicemailRepository.carrierVoicemailNumber().getOrNull()
            if (number.isNullOrBlank()) {
                _effects.send(VoicemailEffect.ShowMessage("No carrier voicemail number available"))
            } else {
                _effects.send(VoicemailEffect.Dial(number))
            }
        }
    }

    // --- Helpers ---------------------------------------------------------------------

    private fun currentItem(id: Long): VoicemailItem? =
        _uiState.value.items.firstOrNull { it.id == id }

    private suspend fun AppResult<Unit>.reportFailure(fallback: String) {
        if (this is AppResult.Failure) {
            Timber.e(error, fallback)
            _uiState.update { it.copy(errorMessage = message ?: fallback) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Stop (don't release) — the player is a process singleton shared with the
        // host; releasing happens at app teardown. Stopping frees the media/buffers.
        player.stop()
    }
}
