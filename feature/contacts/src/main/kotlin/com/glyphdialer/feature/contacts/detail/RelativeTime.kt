// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import java.util.concurrent.TimeUnit

/**
 * Tiny, pure relative-time formatter for the recent-interactions timeline. Kept
 * local (features don't depend on each other, §3) and dependency-free so it unit-tests
 * without Android. Intentionally coarse — the detail screen only needs an at-a-glance
 * "when", not a precise clock.
 */
internal object RelativeTime {

    /** Format [timestampMillis] relative to [now] as "Just now" / "5m ago" / "3d ago" / "12w ago". */
    fun format(timestampMillis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = (now - timestampMillis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            days < 365 -> "${days / 7}w ago"
            else -> "${days / 365}y ago"
        }
    }

    /** Format a call duration in seconds as "m:ss" (or "0:00" for none). */
    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val m = safe / 60
        val s = safe % 60
        return "%d:%02d".format(m, s)
    }
}
