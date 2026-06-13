// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * A single phone number associated with a [Contact] (or standing alone).
 *
 * [normalized] is the E.164 representation when it could be computed (e.g.
 * "+14155552671"); it may be null when the raw input couldn't be parsed against
 * a known region. [formatted] is a human-friendly, region-aware rendering for
 * display (computed via the [com.glyphdialer.core.domain.repository.PhoneNumberFormatter]).
 */
data class PhoneNumber(
    val raw: String,
    val normalized: String? = null,
    val formatted: String = raw,
    val label: NumberLabel = NumberLabel.OTHER,
    /** A free-text label when [label] is [NumberLabel.CUSTOM]. */
    val customLabel: String? = null,
    val isPrimary: Boolean = false,
) {
    /** Best identifier for matching/dialing: E.164 if known, else the raw string. */
    val dialValue: String get() = normalized ?: raw
}

/** Standard phone-number type labels (mirrors ContactsContract.CommonDataKinds.Phone). */
enum class NumberLabel {
    MOBILE,
    HOME,
    WORK,
    MAIN,
    FAX_WORK,
    FAX_HOME,
    PAGER,
    OTHER,
    CUSTOM,
}
