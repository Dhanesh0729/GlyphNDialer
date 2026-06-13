// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.getOrElse
import com.glyphdialer.core.data.db.dao.RecordingDao
import com.glyphdialer.core.data.db.dao.TranscriptDao
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.data.mapper.toEntity
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.Recording
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.repository.CallRecorder
import com.glyphdialer.core.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordingRepository] impl that coordinates the [CallRecorder] peripheral and
 * persists [Recording] metadata in Room (CONVENTIONS.md §5, BUILD_SPEC §12).
 *
 * HONESTY PRINCIPLE (§2.1/§12): [resolveTier] delegates to the recorder's honest
 * per-call tier resolution; the active tier is published via [activeTier] so the
 * UI can truthfully display "two-way" vs "my side only" and never fabricate
 * two-way capture.
 */
@Singleton
class RecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val recorder: CallRecorder,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : RecordingRepository {

    private val _activeTier = MutableStateFlow(RecordingTier.UNAVAILABLE)
    override val activeTier: StateFlow<RecordingTier> = _activeTier.asStateFlow()

    override val isRecording: StateFlow<Boolean> get() = recorder.isRecording

    /** Tracks the in-flight recording so [stopRecording] can finalize its metadata. */
    private var pending: Recording? = null

    override suspend fun resolveTier(call: CallModel): RecordingTier =
        recorder.supportedTierFor(call)

    override suspend fun startRecording(call: CallModel): AppResult<Recording> =
        appResultOfSuspend {
            val tier = recorder.supportedTierFor(call)
            check(tier.isAvailable) { "Recording is unavailable for this call (tier=$tier)." }

            val path = recorder.start(call).getOrElse { failure ->
                throw IllegalStateException(failure.message ?: "Recorder failed to start", failure.error)
            }

            val recording = Recording(
                id = UUID.randomUUID().toString(),
                callId = call.id,
                number = call.number,
                contactName = call.displayName,
                startedAtMillis = System.currentTimeMillis(),
                durationMillis = 0L,
                tier = tier,
                filePath = path,
                announced = true,
            )
            withContext(ioDispatcher) { recordingDao.upsert(recording.toEntity()) }
            pending = recording
            _activeTier.value = tier
            Timber.i("Recording started: id=%s tier=%s", recording.id, tier)
            recording
        }

    override suspend fun stopRecording(): AppResult<Recording?> =
        appResultOfSuspend {
            val current = pending
            recorder.stop().getOrElse { failure ->
                Timber.w(failure.error, "Recorder stop reported failure: %s", failure.message)
            }
            _activeTier.value = RecordingTier.UNAVAILABLE
            pending = null
            if (current == null) {
                Timber.d("stopRecording called with no in-flight recording")
                return@appResultOfSuspend null
            }
            val durationMillis = (System.currentTimeMillis() - current.startedAtMillis).coerceAtLeast(0L)
            val finalized = current.copy(durationMillis = durationMillis)
            withContext(ioDispatcher) { recordingDao.upsert(finalized.toEntity()) }
            Timber.i("Recording finalized: id=%s durationMs=%d", finalized.id, durationMillis)
            finalized
        }

    override fun observeRecordings(): Flow<List<Recording>> =
        recordingDao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getRecording(id: String): AppResult<Recording?> =
        appResultOfSuspend { withContext(ioDispatcher) { recordingDao.getById(id)?.toDomain() } }

    override suspend fun deleteRecording(id: String): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val entity = recordingDao.getById(id)
                if (entity != null) {
                    deleteBodyQuietly(entity.filePath)
                    entity.transcriptId?.let { transcriptDao.deleteById(it) }
                    recordingDao.deleteById(id)
                }
            }
        }

    override suspend fun export(id: String): AppResult<String> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val entity = recordingDao.getById(id)
                    ?: error("No recording with id=$id")
                val source = File(entity.filePath)
                check(source.exists()) { "Recording body missing at ${entity.filePath}" }
                exportToMediaStore(source, entity.id)
            }
        }

    override suspend fun purgeOlderThan(cutoffMillis: Long): AppResult<Int> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val stale = recordingDao.olderThan(cutoffMillis)
                stale.forEach { deleteBodyQuietly(it.filePath) }
                recordingDao.deleteOlderThan(cutoffMillis)
            }
        }

    private fun deleteBodyQuietly(path: String) {
        runCatching {
            val file = File(path)
            if (file.exists() && !file.delete()) {
                Timber.w("Failed to delete recording body at %s", path)
            }
        }.onFailure { Timber.w(it, "Error deleting recording body at %s", path) }
    }

    /** Copies [source] into the shared MediaStore Music collection; returns the content URI. */
    private fun exportToMediaStore(source: File, recordingId: String): String {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "glyph_recording_$recordingId.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/GlyphDialer",
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused the export insert")
        resolver.openOutputStream(uri).use { out ->
            checkNotNull(out) { "Could not open MediaStore output stream" }
            source.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        Timber.i("Exported recording %s to %s", recordingId, uri)
        return uri.toString()
    }
}
