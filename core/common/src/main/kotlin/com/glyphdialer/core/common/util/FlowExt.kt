// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.util

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample

/**
 * Emits the first value immediately, then at most one value per [windowMs]
 * window, always favouring the latest. Unlike a debounce (which waits for a
 * quiet period and can starve a continuous stream), this guarantees periodic
 * updates — useful for high-frequency sources like a live audio amplitude that
 * drives [GlyphWaveform]/Glyph rendering.
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.throttleLatest(windowMs: Long): Flow<T> {
    require(windowMs > 0) { "windowMs must be > 0, was $windowMs" }
    return conflate().sample(windowMs)
}

/**
 * Wraps each emission in [AppResult.Success] and converts a terminal exception
 * into a trailing [AppResult.Failure] instead of crashing the collector. Lets a
 * ViewModel render an error UiState from a flow without a try/catch dance.
 * [CancellationException] still propagates (handled by [catch]'s own contract).
 */
fun <T> Flow<T>.asAppResult(): Flow<AppResult<T>> =
    map<T, AppResult<T>> { AppResult.Success(it) }
        .catch { emit(AppResult.Failure(it, it.message)) }

/** Maps the success branch of a `Flow<AppResult<T>>`, leaving failures intact. */
fun <T, R> Flow<AppResult<T>>.mapResult(transform: (T) -> R): Flow<AppResult<R>> =
    map { it.map(transform) }

/**
 * Prepends [initial] to the stream so collectors get an immediate value before
 * the upstream produces one (e.g. a loading placeholder).
 */
fun <T> Flow<T>.startWith(initial: T): Flow<T> = onStart { emit(initial) }

/**
 * Concatenates [other] after this flow completes (this fully, then other).
 * Handy for "cached then fresh" or "header then page" sequencing.
 */
fun <T> Flow<T>.concatWith(other: Flow<T>): Flow<T> = flow {
    emitAll(this@concatWith)
    emitAll(other)
}

/**
 * A latest-wins buffer: keeps the producer from suspending while ensuring the
 * collector only ever sees the most recent item if it falls behind. Equivalent
 * intent to [conflate] but explicit about overflow policy for readability.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.bufferLatest(): Flow<T> =
    buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
