// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.detail

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the recent-interactions relative-time formatter (CONVENTIONS.md §11).
 */
class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    private fun ago(value: Long, unit: TimeUnit) = now - unit.toMillis(value)

    @Test
    fun `under a minute reads just now`() {
        assertThat(RelativeTime.format(ago(30, TimeUnit.SECONDS), now)).isEqualTo("Just now")
    }

    @Test
    fun `minutes hours days weeks years scale correctly`() {
        assertThat(RelativeTime.format(ago(5, TimeUnit.MINUTES), now)).isEqualTo("5m ago")
        assertThat(RelativeTime.format(ago(3, TimeUnit.HOURS), now)).isEqualTo("3h ago")
        assertThat(RelativeTime.format(ago(2, TimeUnit.DAYS), now)).isEqualTo("2d ago")
        assertThat(RelativeTime.format(ago(14, TimeUnit.DAYS), now)).isEqualTo("2w ago")
        assertThat(RelativeTime.format(ago(800, TimeUnit.DAYS), now)).isEqualTo("2y ago")
    }

    @Test
    fun `future timestamps clamp to just now rather than going negative`() {
        assertThat(RelativeTime.format(now + 10_000, now)).isEqualTo("Just now")
    }

    @Test
    fun `duration formats as m colon ss`() {
        assertThat(RelativeTime.formatDuration(0)).isEqualTo("0:00")
        assertThat(RelativeTime.formatDuration(9)).isEqualTo("0:09")
        assertThat(RelativeTime.formatDuration(75)).isEqualTo("1:15")
        assertThat(RelativeTime.formatDuration(605)).isEqualTo("10:05")
    }
}
