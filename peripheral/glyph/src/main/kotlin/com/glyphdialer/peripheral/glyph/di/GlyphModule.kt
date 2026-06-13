// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph.di

import android.content.Context
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.peripheral.glyph.GdkGlyphController
import com.glyphdialer.peripheral.glyph.GlyphAvailability
import com.glyphdialer.peripheral.glyph.GlyphHardware
import com.glyphdialer.peripheral.glyph.GlyphMatrixController
import com.glyphdialer.peripheral.glyph.NoOpGlyphController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt wiring for the Glyph peripheral (CONVENTIONS.md §5/§6).
 *
 * Provides the single app-wide [GlyphController]:
 *  - [GdkGlyphController] on supported light-strip Nothing phones,
 *  - [GlyphMatrixController] on the Phone (3) Glyph Matrix,
 *  - [NoOpGlyphController] everywhere else (the universal §9/§17.6 fallback).
 *
 * Selection is driven by [GlyphAvailability]; the choice is made ONCE as a
 * `@Singleton` so the GDK session lifecycle is owned in one place. Detection never
 * throws, so a non-Nothing device always lands on the no-op without crashing.
 */
@Module
@InstallIn(SingletonComponent::class)
object GlyphModule {

    @Provides
    @Singleton
    fun provideGlyphAvailability(
        @ApplicationContext context: Context,
    ): GlyphAvailability = GlyphAvailability(context)

    @Provides
    @Singleton
    fun provideGlyphController(
        @ApplicationContext context: Context,
        availability: GlyphAvailability,
        @Dispatcher(GlyphDispatcher.DEFAULT) dispatcher: CoroutineDispatcher,
    ): GlyphController {
        val capability = availability.detect()
        if (!capability.supported) {
            Timber.tag(TAG).i("Glyph unavailable: %s", capability.unavailableReason)
            return NoOpGlyphController(capability.unavailableReason)
        }
        return when (capability.hardware) {
            GlyphHardware.LIGHT_STRIP -> GdkGlyphController(context, capability, dispatcher)
            GlyphHardware.MATRIX -> GlyphMatrixController(context, capability, dispatcher)
            GlyphHardware.NONE -> NoOpGlyphController(capability.unavailableReason)
        }
    }

    private const val TAG = "GlyphModule"
}
