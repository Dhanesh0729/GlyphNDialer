// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail

import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.Transcript
import com.glyphdialer.core.domain.model.Voicemail
import com.glyphdialer.feature.voicemail.component.formatDuration
import com.glyphdialer.feature.voicemail.component.formatMillis
import com.glyphdialer.feature.voicemail.component.relativeTime
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for the pure presentation logic in :feature:voicemail (CONVENTIONS.md
 * §11). Covers duration/relative-time formatting, the playback-progress math, and the
 * "best caption" / honesty derivations on [VoicemailItem].
 */
class VoicemailFormattingTest {

    @ParameterizedTest
    @CsvSource(
        "0, 0:00",
        "5, 0:05",
        "59, 0:59",
        "60, 1:00",
        "75, 1:15",
        "3661, 61:01",
        "-3, 0:00",
    )
    fun `formatDuration renders M colon SS`(seconds: Long, expected: String) {
        assertThat(formatDuration(seconds)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "0, 0:00",
        "1000, 0:01",
        "65000, 1:05",
        "-500, 0:00",
    )
    fun `formatMillis renders M colon SS`(millis: Long, expected: String) {
        assertThat(formatMillis(millis)).isEqualTo(expected)
    }

    @Test
    fun `relativeTime buckets deltas`() {
        val now = 10_000_000_000L
        assertThat(relativeTime(now, now)).isEqualTo("now")
        assertThat(relativeTime(now - 5 * 60_000L, now)).isEqualTo("5m")
        assertThat(relativeTime(now - 3 * 3_600_000L, now)).isEqualTo("3h")
        assertThat(relativeTime(now - 2 * 86_400_000L, now)).isEqualTo("2d")
        assertThat(relativeTime(now - 14 * 86_400_000L, now)).isEqualTo("2w")
    }

    @Test
    fun `playback progress is zero when duration unknown`() {
        val pb = PlaybackState(positionMillis = 5_000, durationMillis = 0)
        assertThat(pb.progress).isEqualTo(0f)
    }

    @Test
    fun `playback progress clamps to one`() {
        val pb = PlaybackState(positionMillis = 30_000, durationMillis = 10_000)
        assertThat(pb.progress).isEqualTo(1f)
    }

    @Test
    fun `playback progress is fractional mid-clip`() {
        val pb = PlaybackState(positionMillis = 5_000, durationMillis = 20_000)
        assertThat(pb.progress).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun `displayTranscription prefers engine transcript over carrier text`() {
        val item = VoicemailItem(
            voicemail = voicemail(transcriptionText = "carrier text"),
            transcript = transcript(fullText = "engine text"),
        )
        assertThat(item.displayTranscription).isEqualTo("engine text")
    }

    @Test
    fun `displayTranscription falls back to carrier text`() {
        val item = VoicemailItem(voicemail = voicemail(transcriptionText = "carrier text"))
        assertThat(item.displayTranscription).isEqualTo("carrier text")
    }

    @Test
    fun `displayTranscription is null when nothing available`() {
        val item = VoicemailItem(voicemail = voicemail(transcriptionText = null))
        assertThat(item.displayTranscription).isNull()
    }

    @Test
    fun `local-side-only flag is surfaced from transcript`() {
        val item = VoicemailItem(
            voicemail = voicemail(),
            transcript = transcript(fullText = "x", localSideOnly = true),
        )
        assertThat(item.transcriptionIsLocalSideOnly).isTrue()
    }

    @Test
    fun `unread count reflects unread items`() {
        val state = VoicemailUiState(
            support = VvmSupportState.SUPPORTED,
            items = listOf(
                VoicemailItem(voicemail = voicemail(id = 1, read = false)),
                VoicemailItem(voicemail = voicemail(id = 2, read = true)),
                VoicemailItem(voicemail = voicemail(id = 3, read = false)),
            ),
        )
        assertThat(state.unreadCount).isEqualTo(2)
        assertThat(state.isEmpty).isFalse()
    }

    private fun voicemail(
        id: Long = 1,
        read: Boolean = false,
        transcriptionText: String? = null,
    ) = Voicemail(
        id = id,
        number = PhoneNumber(raw = "+14155550100"),
        timestampMillis = 0L,
        isRead = read,
        transcriptionText = transcriptionText,
        transcriptId = if (transcriptionText != null) null else null,
    )

    private fun transcript(
        fullText: String,
        localSideOnly: Boolean = false,
    ) = Transcript(
        id = "t1",
        fullText = fullText,
        isLocalSideOnly = localSideOnly,
    )
}
