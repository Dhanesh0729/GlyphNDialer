// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates the three coroutine dispatchers behind a single injectable object.
 *
 * Prefer injecting individual qualified dispatchers (via [Dispatcher]) where you
 * need only one; inject [DispatcherProvider] when a class touches several (e.g. a
 * repository that does IO then maps on default). Either way, no class hard-codes
 * [Dispatchers], which keeps everything swappable in tests (CONVENTIONS.md §5).
 */
interface DispatcherProvider {
    /** Blocking I/O — Room, DataStore, content providers, files, network. */
    val io: CoroutineDispatcher

    /** CPU-bound work — parsing, sorting, T9 indexing. */
    val default: CoroutineDispatcher

    /** The Android main/UI thread. */
    val main: CoroutineDispatcher
}

/**
 * Production [DispatcherProvider] backed by [kotlinx.coroutines.Dispatchers].
 * Tests substitute a provider built from a single `StandardTestDispatcher`.
 */
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}
