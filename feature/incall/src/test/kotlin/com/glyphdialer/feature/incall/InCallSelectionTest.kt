// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall

import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.feature.incall.ui.recordingLabel
import com.glyphdialer.core.domain.model.RecordingTier
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the in-call primary-call selection and the honest recording
 * label (CONVENTIONS.md §11 — JUnit5 + Truth on dependency-light reducer logic).
 */
class InCallSelectionTest {

    private fun call(id: String, state: CallState, conference: Boolean = false) = CallModel(
        id = id,
        number = PhoneNumber(raw = "+1415555010$id"),
        state = state,
        isConference = conference,
    )

    @Test
    fun `incoming ringing wins over active`() {
        val calls = listOf(
            call("1", CallState.ACTIVE),
            call("2", CallState.RINGING).copy(
                direction = com.glyphdialer.core.domain.model.CallDirection.INCOMING,
            ),
        )
        val primary = InCallViewModel.selectPrimary(calls)
        assertThat(primary?.id).isEqualTo("2")
    }

    @Test
    fun `conference parent is foregrounded over a plain active call`() {
        val calls = listOf(
            call("1", CallState.ACTIVE),
            call("2", CallState.CONFERENCE, conference = true),
        )
        val primary = InCallViewModel.selectPrimary(calls)
        assertThat(primary?.id).isEqualTo("2")
    }

    @Test
    fun `dialing wins over a held call`() {
        val calls = listOf(
            call("1", CallState.HOLDING),
            call("2", CallState.DIALING),
        )
        assertThat(InCallViewModel.selectPrimary(calls)?.id).isEqualTo("2")
    }

    @Test
    fun `terminal calls are ignored and empty yields null`() {
        val calls = listOf(call("1", CallState.DISCONNECTED))
        assertThat(InCallViewModel.selectPrimary(calls)).isNull()
        assertThat(InCallViewModel.selectPrimary(emptyList())).isNull()
    }

    @Test
    fun `recording label never overclaims a one-sided capture`() {
        assertThat(recordingLabel(RecordingTier.LOCAL_ONE_SIDED)).contains("MY SIDE ONLY")
        assertThat(recordingLabel(RecordingTier.SYSTEM_TWO_WAY)).contains("TWO-WAY")
        assertThat(recordingLabel(RecordingTier.VOIP_TWO_WAY)).contains("TWO-WAY")
        assertThat(recordingLabel(RecordingTier.UNAVAILABLE)).contains("UNAVAILABLE")
    }
}
