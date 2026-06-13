// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * The shared, throw-free result type for fallible operations that cross layer
 * boundaries (see CONVENTIONS.md §5/§6). Domain/data layers should return
 * [AppResult] instead of throwing so callers can handle failures explicitly.
 *
 * It is intentionally NOT a typealias for [kotlin.Result] so we can carry a
 * human-meaningful [Failure.message] alongside the [Failure.error] and keep the
 * API stable across the whole app.
 */
sealed interface AppResult<out T> {

    /** A successful outcome carrying [data]. */
    data class Success<out T>(val data: T) : AppResult<T>

    /**
     * A failed outcome. [error] is the underlying cause; [message] is an
     * optional, presentable explanation (may be surfaced in the UI).
     */
    data class Failure(
        val error: Throwable,
        val message: String? = null,
    ) : AppResult<Nothing>

    /** True when this is [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** True when this is [Failure]. */
    val isFailure: Boolean get() = this is Failure
}

/**
 * Runs [block], wrapping a normal return in [AppResult.Success] and any thrown
 * [Throwable] in [AppResult.Failure]. [CancellationException] is re-thrown so
 * coroutine cancellation is never swallowed.
 */
inline fun <T> appResultOf(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        AppResult.Failure(t, t.message)
    }

/**
 * Suspending sibling of [appResultOf] for `suspend` blocks. Same cancellation
 * semantics: [CancellationException] propagates, everything else is captured.
 */
suspend inline fun <T> appResultOfSuspend(crossinline block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        AppResult.Failure(t, t.message)
    }

/**
 * Transforms a [AppResult.Success] value with [transform], leaving a
 * [AppResult.Failure] untouched. If [transform] throws, the result becomes a
 * [AppResult.Failure].
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> appResultOf { transform(data) }
        is AppResult.Failure -> this
    }

/**
 * Chains another fallible operation: maps a [AppResult.Success] through
 * [transform] (which itself returns an [AppResult]), propagating failures.
 */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(data)
        is AppResult.Failure -> this
    }

/** Returns the success value or `null` for a [AppResult.Failure]. */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** Returns the success value or [default] for a [AppResult.Failure]. */
fun <T> AppResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

/** Returns the success value or the result of [onFailure] for a failure. */
inline fun <T> AppResult<T>.getOrElse(onFailure: (AppResult.Failure) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> onFailure(this)
    }

/** Returns the [Throwable] for a [AppResult.Failure], else `null`. */
fun AppResult<*>.errorOrNull(): Throwable? = (this as? AppResult.Failure)?.error

/** Invokes [action] with the value when this is a [AppResult.Success]; returns `this`. */
@OptIn(ExperimentalContracts::class)
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (this is AppResult.Success) action(data)
    return this
}

/** Invokes [action] with the failure when this is a [AppResult.Failure]; returns `this`. */
@OptIn(ExperimentalContracts::class)
inline fun <T> AppResult<T>.onFailure(action: (AppResult.Failure) -> Unit): AppResult<T> {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (this is AppResult.Failure) action(this)
    return this
}

/** Collapses both branches into a single [R] via [onSuccess]/[onFailure]. */
inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppResult.Failure) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Failure -> onFailure(this)
}

/** Bridges a [kotlin.Result] into an [AppResult]. */
fun <T> Result<T>.toAppResult(): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Failure(it, it.message) },
    )
