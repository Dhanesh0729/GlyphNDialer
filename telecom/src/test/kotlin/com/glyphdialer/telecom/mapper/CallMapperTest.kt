// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.mapper

import android.net.Uri
import android.telecom.Call
import com.glyphdialer.core.domain.model.CallState
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * State-transition + mapping shape test for [CallMapper] (CONVENTIONS.md §11/§23).
 *
 * We mock the framework [Call]/[Call.Details] (whose constructors are hidden) and
 * assert the framework-free [com.glyphdialer.core.domain.model.CallModel] we derive.
 * Runs on the JVM via MockK — no Robolectric/device needed because we never invoke
 * real framework behaviour, only read mocked getters.
 */
class CallMapperTest {

    private fun mockHandle(number: String): Uri = mockk(relaxed = true) {
        every { schemeSpecificPart } returns number
    }

    private fun mockDetails(
        stateValue: Int,
        direction: Int = Call.Details.DIRECTION_INCOMING,
        number: String = "+14155552671",
        selfManaged: Boolean = false,
        capabilities: Int = 0,
    ): Call.Details = mockk(relaxed = true) {
        every { state } returns stateValue
        every { callDirection } returns direction
        every { handle } returns mockHandle(number)
        every { callerDisplayName } returns null
        every { connectTimeMillis } returns 0L
        every { creationTimeMillis } returns 1_000L
        every { disconnectCause } returns null
        every { hasProperty(Call.Details.PROPERTY_SELF_MANAGED) } returns selfManaged
        every { can(any()) } answers { (capabilities and firstArg<Int>()) == firstArg<Int>() }
    }

    private fun mockCall(
        details: Call.Details,
        legacyState: Int,
        children: List<Call> = emptyList(),
    ): Call = mockk(relaxed = true) {
        every { this@mockk.details } returns details
        every { state } returns legacyState
        every { this@mockk.children } returns children
        every { parent } returns null
    }

    @Test
    fun `ringing incoming call maps to RINGING incoming`() {
        val details = mockDetails(Call.STATE_RINGING)
        val call = mockCall(details, Call.STATE_RINGING)

        val model = CallMapper.toModel("call-1", call, parentId = null, childIds = emptyList(), isMuted = false)

        assertThat(model.state).isEqualTo(CallState.RINGING)
        assertThat(model.isIncomingRinging).isTrue()
        assertThat(model.number.raw).isEqualTo("+14155552671")
    }

    @Test
    fun `active call with children maps to CONFERENCE`() {
        val details = mockDetails(Call.STATE_ACTIVE)
        val child = mockk<Call>(relaxed = true)
        val call = mockCall(details, Call.STATE_ACTIVE, children = listOf(child))

        val model = CallMapper.toModel("call-1", call, parentId = null, childIds = listOf("call-2"), isMuted = false)

        assertThat(model.state).isEqualTo(CallState.CONFERENCE)
        assertThat(model.isConference).isTrue()
        assertThat(model.childCallIds).containsExactly("call-2")
    }

    @Test
    fun `self-managed VoIP call exposes video-upgrade capability, cellular does not`() {
        val voip = CallMapper.toModel(
            "v",
            mockCall(mockDetails(Call.STATE_ACTIVE, selfManaged = true), Call.STATE_ACTIVE),
            parentId = null, childIds = emptyList(), isMuted = false,
        )
        val cell = CallMapper.toModel(
            "c",
            mockCall(mockDetails(Call.STATE_ACTIVE, selfManaged = false), Call.STATE_ACTIVE),
            parentId = null, childIds = emptyList(), isMuted = false,
        )

        // §2.3 honesty: video upgrade is offered only for in-app VoIP, never cellular.
        assertThat(voip.isVoip).isTrue()
        assertThat(voip.capability.canUpgradeToVideo).isTrue()
        assertThat(cell.capability.canUpgradeToVideo).isFalse()
    }

    @Test
    fun `hold capability bit is reflected`() {
        val details = mockDetails(Call.STATE_ACTIVE, capabilities = Call.Details.CAPABILITY_HOLD)
        val model = CallMapper.toModel(
            "h", mockCall(details, Call.STATE_ACTIVE),
            parentId = null, childIds = emptyList(), isMuted = false,
        )
        assertThat(model.capability.canHold).isTrue()
    }

    @Test
    fun `disconnected call maps to DISCONNECTED terminal state`() {
        val details = mockDetails(Call.STATE_DISCONNECTED)
        val model = CallMapper.toModel(
            "d", mockCall(details, Call.STATE_DISCONNECTED),
            parentId = null, childIds = emptyList(), isMuted = true,
        )
        assertThat(model.state).isEqualTo(CallState.DISCONNECTED)
        assertThat(model.state.isTerminal).isTrue()
        assertThat(model.isMuted).isTrue()
    }
}
