// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.dispatchers

import javax.inject.Qualifier

/**
 * Identifies one of the three coroutine dispatchers the app injects. Dispatchers
 * are NEVER hard-coded; consumers inject a qualified [kotlinx.coroutines.CoroutineDispatcher]
 * (see CONVENTIONS.md §5/§6).
 */
enum class GlyphDispatcher {
    /** CPU-bound work (parsing, sorting, T9 indexing). */
    DEFAULT,

    /** Blocking I/O (Room, DataStore, content providers, files, network). */
    IO,

    /** The Android main/UI thread. */
    MAIN,
}

/**
 * Hilt qualifier selecting which [GlyphDispatcher] to inject.
 *
 * Usage:
 * ```
 * class Foo @Inject constructor(
 *     @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
 * )
 * ```
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Dispatcher(val type: GlyphDispatcher)
