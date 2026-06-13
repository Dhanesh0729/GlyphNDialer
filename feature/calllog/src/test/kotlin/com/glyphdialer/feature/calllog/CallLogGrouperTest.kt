// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog

import com.glyphdialer.core.domain.model.CallLogEntry
import com.glyphdialer.core.domain.model.CallType
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import com.glyphdialer.core.domain.usecase.FormatNumberUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Pure-logic unit tests for the call-log grouping + relative-time formatting
 * (CONVENTIONS.md §11). No Android/Compose types involved.
 */
class CallLogGrouperTest {

    private val formatNumber = FormatNumberUseCase(IdentityFormatter())
    private val grouper = CallLogGrouper(formatNumber)
    private val now = 1_000_000_000_000L

    @Test
    fun `adjacent calls with same number collapse into one group with count`() {
        val entries = listOf(
            entry(id = 3, number = "+14155550142", type = CallType.MISSED, ts = now - min(1)),
            entry(id = 2, number = "+14155550142", type = CallType.MISSED, ts = now - min(2)),
            entry(id = 1, number = "+14155550142", type = CallType.MISSED, ts = now - min(3)),
        )

        val groups = grouper.group(entries, now)

        assertThat(groups).hasSize(1)
        assertThat(groups[0].id).isEqualTo(3L)
        assertThat(groups[0].count).isEqualTo(3)
        assertThat(groups[0].entryIds).containsExactly(3L, 2L, 1L).inOrder()
        assertThat(groups[0].dominantType).isEqualTo(CallType.MISSED)
    }

    @Test
    fun `interleaved counterparts are not merged across a gap`() {
        val entries = listOf(
            entry(id = 4, number = "+1AAA", type = CallType.OUTGOING, ts = now - min(1)),
            entry(id = 3, number = "+1BBB", type = CallType.INCOMING, ts = now - min(2)),
            entry(id = 2, number = "+1AAA", type = CallType.OUTGOING, ts = now - min(3)),
        )

        val groups = grouper.group(entries, now)

        // A → B → A yields three separate, chronological rows.
        assertThat(groups).hasSize(3)
        assertThat(groups.map { it.id }).containsExactly(4L, 3L, 2L).inOrder()
    }

    @Test
    fun `contact lookup key merges different numbers of the same person`() {
        val entries = listOf(
            entry(id = 2, number = "+1HOME", type = CallType.INCOMING, ts = now - min(1), lookupKey = "ada"),
            entry(id = 1, number = "+1MOBILE", type = CallType.OUTGOING, ts = now - min(2), lookupKey = "ada"),
        )

        val groups = grouper.group(entries, now)

        assertThat(groups).hasSize(1)
        assertThat(groups[0].count).isEqualTo(2)
    }

    @Test
    fun `pre-grouped groupCount is honored alongside adjacency merge`() {
        val entries = listOf(
            entry(id = 1, number = "+1X", type = CallType.MISSED, ts = now, groupCount = 3),
        )

        val groups = grouper.group(entries, now)

        assertThat(groups).hasSize(1)
        assertThat(groups[0].count).isEqualTo(3)
    }

    @Test
    fun `empty input yields empty output`() {
        assertThat(grouper.group(emptyList(), now)).isEmpty()
    }

    @Test
    fun `relative time buckets format as expected`() {
        assertThat(CallLogGrouper.relativeTime(now, now)).isEqualTo("now")
        assertThat(CallLogGrouper.relativeTime(now - min(5), now)).isEqualTo("5m")
        assertThat(CallLogGrouper.relativeTime(now - hours(3), now)).isEqualTo("3h")
        assertThat(CallLogGrouper.relativeTime(now - days(1), now)).isEqualTo("Yesterday")
        assertThat(CallLogGrouper.relativeTime(now - days(3), now)).isEqualTo("3d")
        assertThat(CallLogGrouper.relativeTime(now - days(14), now)).isEqualTo("2w")
    }

    @Test
    fun `future timestamps clamp to now`() {
        assertThat(CallLogGrouper.relativeTime(now + min(5), now)).isEqualTo("now")
    }

    // ---- helpers ----------------------------------------------------------

    private fun entry(
        id: Long,
        number: String,
        type: CallType,
        ts: Long,
        lookupKey: String? = null,
        groupCount: Int = 1,
    ) = CallLogEntry(
        id = id,
        number = PhoneNumber(raw = number, normalized = number, formatted = number),
        contactLookupKey = lookupKey,
        type = type,
        timestampMillis = ts,
        groupCount = groupCount,
    )

    private fun min(m: Long) = TimeUnit.MINUTES.toMillis(m)
    private fun hours(h: Long) = TimeUnit.HOURS.toMillis(h)
    private fun days(d: Long) = TimeUnit.DAYS.toMillis(d)

    /** A trivial formatter that echoes the input — keeps grouping the unit under test. */
    private class IdentityFormatter : PhoneNumberFormatter {
        override val defaultRegion: String = "US"
        override fun format(raw: String, region: String): String = raw
        override fun toE164(raw: String, region: String): String? = raw
        override fun isValid(raw: String, region: String): Boolean = true
        override fun toPhoneNumber(raw: String, region: String): PhoneNumber =
            PhoneNumber(raw = raw, normalized = raw, formatted = raw)
        override fun formatAsYouType(input: String, region: String): String = input
    }
}
