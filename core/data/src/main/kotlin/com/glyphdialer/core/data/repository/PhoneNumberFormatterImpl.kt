// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.repository

import android.content.Context
import android.telephony.TelephonyManager
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.domain.model.NumberLabel
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * libphonenumber-backed [PhoneNumberFormatter] (CONVENTIONS.md §6, BUILD_SPEC §8).
 *
 * Synchronous by design: all operations are pure CPU work and fall back gracefully
 * to the raw input rather than throwing, so no [com.glyphdialer.core.common.AppResult]
 * is needed. The default region is inferred from the SIM, then the locale, then the
 * [Constants.DEFAULT_REGION] fallback.
 */
@Singleton
class PhoneNumberFormatterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhoneNumberFormatter {

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    override val defaultRegion: String by lazy { resolveDefaultRegion() }

    private fun resolveDefaultRegion(): String {
        val fromSim = runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            // simCountryIso is most reliable; networkCountryIso as a secondary signal.
            tm?.simCountryIso?.takeIf { it.isNotBlank() }
                ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val region = (fromSim ?: Locale.getDefault().country)
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            ?: Constants.DEFAULT_REGION

        return region
    }

    override fun format(raw: String, region: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return raw
        return try {
            val parsed = util.parse(cleaned, region)
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        } catch (e: NumberParseException) {
            Timber.v("format: cannot parse '%s' (region=%s): %s", cleaned, region, e.errorType)
            raw
        }
    }

    override fun toE164(raw: String, region: String): String? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        return try {
            val parsed = util.parse(cleaned, region)
            if (util.isValidNumber(parsed)) {
                util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (e: NumberParseException) {
            null
        }
    }

    override fun isValid(raw: String, region: String): Boolean = try {
        util.isValidNumber(util.parse(raw.trim(), region))
    } catch (e: NumberParseException) {
        false
    }

    override fun toPhoneNumber(raw: String, region: String): PhoneNumber {
        val normalized = toE164(raw, region)
        val formatted = format(raw, region)
        return PhoneNumber(
            raw = raw,
            normalized = normalized,
            formatted = formatted,
            label = NumberLabel.OTHER,
        )
    }

    override fun formatAsYouType(input: String, region: String): String {
        if (input.isEmpty()) return input
        val formatter = util.getAsYouTypeFormatter(region)
        var result = ""
        // The AsYouType formatter is stateful: feed every char in order.
        for (ch in input) {
            result = formatter.inputDigit(ch)
        }
        return result
    }
}
