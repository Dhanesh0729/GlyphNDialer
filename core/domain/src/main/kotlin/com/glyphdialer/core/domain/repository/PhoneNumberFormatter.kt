// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.domain.model.PhoneNumber

/**
 * Region-aware phone-number formatting/validation (§8). The impl in `:core:data`
 * wraps libphonenumber. Kept synchronous (pure CPU work) — no [AppResult] since
 * formatting falls back gracefully to the raw input rather than failing.
 */
interface PhoneNumberFormatter {

    /** The default region ISO code (e.g. "US") inferred from SIM/locale. */
    val defaultRegion: String

    /**
     * Format [raw] for display in [region] (defaults to [defaultRegion]). Returns
     * the input unchanged if it can't be parsed.
     */
    fun format(raw: String, region: String = defaultRegion): String

    /** Normalize [raw] to E.164 (e.g. "+14155552671"), or null if not parseable. */
    fun toE164(raw: String, region: String = defaultRegion): String?

    /** Whether [raw] is a valid number for [region]. */
    fun isValid(raw: String, region: String = defaultRegion): Boolean

    /**
     * Build a fully-populated [PhoneNumber] (raw + normalized + formatted) from
     * [raw], inferring against [region].
     */
    fun toPhoneNumber(raw: String, region: String = defaultRegion): PhoneNumber

    /**
     * Incremental "as-you-type" formatting for the dialpad input (§8). Feed the
     * full current input each keystroke; returns the prettified rendering.
     */
    fun formatAsYouType(input: String, region: String = defaultRegion): String
}
