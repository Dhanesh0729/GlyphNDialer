// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.transcription.engine

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Optional cloud speech-to-text engine (§13), gated behind a flag and explicit user
 * consent (audio leaves the device → privacy + §2.4 honesty matter most here).
 *
 * This is a documented STUB: no network client is bundled and no audio is ever sent.
 * [isAvailable] returns whether a backend has been configured ([endpoint] non-blank);
 * by default it is empty, so the engine is honestly unavailable and both paths return
 * an [AppResult.Failure] explaining how to enable it.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TODO(cloud-stt): To enable the network path:
 *   1. Add an HTTP/WebSocket client (the catalog already has Ktor: libs.bundles.ktor.client)
 *      to this module and inject it here.
 *   2. Set [endpoint] from SettingsRepository (a user-entered/region URL) — never hard-code.
 *   3. Implement [transcribeFile]: stream the (consented) audio to the endpoint and map
 *      the JSON response to a Transcript with timestamped segments.
 *   4. Implement [liveCaptions]: open a bidirectional WebSocket, push PCM frames, emit
 *      interim transcripts. Surface "audio sent to cloud" prominently in the UI.
 *   5. Require explicit per-call consent before any byte leaves the device.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class CloudSttEngine @Inject constructor() : TranscriptionEngine {

    override val type: TranscriptionEngineType = TranscriptionEngineType.CLOUD

    /**
     * Configured STT backend URL. Empty by default → engine unavailable. A real build
     * sets this from user settings; we never hard-code a backend (privacy + flexibility).
     */
    var endpoint: String = ""

    override val isAvailable: Boolean
        get() = endpoint.isNotBlank()

    override suspend fun transcribeFile(path: String): AppResult<Transcript> {
        if (!isAvailable) return notConfigured()
        // TODO(cloud-stt): upload + map response. Unreachable until endpoint is set.
        return notConfigured()
    }

    override fun liveCaptions(): Flow<String> {
        if (!isAvailable) {
            Timber.i("CloudSttEngine.liveCaptions: no endpoint configured — no captions")
        }
        return emptyFlow()
    }

    override fun release() = Unit

    private fun notConfigured(): AppResult.Failure {
        val msg = "Cloud transcription is not configured. It is disabled by default " +
            "because it sends call audio off-device. Configure a backend endpoint and " +
            "grant consent in Settings to enable it, or use an on-device engine."
        Timber.w(msg)
        return AppResult.Failure(error = CloudSttUnavailableException(msg), message = msg)
    }
}

/** Thrown when cloud STT is requested but no backend endpoint has been configured. */
class CloudSttUnavailableException(message: String) : Exception(message)
