// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.di

import android.content.Context
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.feature.voicemail.player.ExoVoicemailPlayer
import com.glyphdialer.feature.voicemail.player.VoicemailPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * DI wiring for :feature:voicemail.
 *
 * The voicemail player is a process-wide singleton (only one VVM message plays at a
 * time, and we want it to survive recomposition). It is driven on a main-affine
 * scope so ExoPlayer's looper requirement is honored without the ViewModel managing
 * threading.
 */
@Module
@InstallIn(SingletonComponent::class)
object VoicemailModule {

    /** Qualifier for the player's internal main-thread coroutine scope. */
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class PlayerScope

    @Provides
    @Singleton
    @PlayerScope
    fun providePlayerScope(
        @Dispatcher(GlyphDispatcher.MAIN) mainDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    @Provides
    @Singleton
    fun provideVoicemailPlayer(
        @ApplicationContext context: Context,
        @PlayerScope scope: CoroutineScope,
    ): VoicemailPlayer = ExoVoicemailPlayer(context, scope)
}
