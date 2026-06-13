// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.di

import com.glyphdialer.core.domain.repository.TelecomRepository
import com.glyphdialer.telecom.repository.TelecomRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the `:telecom` implementation of the domain [TelecomRepository]
 * (CONVENTIONS.md §5/§6). The dependencies the impl injects — [WebRtcClient] and the
 * qualified dispatchers — are provided by `:peripheral:webrtc` and `:core:common`
 * respectively; the full graph is assembled in `:app`, so this module only contributes
 * the binding it owns (no @Provides for cross-module impls — that would create a
 * forbidden dependency, see CONVENTIONS.md §3).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelecomModule {

    @Binds
    @Singleton
    abstract fun bindTelecomRepository(impl: TelecomRepositoryImpl): TelecomRepository
}
