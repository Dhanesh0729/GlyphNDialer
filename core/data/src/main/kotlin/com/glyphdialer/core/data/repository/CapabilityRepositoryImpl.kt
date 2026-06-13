// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.CapabilityFlags
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.repository.CallRecorder
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.repository.TranscriptionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The runtime authority for the HONESTY PRINCIPLE (§9). Resolves what the device/OS
 * can ACTUALLY do and exposes it as an observable [CapabilityFlags].
 *
 * Conservative by default (§2): every flag starts false / UNAVAILABLE, and we only
 * flip one to true when we have positive evidence. We never claim a capability the
 * platform can't honestly deliver — e.g. [CapabilityFlags.systemCallAudio] stays
 * false on stock Android because third-party apps cannot capture remote call audio.
 *
 * Glyph detection is delegated to [GlyphAvailabilityProbe] so this module never
 * imports the GDK (only :peripheral:glyph may — CONVENTIONS.md §3).
 */
@Singleton
class CapabilityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recorder: CallRecorder,
    private val transcriptionEngine: TranscriptionEngine,
    private val glyphProbe: GlyphAvailabilityProbe,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(GlyphDispatcher.DEFAULT) private val scopeDispatcher: CoroutineDispatcher,
) : CapabilityRepository {

    private val _capabilities = MutableStateFlow(CapabilityFlags())
    override val capabilities: Flow<CapabilityFlags> = _capabilities.asStateFlow()

    private val scope = CoroutineScope(scopeDispatcher)

    init {
        // Prime once at construction; callers can refresh() after role/permission changes.
        scope.launch { runCatching { refresh() } }
    }

    override suspend fun current(): AppResult<CapabilityFlags> =
        AppResult.Success(_capabilities.value)

    override suspend fun refresh(): AppResult<CapabilityFlags> =
        appResultOfSuspend {
            val flags = withContext(ioDispatcher) { resolve() }
            _capabilities.value = flags
            Timber.d("Capabilities resolved: %s", flags)
            flags
        }

    private fun resolve(): CapabilityFlags {
        val pm = context.packageManager
        val micGranted = context.checkSelfPermission(
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        val deviceTier = recorder.supportedTier()
        // Honest mic availability: permission granted AND the recorder can at least
        // do local one-sided capture.
        val microphoneAvailable = micGranted && deviceTier.isAvailable

        return CapabilityFlags(
            glyphAvailable = glyphProbe.isGlyphAvailable(),
            isDefaultDialer = isDefaultDialer(),
            // Stock Android NEVER exposes remote call audio to third-party apps.
            systemCallAudio = deviceTier == RecordingTier.SYSTEM_TWO_WAY,
            // VoIP reachability is owned by :peripheral:webrtc; conservative false here.
            voipAvailable = false,
            // VVM is line-dependent; resolved by VoicemailRepository at use time.
            vvmSupported = false,
            microphoneAvailable = microphoneAvailable,
            cameraAvailable = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            onDeviceSpeechAvailable = transcriptionEngine.isAvailable,
            maxRecordingTier = deviceTier,
        )
    }

    private fun isDefaultDialer(): Boolean = runCatching {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tm?.defaultDialerPackage == context.packageName
        } else {
            @Suppress("DEPRECATION")
            tm?.defaultDialerPackage == context.packageName
        }
    }.getOrDefault(false)
}

/**
 * Indirection so :core:data can ask "is Glyph available?" without importing the GDK
 * (only :peripheral:glyph may — CONVENTIONS.md §3). The real probe is bound in
 * :peripheral:glyph / :app; the conservative default here returns false so non-Nothing
 * builds never claim Glyph (§9).
 */
interface GlyphAvailabilityProbe {
    fun isGlyphAvailable(): Boolean
}

/** Conservative default: no Glyph. Overridden by a real binding in :peripheral:glyph. */
@Singleton
class NoGlyphAvailabilityProbe @Inject constructor() : GlyphAvailabilityProbe {
    override fun isGlyphAvailable(): Boolean = false
}
