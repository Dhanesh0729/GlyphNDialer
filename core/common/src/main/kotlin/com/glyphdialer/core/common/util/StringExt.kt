// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common.util

/**
 * Returns only the ASCII digits of this string, dropping spaces, dashes,
 * parentheses, etc. A `+` prefix is NOT preserved (use [dialableChars] when you
 * need the leading plus / DTMF symbols). Useful for T9 indexing and comparing
 * raw number input.
 */
fun String.digitsOnly(): String = filter(Char::isDigit)

/**
 * Keeps the characters that are meaningful to the dialer: digits, a single
 * leading `+`, and the DTMF symbols `*` `#`. Everything else (formatting noise)
 * is stripped. A `+` is only kept if it is the very first surviving character.
 */
fun String.dialableChars(): String {
    val sb = StringBuilder(length)
    for ((index, c) in this.withIndex()) {
        when {
            c.isDigit() || c == '*' || c == '#' -> sb.append(c)
            c == '+' && index == 0 -> sb.append(c)
        }
    }
    return sb.toString()
}

/** True when the string is non-null and contains at least one non-whitespace char. */
fun CharSequence?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()

/**
 * Returns this string, or [fallback] when it is null or blank. Avoids the common
 * `?.takeIf { it.isNotBlank() } ?: fallback` boilerplate when rendering names.
 */
fun String?.orFallback(fallback: String): String =
    if (this.isNullOrBlank()) fallback else this

/**
 * Truncates to at most [maxLength] characters, appending [ellipsis] when cut.
 * The result (including the ellipsis) never exceeds [maxLength]. Used for
 * ticker-tape captions and compact labels.
 */
fun String.ellipsize(maxLength: Int, ellipsis: String = "…"): String {
    require(maxLength >= 0) { "maxLength must be >= 0, was $maxLength" }
    if (length <= maxLength) return this
    if (maxLength <= ellipsis.length) return ellipsis.take(maxLength)
    return take(maxLength - ellipsis.length) + ellipsis
}

/**
 * Uppercases the first letter of each whitespace-separated word (display-only;
 * not locale-perfect for every script, adequate for contact-name presentation).
 */
fun String.titleCaseWords(): String =
    split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

/**
 * Derives up to two uppercase initials from a display name (e.g. "Ada Lovelace"
 * -> "AL", "cher" -> "C"). Empty/blank input yields [placeholder]. Feeds the
 * dot-matrix avatar fallback.
 */
fun String.initials(placeholder: String = "#"): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return placeholder
    val first = words.first().first().uppercaseChar()
    if (words.size == 1) return first.toString()
    val last = words.last().first().uppercaseChar()
    return "$first$last"
}
