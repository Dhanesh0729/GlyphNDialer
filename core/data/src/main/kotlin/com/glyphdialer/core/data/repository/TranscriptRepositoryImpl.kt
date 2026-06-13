// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.common.getOrElse
import com.glyphdialer.core.data.db.dao.RecordingDao
import com.glyphdialer.core.data.db.dao.TranscriptDao
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.data.mapper.toEntity
import com.glyphdialer.core.data.mapper.toSegmentEntities
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.repository.TranscriptRepository
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TranscriptRepository] impl coordinating the selected [TranscriptionEngine] and
 * persisting [Transcript]s in Room (CONVENTIONS.md §5, BUILD_SPEC §13).
 *
 * HONESTY PRINCIPLE (§2.4): the [Transcript.isLocalSideOnly] flag produced by the
 * engine is persisted verbatim and never overridden; live captions reflect only
 * what the engine can legally/technically access.
 */
@Singleton
class TranscriptRepositoryImpl @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val recordingDao: RecordingDao,
    private val engine: TranscriptionEngine,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TranscriptRepository {

    override fun observeTranscripts(): Flow<List<Transcript>> =
        transcriptDao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun search(query: String): AppResult<List<Transcript>> =
        appResultOfSuspend {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                emptyList()
            } else {
                withContext(ioDispatcher) {
                    transcriptDao.search(trimmed).map { it.toDomain() }
                }
            }
        }

    override suspend fun getTranscript(id: String): AppResult<Transcript?> =
        appResultOfSuspend { withContext(ioDispatcher) { transcriptDao.getById(id)?.toDomain() } }

    override suspend fun getForRecording(recordingId: String): AppResult<Transcript?> =
        appResultOfSuspend {
            withContext(ioDispatcher) { transcriptDao.getForRecording(recordingId)?.toDomain() }
        }

    override suspend fun transcribeRecording(recordingId: String): AppResult<Transcript> =
        appResultOfSuspend {
            check(engine.isAvailable) { "Transcription engine '${engine.type}' is unavailable." }
            val recording = withContext(ioDispatcher) { recordingDao.getById(recordingId) }
                ?: error("No recording with id=$recordingId")

            val produced = engine.transcribeFile(recording.filePath).getOrElse { failure ->
                throw IllegalStateException(failure.message ?: "Transcription failed", failure.error)
            }
            // Bind the engine output to this recording (engine may not know the id).
            val transcript = produced.copy(recordingId = recordingId)

            withContext(ioDispatcher) {
                transcriptDao.upsertWithSegments(transcript.toEntity(), transcript.toSegmentEntities())
                recordingDao.attachTranscript(recordingId, transcript.id)
            }
            Timber.i("Transcribed recording %s -> transcript %s (localOnly=%b)",
                recordingId, transcript.id, transcript.isLocalSideOnly)
            transcript
        }

    override fun liveCaptions(): Flow<String> = engine.liveCaptions()

    override suspend fun deleteTranscript(id: String): AppResult<Unit> =
        appResultOfSuspend { withContext(ioDispatcher) { transcriptDao.deleteById(id) } }

    override suspend fun purgeOlderThan(cutoffMillis: Long): AppResult<Int> =
        appResultOfSuspend { withContext(ioDispatcher) { transcriptDao.deleteOlderThan(cutoffMillis) } }
}
