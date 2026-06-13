// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording

import android.content.Context
import androidx.core.content.ContextCompat
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.repository.CallRecorder
import com.glyphdialer.peripheral.recording.internal.TierCaptureEngine
import com.glyphdialer.peripheral.recording.service.RecordingForegroundService
import com.glyphdialer.peripheral.recording.storage.EncryptedRecordingStore
import com.glyphdialer.peripheral.recording.tier.RecorderTierResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Concrete [CallRecorder] (§12). Resolves the honest tier per call via
 * [RecorderTierResolver], selects the matching [TierCaptureEngine], streams encrypted PCM
 * through [EncryptedRecordingStore], and runs the [RecordingForegroundService] for the
 * duration of capture.
 *
 * HONESTY: [supportedTier]/[supportedTierFor] never over-claim — they delegate to the
 * resolver, which only returns SYSTEM_TWO_WAY behind a positive device probe. The
 * returned file path from [start] is the ENCRYPTED body's path (a `.glaud` file); the
 * coordinating [com.glyphdialer.core.domain.repository.RecordingRepository] persists it
 * as [com.glyphdialer.core.domain.model.Recording.filePath] alongside the resolved tier.
 */
@Singleton
class CallRecorderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: RecorderTierResolver,
    private val store: EncryptedRecordingStore,
    private val systemRecorder: Provider<SystemCallAudioRecorder>,
    private val voipRecorder: Provider<VoipTrackRecorder>,
    private val localRecorder: Provider<LocalSideRecorder>,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CallRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Mirrors the active engine's amplitude; emptied between recordings. */
    override val amplitude: Flow<Float>
        get() = activeEngine?.amplitude ?: emptyFlow()

    private val lock = Mutex()

    @Volatile private var activeEngine: TierCaptureEngine? = null
    @Volatile private var activeSession: EncryptedRecordingStore.RecordingSession? = null
    @Volatile private var activeId: String? = null

    override fun supportedTier(): RecordingTier = resolver.resolveBaseline()

    override fun supportedTierFor(call: CallModel): RecordingTier = resolver.resolveForCall(call)

    override suspend fun start(call: CallModel): AppResult<String> = lock.withLock {
        if (_isRecording.value) {
            return AppResult.Failure(
                IllegalStateException("already recording"),
                "A recording is already in progress.",
            )
        }
        appResultOfSuspend {
            val tier = resolver.resolveForCall(call)
            if (tier == RecordingTier.UNAVAILABLE) {
                error("Recording is not available on this device for this call.")
            }

            // Pick the engine for the resolved tier, degrading honestly if a higher tier
            // fails to initialize at runtime (e.g. Tier A privilege turns out denied).
            val (engine, effectiveTier) = startEngineWithFallback(tier, call)

            activeEngine = engine
            _isRecording.value = true

            // Foreground service + Glyph mirror for the duration of capture.
            startForegroundService(effectiveTier, call)

            Timber.d("Recording started for call %s at tier %s", call.id, effectiveTier)
            activeSession!!.encryptedPath()
        }.also { result ->
            if (result is AppResult.Failure) {
                cleanupAfterFailure()
            }
        }
    }

    override suspend fun stop(): AppResult<Unit> = lock.withLock {
        if (!_isRecording.value) {
            return AppResult.Success(Unit)
        }
        appResultOfSuspend {
            val engine = activeEngine
            withContext(ioDispatcher) {
                runCatching { engine?.stop() }
                    .onFailure { Timber.w(it, "engine.stop failed") }
                activeSession?.finalizeAndEncrypt()
            }
            stopForegroundService()
            _isRecording.value = false
            activeEngine?.release()
            activeEngine = null
            activeSession = null
            activeId = null
            Unit
        }
    }

    override fun release() {
        runCatching { activeEngine?.release() }
        activeEngine = null
        // Best-effort finalize so we don't orphan a temp file.
        runCatching { activeSession?.finalizeAndEncrypt() }
        activeSession = null
        activeId = null
        _isRecording.value = false
    }

    /**
     * Starts the engine for [tier]; if a two-way engine fails to initialize at runtime we
     * fall back honestly to LOCAL_ONE_SIDED rather than reporting a tier we can't deliver.
     * Returns the engine actually started and its honest tier.
     */
    private suspend fun startEngineWithFallback(
        tier: RecordingTier,
        call: CallModel,
    ): Pair<TierCaptureEngine, RecordingTier> {
        val ordered = when (tier) {
            RecordingTier.SYSTEM_TWO_WAY -> listOf(RecordingTier.SYSTEM_TWO_WAY, RecordingTier.LOCAL_ONE_SIDED)
            RecordingTier.VOIP_TWO_WAY -> listOf(RecordingTier.VOIP_TWO_WAY) // no honest fallback for VoIP
            RecordingTier.LOCAL_ONE_SIDED -> listOf(RecordingTier.LOCAL_ONE_SIDED)
            RecordingTier.UNAVAILABLE -> emptyList()
        }
        var lastError: Throwable? = null
        for (candidate in ordered) {
            val engine = engineFor(candidate)
            try {
                val id = newRecordingId(call)
                val session = store.openSession(id, engine.format, candidate)
                withContext(ioDispatcher) { engine.start(session) }
                activeSession = session
                activeId = id
                if (candidate != tier) {
                    Timber.w("Tier %s unavailable at runtime; degraded honestly to %s", tier, candidate)
                }
                return engine to candidate
            } catch (t: Throwable) {
                lastError = t
                Timber.w(t, "Engine for tier %s failed to start; trying next", candidate)
                runCatching { engine.release() }
                runCatching { activeSession?.finalizeAndEncrypt() }
                activeSession = null
            }
        }
        throw IllegalStateException(
            "Could not start any recording engine for tier $tier",
            lastError,
        )
    }

    private fun engineFor(tier: RecordingTier): TierCaptureEngine = when (tier) {
        RecordingTier.SYSTEM_TWO_WAY -> systemRecorder.get()
        RecordingTier.VOIP_TWO_WAY -> voipRecorder.get()
        RecordingTier.LOCAL_ONE_SIDED -> localRecorder.get()
        RecordingTier.UNAVAILABLE -> error("No engine for UNAVAILABLE tier")
    }

    private fun startForegroundService(tier: RecordingTier, call: CallModel) {
        val title = call.displayName ?: call.number.formatted
        val intent = RecordingForegroundService.startIntent(context, tier, title)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { Timber.e(it, "Failed to start recording foreground service") }
    }

    private fun stopForegroundService() {
        runCatching { context.startService(RecordingForegroundService.stopIntent(context)) }
            .onFailure { Timber.w(it, "Failed to send stop to recording service") }
    }

    private fun cleanupAfterFailure() {
        runCatching { activeEngine?.release() }
        runCatching { activeSession?.finalizeAndEncrypt() }
        activeId?.let { runCatching { store.delete(it) } }
        activeEngine = null
        activeSession = null
        activeId = null
        _isRecording.value = false
        stopForegroundService()
    }

    private fun newRecordingId(call: CallModel): String =
        "rec_${call.id}_${System.currentTimeMillis()}"

    /** Resolves the encrypted-body path the session writes to (without finalizing). */
    private fun EncryptedRecordingStore.RecordingSession.encryptedPath(): String =
        store.encryptedFileFor(id).absolutePath
}
