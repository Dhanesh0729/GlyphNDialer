// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.data.db.dao.BlockedNumberDao
import com.glyphdialer.core.data.db.entity.BlockedNumberEntity
import com.glyphdialer.core.data.mapper.toDomain
import com.glyphdialer.core.domain.model.BlockedNumber
import com.glyphdialer.core.domain.repository.BlockedNumberRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BlockedNumberRepository] backed by [BlockedNumberContract] (authoritative when
 * the app is the default dialer) mirrored into a Room cache for fast screening
 * checks (CONVENTIONS.md §5, BUILD_SPEC §8).
 *
 * The observable list is served from the Room cache so the UI updates instantly;
 * writes go to BOTH the platform contract (best-effort — requires the role) and the
 * cache. A [SecurityException] from the contract is captured into [AppResult] and
 * the local cache still reflects the user's intent.
 */
@Singleton
class BlockedNumberRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BlockedNumberDao,
    private val formatter: PhoneNumberFormatter,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : BlockedNumberRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun observeBlocked(): Flow<List<BlockedNumber>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun isBlocked(number: String): AppResult<Boolean> =
        appResultOfSuspend {
            val normalized = formatter.toE164(number) ?: number
            withContext(ioDispatcher) { dao.isBlocked(normalized) }
        }

    override suspend fun block(number: String, reportAsSpam: Boolean): AppResult<Unit> =
        appResultOfSuspend {
            val normalized = formatter.toE164(number) ?: number
            withContext(ioDispatcher) {
                // Best-effort platform write (requires default-dialer role).
                runCatching {
                    val values = ContentValues().apply {
                        put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                        put(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER, normalized)
                    }
                    resolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
                }.onFailure { Timber.w(it, "Platform block write failed; cache still updated") }

                dao.upsert(
                    BlockedNumberEntity(
                        rawNumber = number,
                        normalizedNumber = normalized,
                        createdAtMillis = System.currentTimeMillis(),
                        reportedAsSpam = reportAsSpam,
                    ),
                )
            }
            Unit
        }

    override suspend fun unblock(number: String): AppResult<Unit> =
        appResultOfSuspend {
            val normalized = formatter.toE164(number) ?: number
            withContext(ioDispatcher) {
                runCatching {
                    resolver.delete(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        "${BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER} = ? OR " +
                            "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                        arrayOf(normalized, number),
                    )
                }.onFailure { Timber.w(it, "Platform unblock write failed; cache still updated") }

                dao.deleteByNormalized(normalized)
            }
            Unit
        }
}
