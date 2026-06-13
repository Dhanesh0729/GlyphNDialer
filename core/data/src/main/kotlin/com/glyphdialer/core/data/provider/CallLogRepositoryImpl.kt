// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CallType
import com.glyphdialer.core.domain.repository.CallLogRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CallLogRepository] backed by [CallLog.Calls] (CONVENTIONS.md §5, BUILD_SPEC §8).
 * Reads are observable via a ContentObserver; writes require the default-dialer
 * role and will throw [SecurityException] otherwise (captured into [AppResult]).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class CallLogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatter: PhoneNumberFormatter,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : CallLogRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun observeCallLog(types: Set<CallType>?, limit: Int): Flow<List<CallLogEntry>> =
        resolver.observeChanges(CallLog.Calls.CONTENT_URI)
            .conflate()
            .mapLatest { withContext(ioDispatcher) { queryEntries(types, limit, 0, numberLike = null) } }
            .flowOn(ioDispatcher)

    override suspend fun getCallLog(types: Set<CallType>?, limit: Int, offset: Int): AppResult<List<CallLogEntry>> =
        appResultOfSuspend { withContext(ioDispatcher) { queryEntries(types, limit, offset, numberLike = null) } }

    override fun observeForNumber(number: String): Flow<List<CallLogEntry>> =
        resolver.observeChanges(CallLog.Calls.CONTENT_URI)
            .conflate()
            .mapLatest { withContext(ioDispatcher) { queryEntries(types = null, limit = CallLogRepository.DEFAULT_LIMIT, offset = 0, numberLike = number) } }
            .flowOn(ioDispatcher)

    override suspend fun search(query: String): AppResult<List<CallLogEntry>> =
        appResultOfSuspend {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) emptyList()
            else withContext(ioDispatcher) {
                queryEntries(types = null, limit = CallLogRepository.DEFAULT_LIMIT, offset = 0, numberLike = trimmed)
            }
        }

    override suspend fun deleteEntries(ids: List<Long>): AppResult<Unit> =
        appResultOfSuspend {
            if (ids.isEmpty()) return@appResultOfSuspend Unit
            withContext(ioDispatcher) {
                val placeholders = ids.joinToString(",") { "?" }
                resolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    ids.map { it.toString() }.toTypedArray(),
                )
            }
            Unit
        }

    override suspend fun clearAll(): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) { resolver.delete(CallLog.Calls.CONTENT_URI, null, null) }
            Unit
        }

    override suspend fun markAllRead(): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val values = ContentValues().apply {
                    put(CallLog.Calls.NEW, 0)
                    put(CallLog.Calls.IS_READ, 1)
                }
                resolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls.NEW} = 1 AND ${CallLog.Calls.TYPE} = ?",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                )
            }
            Unit
        }

    private fun queryEntries(types: Set<CallType>?, limit: Int, offset: Int, numberLike: String?): List<CallLogEntry> {
        val selections = mutableListOf<String>()
        val args = mutableListOf<String>()
        types?.takeIf { it.isNotEmpty() }?.let { set ->
            val typeCodes = set.flatMap { toAndroidTypeCodes(it) }.distinct()
            if (typeCodes.isNotEmpty()) {
                selections += "${CallLog.Calls.TYPE} IN (${typeCodes.joinToString(",") { "?" }})"
                args += typeCodes.map { it.toString() }
            }
        }
        if (numberLike != null) {
            selections += "(${CallLog.Calls.NUMBER} LIKE ? OR ${CallLog.Calls.CACHED_NAME} LIKE ?)"
            args += "%$numberLike%"
            args += "%$numberLike%"
        }
        val selection = selections.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit OFFSET $offset"

        val result = ArrayList<CallLogEntry>()
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_LOOKUP_URI,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.FEATURES,
            ),
            selection,
            args.toTypedArray().takeIf { it.isNotEmpty() },
            sortOrder,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameCol = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val lookupCol = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_LOOKUP_URI)
            val photoCol = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)
            val typeCol = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durCol = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val featCol = c.getColumnIndexOrThrow(CallLog.Calls.FEATURES)
            while (c.moveToNext()) {
                val raw = c.getString(numCol).orEmpty()
                val features = c.getInt(featCol)
                result += CallLogEntry(
                    id = c.getLong(idCol),
                    number = formatter.toPhoneNumber(raw),
                    contactLookupKey = c.getString(lookupCol),
                    displayName = c.getString(nameCol),
                    photoUri = c.getString(photoCol),
                    type = fromAndroidType(c.getInt(typeCol)),
                    timestampMillis = c.getLong(dateCol),
                    durationSeconds = c.getLong(durCol),
                    isVideo = (features and CallLog.Calls.FEATURES_VIDEO) != 0,
                )
            }
        }
        return result
    }

    private fun fromAndroidType(type: Int): CallType = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.INCOMING
    }

    private fun toAndroidTypeCodes(type: CallType): List<Int> = when (type) {
        CallType.INCOMING -> listOf(CallLog.Calls.INCOMING_TYPE)
        CallType.OUTGOING -> listOf(CallLog.Calls.OUTGOING_TYPE)
        CallType.MISSED -> listOf(CallLog.Calls.MISSED_TYPE)
        CallType.REJECTED -> listOf(CallLog.Calls.REJECTED_TYPE)
        CallType.BLOCKED -> listOf(CallLog.Calls.BLOCKED_TYPE)
        CallType.VOICEMAIL -> listOf(CallLog.Calls.VOICEMAIL_TYPE)
    }
}
