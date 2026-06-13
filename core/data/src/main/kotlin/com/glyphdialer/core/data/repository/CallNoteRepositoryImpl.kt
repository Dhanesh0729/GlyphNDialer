// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.data.db.dao.CallNoteDao
import com.glyphdialer.core.data.db.entity.CallNoteEntity
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.domain.model.CallNote
import com.glyphdialer.core.domain.repository.CallNoteRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [CallNoteRepository] (CONVENTIONS.md §5, BUILD_SPEC §18). */
@Singleton
class CallNoteRepositoryImpl @Inject constructor(
    private val dao: CallNoteDao,
    private val formatter: PhoneNumberFormatter,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CallNoteRepository {

    override fun observeForCall(callId: String): Flow<List<CallNote>> =
        dao.observeForCall(callId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeForNumber(number: String): Flow<List<CallNote>> {
        val normalized = formatter.toE164(number)
        return dao.observeForNumber(normalized = normalized, raw = number)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun addNote(callId: String, number: String, text: String): AppResult<String> =
        appResultOfSuspend {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            withContext(ioDispatcher) {
                dao.upsert(
                    CallNoteEntity(
                        id = id,
                        callId = callId,
                        rawNumber = number,
                        normalizedNumber = formatter.toE164(number),
                        text = text,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ),
                )
            }
            id
        }

    override suspend fun updateNote(id: String, text: String): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                dao.updateText(id = id, text = text, updatedAt = System.currentTimeMillis())
            }
        }

    override suspend fun deleteNote(id: String): AppResult<Unit> =
        appResultOfSuspend { withContext(ioDispatcher) { dao.deleteById(id) } }
}
