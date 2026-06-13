// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Voicemail
import kotlinx.coroutines.flow.Flow

/**
 * Visual voicemail via [android.provider.VoicemailContract] where supported (§8).
 *
 * HONESTY PRINCIPLE: VVM is carrier/line-dependent. Callers must consult
 * [CapabilityRepository] /
 * [com.glyphdialer.core.domain.model.CapabilityFlags.vvmSupported] and fall back
 * to dialing the carrier voicemail number when unsupported. [isSupported] mirrors
 * that runtime check for convenience.
 */
interface VoicemailRepository {

    /** Whether visual voicemail is available on the active line right now. */
    suspend fun isSupported(): AppResult<Boolean>

    /** Observe stored voicemails, newest first. Empty when VVM is unsupported. */
    fun observeVoicemails(): Flow<List<Voicemail>>

    /** The carrier voicemail dial number for the fallback shortcut, or null. */
    suspend fun carrierVoicemailNumber(): AppResult<String?>

    /** Ensure the audio body for [id] is downloaded; returns the local audio URI. */
    suspend fun downloadAudio(id: Long): AppResult<String>

    /** Mark [id] read/unread. */
    suspend fun setRead(id: Long, read: Boolean): AppResult<Unit>

    /** Delete the voicemail [id]. */
    suspend fun delete(id: Long): AppResult<Unit>
}
