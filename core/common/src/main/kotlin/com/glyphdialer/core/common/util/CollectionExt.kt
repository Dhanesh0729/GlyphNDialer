// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.util

/** True when the collection is non-null and contains at least one element. */
fun <T> Collection<T>?.isNotNullOrEmpty(): Boolean = !this.isNullOrEmpty()

/**
 * Returns the element at [index], or `null` when [index] is out of bounds —
 * a non-throwing companion to [List.get].
 */
fun <T> List<T>.getOrNullAt(index: Int): T? = if (index in indices) this[index] else null

/**
 * Splits the list into chunks of [size], the last possibly smaller. Differs
 * from stdlib [chunked] only in name-clarity at call sites that already use
 * "window"/"chunk" for other purposes; delegates to the stdlib implementation.
 */
fun <T> List<T>.chunkedBy(size: Int): List<List<T>> {
    require(size > 0) { "size must be > 0, was $size" }
    return chunked(size)
}

/**
 * Returns a new list with [item] toggled: removed if present (by equality),
 * appended otherwise. Convenient for selection sets in UiState without mutating.
 */
fun <T> List<T>.toggle(item: T): List<T> =
    if (contains(item)) filterNot { it == item } else this + item

/**
 * Replaces the first element matching [predicate] with [replacement], returning
 * a new list. If nothing matches, returns the original list unchanged. Useful
 * for immutable UiState updates of a single row.
 */
inline fun <T> List<T>.replaceFirst(replacement: T, predicate: (T) -> Boolean): List<T> {
    val index = indexOfFirst(predicate)
    if (index < 0) return this
    return toMutableList().also { it[index] = replacement }
}

/**
 * Groups elements by [keySelector] while preserving first-seen key order
 * (unlike [groupBy], whose ordering is the backing LinkedHashMap's — which is
 * also insertion order, but this makes the intent explicit and returns a List of
 * pairs ready for sectioned UIs like an alphabetised contact list).
 */
inline fun <T, K> Iterable<T>.groupByOrdered(keySelector: (T) -> K): List<Pair<K, List<T>>> {
    val map = LinkedHashMap<K, MutableList<T>>()
    for (element in this) {
        map.getOrPut(keySelector(element)) { mutableListOf() }.add(element)
    }
    return map.map { (key, value) -> key to value }
}

/**
 * Returns the most common element and its count, or `null` for an empty
 * iterable. Ties resolve to the first-encountered value. Handy for deriving a
 * "primary number" from call-log rows.
 */
fun <T> Iterable<T>.mostCommonOrNull(): Pair<T, Int>? =
    groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.toPair()
