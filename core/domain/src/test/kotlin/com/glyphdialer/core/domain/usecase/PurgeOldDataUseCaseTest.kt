// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.fake.FakeRecordingRepository
import com.glyphdialer.core.domain.fake.FakeTranscriptRepository
import com.glyphdialer.core.domain.model.RetentionWindow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for the retention/purge logic (CONVENTIONS.md section 11). Uses hand-rolled
 * fakes (no mocking framework) so the cutoff math + branch behavior are pinned.
 */
class PurgeOldDataUseCaseTest {

    private val recordings = FakeRecordingRepository()
    private val transcripts = FakeTranscriptRepository()
    private val useCase = PurgeOldDataUseCase(recordings, transcripts)

    private val now = 1_000_000_000_000L // fixed "current time"

    @Test
    fun `NEVER window purges nothing and never calls the repositories`() = runTest {
        val result = useCase(RetentionWindow.NEVER, now)

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val data = (result as AppResult.Success).data
        assertThat(data).isEqualTo(PurgeResult.NONE)
        assertThat(recordings.purgeCalls).isEmpty()
        assertThat(transcripts.purgeCalls).isEmpty()
    }

    @Test
    fun `30-day window passes the correct cutoff to both repositories`() = runTest {
        recordings.deletedCount = 3
        transcripts.deletedCount = 2

        val result = useCase(RetentionWindow.DAYS_30, now)

        val expectedCutoff = now - 30L * RetentionWindow.MILLIS_PER_DAY
        assertThat(recordings.purgeCalls).containsExactly(expectedCutoff)
        assertThat(transcripts.purgeCalls).containsExactly(expectedCutoff)

        val data = (result as AppResult.Success).data
        assertThat(data.recordingsDeleted).isEqualTo(3)
        assertThat(data.transcriptsDeleted).isEqualTo(2)
        assertThat(data.total).isEqualTo(5)
    }

    @Test
    fun `180-day window computes the correct cutoff`() = runTest {
        useCase(RetentionWindow.DAYS_180, now)
        val expectedCutoff = now - 180L * RetentionWindow.MILLIS_PER_DAY
        assertThat(recordings.purgeCalls).containsExactly(expectedCutoff)
    }

    @Test
    fun `a repository failure short-circuits and propagates`() = runTest {
        recordings.failWith = IllegalStateException("disk error")

        val result = useCase(RetentionWindow.DAYS_90, now)

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        // Transcripts must NOT be purged once recordings failed.
        assertThat(transcripts.purgeCalls).isEmpty()
    }

    @Test
    fun `RetentionWindow cutoffMillis math is correct`() {
        assertThat(RetentionWindow.DAYS_30.cutoffMillis(now))
            .isEqualTo(now - 30L * 24 * 60 * 60 * 1000)
        assertThat(RetentionWindow.DAYS_90.cutoffMillis(now))
            .isEqualTo(now - 90L * 24 * 60 * 60 * 1000)
        assertThat(RetentionWindow.NEVER.cutoffMillis(now)).isNull()
    }
}
