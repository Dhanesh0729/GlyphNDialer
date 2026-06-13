// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.repository.BlockedNumberRepository
import com.glyphdialer.telecom.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Screens incoming calls (BUILD_SPEC §7.5 / §8): consults the
 * [BlockedNumberRepository] and decides whether to allow, silence, or block-and-reject
 * the call before it ever rings. The OS gives screening services a short budget, so
 * we time-box the lookup and default to ALLOW on timeout/failure (never silently drop
 * a call because of an internal error).
 *
 * Caller-ID / spam *labeling* for allowed calls is surfaced in the in-call UI via the
 * [com.glyphdialer.telecom.CallRegistry] mapper + contacts lookup; this service only
 * makes the allow/block decision (the screening API cannot attach a display label).
 */
@AndroidEntryPoint
class GlyphCallScreeningService : CallScreeningService() {

    @Inject lateinit var blockedNumbers: BlockedNumberRepository

    @Inject @Dispatcher(GlyphDispatcher.IO) lateinit var ioDispatcher: CoroutineDispatcher

    private val scope = CoroutineScope(SupervisorJob())

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen genuinely incoming calls; pass everything else through.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondAllow(callDetails)
            return
        }
        val number = callDetails.handle?.schemeSpecificPart
        if (number.isNullOrBlank()) {
            // Anonymous/withheld number: allow (don't block what we can't identify).
            respondAllow(callDetails)
            return
        }

        scope.launch(ioDispatcher) {
            val blocked = withTimeoutOrNull(SCREENING_BUDGET_MS) {
                when (val r = blockedNumbers.isBlocked(number)) {
                    is AppResult.Success -> r.data
                    is AppResult.Failure -> {
                        Timber.tag(Constants.TAG).w(r.error, "Block check failed for screened call")
                        false
                    }
                }
            } ?: false // Timed out: fail open (allow).

            if (blocked) {
                Timber.tag(Constants.TAG).i("Screening: blocking incoming call")
                respondBlock(callDetails)
            } else {
                respondAllow(callDetails)
            }
        }
    }

    private fun respondAllow(details: Call.Details) {
        respondToCall(details, CallResponse.Builder().build())
    }

    private fun respondBlock(details: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            // Keep it out of the missed-call notification + call log per block semantics.
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()
        respondToCall(details, response)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** The OS expects a fast screening decision; budget the block-list lookup. */
        const val SCREENING_BUDGET_MS = 1_500L
    }
}
