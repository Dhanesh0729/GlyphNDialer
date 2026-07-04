// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.di

import android.content.Context
import androidx.work.WorkManager
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.data.repository.GlyphAvailabilityProbe
import com.glyphdialer.peripheral.glyph.GlyphAvailability
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
 * Qualifier for the application-wide [CoroutineScope] — a long-lived scope tied to the
 * process, used for fire-and-forget work that must outlive any single screen
 * (CONVENTIONS.md §5). Distinct from injected dispatchers.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * App-level Hilt bindings not covered by a module's own DI (CONVENTIONS.md §3).
 *
 * Everything feature/peripheral-specific (the [com.glyphdialer.core.domain.glyph.GlyphController],
 * repositories, use cases, dispatchers) is already provided by its owning module's
 * Hilt module — those modules merely need to be on the `:app` classpath (they are, via
 * the project dependencies in build.gradle.kts). We only add the genuinely app-scoped
 * primitives here: [WorkManager] and the application [CoroutineScope].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** The process-wide [WorkManager] used to schedule the purge worker. */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /**
     * A long-lived application [CoroutineScope] backed by a [SupervisorJob] on the IO
     * dispatcher (injected from :core:common, never hard-coded — §5). Lives for the
     * whole process, so it is intentionally never cancelled.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @Dispatcher(GlyphDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Provides
    @Singleton
    fun provideGlyphAvailabilityProbe(
        availability: GlyphAvailability
    ): GlyphAvailabilityProbe = object : GlyphAvailabilityProbe {
        override fun isGlyphAvailable(): Boolean = availability.detect().supported
    }
}
