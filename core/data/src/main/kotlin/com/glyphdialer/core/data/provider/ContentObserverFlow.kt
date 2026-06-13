// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.provider

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits [Unit] immediately and again whenever the content at [uri] changes, via a
 * [ContentObserver] (CONVENTIONS.md §5 — provider reads are observable Flows). The
 * observer is unregistered when collection stops.
 *
 * Collectors typically `mapLatest`/`flatMapLatest` each tick into a fresh provider
 * query and `conflate()` to coalesce bursts.
 */
internal fun ContentResolver.observeChanges(
    uri: Uri,
    notifyForDescendants: Boolean = true,
): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    registerContentObserver(uri, notifyForDescendants, observer)
    // Prime the flow so collectors get an initial snapshot without waiting for a change.
    trySend(Unit)
    awaitClose { unregisterContentObserver(observer) }
}
