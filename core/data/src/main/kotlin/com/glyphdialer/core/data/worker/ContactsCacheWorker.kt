// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Warms an in-memory/provider read of contacts so the first UI paint is instant
 * (BUILD_SPEC §19). Contacts are NOT duplicated into Room (the platform provider is
 * authoritative); this worker simply primes the provider query and verifies the
 * account filter resolves, surfacing failures via WorkManager retry.
 */
@HiltWorker
class ContactsCacheWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val contacts: ContactsRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val filter = when (val r = settings.current()) {
            is AppResult.Success -> r.data.contactsAccountFilter
            is AppResult.Failure -> null
        }
        return try {
            // Trigger a single provider read (priming OS-side caches).
            val snapshot = contacts.observeContacts(filter).first()
            Timber.i("Contacts cache primed: %d contacts (filter=%s)", snapshot.size, filter)
            Result.success()
        } catch (e: SecurityException) {
            // READ_CONTACTS not granted yet — not retryable; the UI will re-trigger
            // after the permission flow.
            Timber.w(e, "Contacts cache skipped: permission not granted")
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "Contacts cache failed; retrying")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME: String = com.glyphdialer.core.common.Constants.Work.CONTACTS_CACHE
    }
}
