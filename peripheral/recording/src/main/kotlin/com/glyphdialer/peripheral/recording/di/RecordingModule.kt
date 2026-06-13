// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.di

import com.glyphdialer.core.domain.repository.CallRecorder
import com.glyphdialer.peripheral.recording.CallRecorderImpl
import com.glyphdialer.peripheral.recording.VoipPcmFeed
import com.glyphdialer.peripheral.recording.VoipTrackRecorder
import com.glyphdialer.peripheral.recording.tier.DefaultSystemCallAudioProbe
import com.glyphdialer.peripheral.recording.tier.SystemCallAudioProbe
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for `:peripheral:recording`.
 *
 * NOTE on Tier A (§2.1): the default [SystemCallAudioProbe] binding is the CONSERVATIVE
 * one ([DefaultSystemCallAudioProbe]), which returns `false` on stock Android. An
 * OEM/system/rooted integration replaces this binding to enable SYSTEM_TWO_WAY — the rest
 * of the module is unchanged.
 *
 * NOTE on Tier B: [VoipTrackRecorder] is also exposed as [VoipPcmFeed] so
 * `:peripheral:webrtc` (wired in `:app`) can push mixed PCM frames into the recorder
 * without this module depending on the WebRTC module (preserves the §3 dependency graph).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingModule {

    /** The domain [CallRecorder] contract is satisfied by [CallRecorderImpl]. */
    @Binds
    @Singleton
    abstract fun bindCallRecorder(impl: CallRecorderImpl): CallRecorder

    /** Conservative, honest default for Tier A detection (replace on privileged builds). */
    @Binds
    @Singleton
    abstract fun bindSystemCallAudioProbe(impl: DefaultSystemCallAudioProbe): SystemCallAudioProbe

    /** Expose the singleton VoIP recorder as the PCM feed for the WebRTC producer. */
    @Binds
    abstract fun bindVoipPcmFeed(impl: VoipTrackRecorder): VoipPcmFeed
}
