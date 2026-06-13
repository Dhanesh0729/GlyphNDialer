// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.RetentionWindow
import com.glyphdialer.core.domain.repository.RecordingRepository
import com.glyphdialer.core.domain.repository.TranscriptRepository
import com.glyphdialer.core.domain.usecase.PurgeOldDataUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Retention/purge date math (CONVENTIONS.md §11). The cutoff arithmetic lives in
 * [RetentionWindow.cutoffMillis]; these tests pin it and verify the
 * [PurgeOldDataUseCase] forwards exactly that cutoff to both repositories.
 */
class RetentionPurgeTest {

    private val nowFixed = 1_700_000_000_000L // fixed wall clock for determinism

    @ParameterizedTest(name = "{0} days -> cutoff = now - days*86400000")
    @CsvSource(
        "DAYS_30, 30",
        "DAYS_90, 90",
        "DAYS_180, 180",
    )
    fun `cutoffMillis subtracts exactly the window in days`(name: String, days: Int) {
        val window = RetentionWindow.valueOf(name)
        val expected = nowFixed - days * RetentionWindow.MILLIS_PER_DAY
        assertThat(window.cutoffMillis(nowFixed)).isEqualTo(expected)
    }

    @Test
    fun `NEVER window yields no cutoff`() {
        assertThat(RetentionWindow.NEVER.cutoffMillis(nowFixed)).isNull()
    }

    @Test
    fun `purge forwards the computed cutoff to both repositories`() = runTest {
        val recordings = mockk<RecordingRepository>()
        val transcripts = mockk<TranscriptRepository>()
        val recCutoff = slot<Long>()
        val transCutoff = slot<Long>()
        coEvery { recordings.purgeOlderThan(capture(recCutoff)) } returns AppResult.Success(3)
        coEvery { transcripts.purgeOlderThan(capture(transCutoff)) } returns AppResult.Success(5)

        val useCase = PurgeOldDataUseCase(recordings, transcripts)
        val result = useCase(RetentionWindow.DAYS_90, nowFixed)

        val expectedCutoff = nowFixed - 90 * RetentionWindow.MILLIS_PER_DAY
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val data = (result as AppResult.Success).data
        assertThat(data.recordingsDeleted).isEqualTo(3)
        assertThat(data.transcriptsDeleted).isEqualTo(5)
        assertThat(data.total).isEqualTo(8)
        assertThat(recCutoff.captured).isEqualTo(expectedCutoff)
        assertThat(transCutoff.captured).isEqualTo(expectedCutoff)
    }

    @Test
    fun `NEVER window purges nothing and never touches the repositories`() = runTest {
        val recordings = mockk<RecordingRepository>(relaxed = true)
        val transcripts = mockk<TranscriptRepository>(relaxed = true)

        val useCase = PurgeOldDataUseCase(recordings, transcripts)
        val result = useCase(RetentionWindow.NEVER, nowFixed)

        assertThat((result as AppResult.Success).data.total).isEqualTo(0)
        coVerify(exactly = 0) { recordings.purgeOlderThan(any()) }
        coVerify(exactly = 0) { transcripts.purgeOlderThan(any()) }
    }

    @Test
    fun `a repository failure short-circuits the purge`() = runTest {
        val recordings = mockk<RecordingRepository>()
        val transcripts = mockk<TranscriptRepository>(relaxed = true)
        coEvery { recordings.purgeOlderThan(any()) } returns
            AppResult.Failure(IllegalStateException("disk full"), "disk full")

        val useCase = PurgeOldDataUseCase(recordings, transcripts)
        val result = useCase(RetentionWindow.DAYS_30, nowFixed)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        coVerify(exactly = 0) { transcripts.purgeOlderThan(any()) }
    }
}
