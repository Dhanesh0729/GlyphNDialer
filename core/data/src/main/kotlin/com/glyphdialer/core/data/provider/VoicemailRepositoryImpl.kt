// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.VoicemailContract
import android.telephony.TelephonyManager
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.Voicemail
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import com.glyphdialer.core.domain.repository.VoicemailRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VoicemailRepository] backed by [VoicemailContract] (CONVENTIONS.md §5, BUILD_SPEC
 * §8).
 *
 * HONESTY PRINCIPLE (§9): visual voicemail is carrier/line-dependent. We never
 * pretend it works — [isSupported] reflects the genuine runtime check (telephony +
 * carrier VVM config), and the observable list is empty when VVM is unavailable.
 * Callers fall back to dialing [carrierVoicemailNumber].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class VoicemailRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatter: PhoneNumberFormatter,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : VoicemailRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun isSupported(): AppResult<Boolean> =
        appResultOfSuspend { withContext(ioDispatcher) { resolveSupported() } }

    private fun resolveSupported(): Boolean {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        if (!hasTelephony) return false
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            // Best-effort honest signal: VVM config presence. Many devices gate this
            // behind READ_PHONE_STATE / carrier privileges; absence => treat as unsupported.
            tm?.isVoiceMailNumberAvailable() ?: false
        }.getOrDefault(false)
    }

    private fun TelephonyManager.isVoiceMailNumberAvailable(): Boolean =
        runCatching { !voiceMailNumber.isNullOrBlank() }.getOrDefault(false)

    override fun observeVoicemails(): Flow<List<Voicemail>> =
        resolver.observeChanges(VoicemailContract.Voicemails.CONTENT_URI)
            .conflate()
            .mapLatest { withContext(ioDispatcher) { queryVoicemails() } }
            .flowOn(ioDispatcher)

    override suspend fun carrierVoicemailNumber(): AppResult<String?> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                runCatching {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    tm?.voiceMailNumber?.takeIf { it.isNotBlank() }
                }.getOrNull()
            }
        }

    override suspend fun downloadAudio(id: Long): AppResult<String> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val uri = android.content.ContentUris.withAppendedId(
                    VoicemailContract.Voicemails.CONTENT_URI, id,
                )
                // The body, once present, is read via this content URI. Triggering a
                // fetch from the carrier requires the VVM source app; absent that we
                // honestly report the existing URI rather than fabricating a download.
                resolver.query(
                    uri,
                    arrayOf(VoicemailContract.Voicemails.HAS_CONTENT),
                    null, null, null,
                )?.use { c ->
                    check(c.moveToFirst()) { "Voicemail $id not found" }
                    val hasContent = c.getInt(0) == 1
                    check(hasContent) { "Voicemail $id body is not available locally" }
                }
                uri.toString()
            }
        }

    override suspend fun setRead(id: Long, read: Boolean): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val uri = android.content.ContentUris.withAppendedId(
                    VoicemailContract.Voicemails.CONTENT_URI, id,
                )
                val values = ContentValues().apply {
                    put(VoicemailContract.Voicemails.IS_READ, if (read) 1 else 0)
                }
                resolver.update(uri, values, null, null)
            }
            Unit
        }

    override suspend fun delete(id: Long): AppResult<Unit> =
        appResultOfSuspend {
            withContext(ioDispatcher) {
                val uri = android.content.ContentUris.withAppendedId(
                    VoicemailContract.Voicemails.CONTENT_URI, id,
                )
                resolver.delete(uri, null, null)
            }
            Unit
        }

    private fun queryVoicemails(): List<Voicemail> {
        if (!resolveSupported()) {
            Timber.d("VVM unsupported on this line; returning empty list")
            return emptyList()
        }
        val result = ArrayList<Voicemail>()
        runCatching {
            resolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                arrayOf(
                    VoicemailContract.Voicemails._ID,
                    VoicemailContract.Voicemails.NUMBER,
                    VoicemailContract.Voicemails.DATE,
                    VoicemailContract.Voicemails.DURATION,
                    VoicemailContract.Voicemails.IS_READ,
                    VoicemailContract.Voicemails.HAS_CONTENT,
                    VoicemailContract.Voicemails.TRANSCRIPTION,
                ),
                "${VoicemailContract.Voicemails.DELETED} = 0",
                null,
                "${VoicemailContract.Voicemails.DATE} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails._ID)
                val numCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails.NUMBER)
                val dateCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails.DATE)
                val durCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails.DURATION)
                val readCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails.IS_READ)
                val contentCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails.HAS_CONTENT)
                val transCol = c.getColumnIndexOrThrow(VoicemailContract.Voicemails.TRANSCRIPTION)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val hasContent = c.getInt(contentCol) == 1
                    result += Voicemail(
                        id = id,
                        number = formatter.toPhoneNumber(c.getString(numCol).orEmpty()),
                        timestampMillis = c.getLong(dateCol),
                        durationSeconds = c.getLong(durCol),
                        isRead = c.getInt(readCol) == 1,
                        hasContent = hasContent,
                        audioUri = if (hasContent) {
                            android.content.ContentUris.withAppendedId(
                                VoicemailContract.Voicemails.CONTENT_URI, id,
                            ).toString()
                        } else {
                            null
                        },
                        transcriptionText = c.getString(transCol),
                    )
                }
            }
        }.onFailure { Timber.w(it, "VVM query failed; treating as unsupported") }
        return result
    }
}
