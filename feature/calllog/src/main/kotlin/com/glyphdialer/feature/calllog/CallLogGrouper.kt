// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.usecase.FormatNumberUseCase
import java.util.concurrent.TimeUnit

/**
 * Pure, dependency-light grouping + relative-time logic for the call log
 * (BUILD_SPEC §8 — "grouped by contact + relative time"). Kept out of the
 * ViewModel and free of Android/Compose types so it is straightforwardly
 * unit-testable (CONVENTIONS.md §11).
 *
 * Display formatting goes through [FormatNumberUseCase] (BUILD_SPEC §8 — the
 * feature uses the use case, which wraps libphonenumber in `:core:data`).
 *
 * Grouping rule (mirrors Google Dialer): walk the time-ordered list and collapse
 * *adjacent* entries that share the same counterpart key. We only merge runs that
 * are contiguous in time so an "X → Y → X" sequence yields three rows, not two —
 * matching user expectation that history reads chronologically.
 */
internal class CallLogGrouper(
    private val formatNumber: FormatNumberUseCase,
) {

    /**
     * Collapse [entries] (assumed newest-first, as the repository emits) into
     * display-ready [CallLogGroup]s. [now] is injected so relative-time formatting
     * is deterministic and testable.
     */
    fun group(entries: List<CallLogEntry>, now: Long): List<CallLogGroup> {
        if (entries.isEmpty()) return emptyList()

        val out = ArrayList<CallLogGroup>(entries.size)
        var i = 0
        while (i < entries.size) {
            val head = entries[i]
            val key = counterpartKey(head)
            val ids = ArrayList<Long>()
            ids.add(head.id)

            // Absorb the contiguous run sharing the same counterpart.
            var j = i + 1
            while (j < entries.size && counterpartKey(entries[j]) == key) {
                ids.add(entries[j].id)
                j++
            }

            out.add(toGroup(head, ids, now))
            i = j
        }
        return out
    }

    /**
     * The identity used to merge adjacent entries. Prefer the contact lookup key
     * (so a person's multiple numbers still collapse), else the normalized number,
     * else the raw string.
     */
    private fun counterpartKey(entry: CallLogEntry): String =
        entry.contactLookupKey
            ?: entry.number.normalized
            ?: entry.number.raw

    private fun toGroup(head: CallLogEntry, ids: List<Long>, now: Long): CallLogGroup {
        val formatted = runCatching { formatNumber(head.number.dialValue) }
            .getOrDefault(head.number.formatted)
            .ifBlank { head.number.formatted }
        // Honor any pre-grouping the repository already did (groupCount) plus our
        // own adjacency merge, so a single emitted "x3" row still reads as three.
        val count = (head.groupCount.coerceAtLeast(1)) + (ids.size - 1)
        return CallLogGroup(
            id = head.id,
            entryIds = ids,
            displayName = head.displayName,
            number = head.number.dialValue,
            formattedNumber = formatted,
            photoUri = head.photoUri,
            dominantType = head.type,
            timestampMillis = head.timestampMillis,
            relativeTime = relativeTime(head.timestampMillis, now),
            count = count,
            durationSeconds = head.durationSeconds,
            isVoip = head.isVoip,
            isVideo = head.isVideo,
            spamLabel = head.spamLabel,
        )
    }

    companion object {
        /**
         * A compact, locale-light relative-time label ("now", "5m", "3h",
         * "Yesterday", "Mon", "12 Jan"). Pure and testable; the UI may prepend an
         * accessibility-friendly full timestamp separately.
         */
        fun relativeTime(timestampMillis: Long, now: Long): String {
            val delta = (now - timestampMillis).coerceAtLeast(0L)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
            val hours = TimeUnit.MILLISECONDS.toHours(delta)
            val days = TimeUnit.MILLISECONDS.toDays(delta)
            return when {
                minutes < 1L -> "now"
                minutes < 60L -> "${minutes}m"
                hours < 24L -> "${hours}h"
                days == 1L -> "Yesterday"
                days < 7L -> "${days}d"
                days < 365L -> "${days / 7L}w"
                else -> "${days / 365L}y"
            }
        }
    }
}
