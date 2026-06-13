// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * A blocked number, backed by [android.provider.BlockedNumberContract] when the
 * app is the default dialer, mirrored into Room for fast local checks.
 */
data class BlockedNumber(
    val id: Long = 0L,
    val number: PhoneNumber,
    val createdAtMillis: Long = 0L,
    /** True when the user also chose "block & report as spam". */
    val reportedAsSpam: Boolean = false,
)
