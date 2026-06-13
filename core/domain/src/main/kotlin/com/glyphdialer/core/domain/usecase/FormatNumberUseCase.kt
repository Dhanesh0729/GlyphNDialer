// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import javax.inject.Inject

/**
 * Region-aware number formatting (§8). Delegates to [PhoneNumberFormatter]
 * (libphonenumber in `:core:data`). Synchronous because formatting is pure CPU work
 * that gracefully falls back to the raw input.
 */
class FormatNumberUseCase @Inject constructor(
    private val formatter: PhoneNumberFormatter,
) {
    /** Pretty, display-ready string for [raw] in [region] (defaults to inferred). */
    operator fun invoke(raw: String, region: String = formatter.defaultRegion): String =
        formatter.format(raw, region)

    /** As-you-type formatting for the dialpad input field (§8). */
    fun asYouType(input: String, region: String = formatter.defaultRegion): String =
        formatter.formatAsYouType(input, region)

    /** Build a fully-populated [PhoneNumber] (raw + E.164 + formatted). */
    fun toModel(raw: String, region: String = formatter.defaultRegion): PhoneNumber =
        formatter.toPhoneNumber(raw, region)
}
