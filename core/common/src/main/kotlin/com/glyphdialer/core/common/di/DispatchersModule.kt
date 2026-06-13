// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.di

import com.glyphdialer.core.common.dispatchers.DefaultDispatcherProvider
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.DispatcherProvider
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Provides the three qualified coroutine dispatchers for the whole app. Anything
 * needing a dispatcher injects it with the [Dispatcher] qualifier rather than
 * referencing [Dispatchers] directly (CONVENTIONS.md §5/§6).
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Dispatcher(GlyphDispatcher.DEFAULT)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Dispatcher(GlyphDispatcher.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(GlyphDispatcher.MAIN)
    fun providesMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

/**
 * Binds the [DispatcherProvider] interface to its production implementation.
 * Kept separate from [DispatchersModule] because @Binds requires an abstract
 * declaration (interface/abstract class), while the dispatcher providers above
 * are concrete @Provides functions.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherProviderModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider,
    ): DispatcherProvider
}
