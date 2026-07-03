// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.tier

import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.glyphdialer.core.domain.model.CallDirection
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.RecordingTier
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Decision-table tests for [RecorderTierResolver] — the heart of the honesty principle
 * (§2.1/§12). Verifies the resolver never over-claims a tier the inputs don't support.
 */
class RecorderTierResolverTest {

    private val context: Context = mockk(relaxed = true)
    private val telecomManager: TelecomManager = mockk(relaxed = true)
    private val probe: SystemCallAudioProbe = mockk()

    private lateinit var resolver: RecorderTierResolver

    @BeforeEach
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { context.packageName } returns PKG
        every { context.getSystemService(Context.TELECOM_SERVICE) } returns telecomManager
        resolver = RecorderTierResolver(context, probe)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun grantRecordAudio(granted: Boolean) {
        every {
            ContextCompat.checkSelfPermission(context, any())
        } returns if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    private fun setDefaultDialer(isDefault: Boolean) {
        every { telecomManager.defaultDialerPackage } returns if (isDefault) PKG else "other.pkg"
    }

    private fun setProbe(canCapture: Boolean) {
        every { probe.canCaptureCallAudio() } returns canCapture
    }

    private fun cellularCall() = CallModel(
        id = "c1",
        number = PhoneNumber(raw = "+14155550100"),
        state = CallState.ACTIVE,
        direction = CallDirection.OUTGOING,
        isVoip = false,
    )

    private fun voipCall() = cellularCall().copy(id = "v1", isVoip = true)

    // --- baseline ---------------------------------------------------------------------

    @Test
    fun `baseline UNAVAILABLE when RECORD_AUDIO denied`() {
        grantRecordAudio(false)
        assertThat(resolver.resolveBaseline()).isEqualTo(RecordingTier.UNAVAILABLE)
    }

    @Test
    fun `baseline SPEAKER_TWO_WAY on stock device with permission`() {
        grantRecordAudio(true)
        setDefaultDialer(false)
        setProbe(false)
        assertThat(resolver.resolveBaseline()).isEqualTo(RecordingTier.SPEAKER_TWO_WAY)
    }

    @Test
    fun `baseline SYSTEM_TWO_WAY only when default dialer AND probe positive`() {
        grantRecordAudio(true)
        setDefaultDialer(true)
        setProbe(true)
        assertThat(resolver.resolveBaseline()).isEqualTo(RecordingTier.SYSTEM_TWO_WAY)
    }

    @Test
    fun `baseline never SYSTEM_TWO_WAY when default dialer but probe negative`() {
        grantRecordAudio(true)
        setDefaultDialer(true)
        setProbe(false)
        assertThat(resolver.resolveBaseline()).isEqualTo(RecordingTier.SPEAKER_TWO_WAY)
    }

    @Test
    fun `baseline never SYSTEM_TWO_WAY when probe positive but not default dialer`() {
        grantRecordAudio(true)
        setDefaultDialer(false)
        // probe is never consulted because default-dialer gate fails first; relax it.
        every { probe.canCaptureCallAudio() } returns true
        assertThat(resolver.resolveBaseline()).isEqualTo(RecordingTier.SPEAKER_TWO_WAY)
    }

    // --- per-call decision table ------------------------------------------------------

    @ParameterizedTest(name = "[{index}] perm={0} voip={1} dialer={2} probe={3} -> {4}")
    @MethodSource("decisionTable")
    fun `resolveForCall honest decision table`(
        permission: Boolean,
        voip: Boolean,
        defaultDialer: Boolean,
        probePositive: Boolean,
        expected: RecordingTier,
    ) {
        grantRecordAudio(permission)
        setDefaultDialer(defaultDialer)
        every { probe.canCaptureCallAudio() } returns probePositive

        val call = if (voip) voipCall() else cellularCall()
        assertThat(resolver.resolveForCall(call)).isEqualTo(expected)
    }

    @Test
    fun `VoIP reaches VOIP_TWO_WAY regardless of the device baseline`() {
        grantRecordAudio(true)
        setDefaultDialer(false)
        setProbe(false)
        assertThat(resolver.resolveBaseline()).isEqualTo(RecordingTier.SPEAKER_TWO_WAY)
        assertThat(resolver.resolveForCall(voipCall())).isEqualTo(RecordingTier.VOIP_TWO_WAY)
    }

    @Test
    fun `defaultDialer read SecurityException degrades to non-system tier`() {
        grantRecordAudio(true)
        every { telecomManager.defaultDialerPackage } throws SecurityException("denied")
        every { probe.canCaptureCallAudio() } returns true
        assertThat(resolver.resolveForCall(cellularCall())).isEqualTo(RecordingTier.SPEAKER_TWO_WAY)
    }

    companion object {
        private const val PKG = "com.glyphdialer"

        @JvmStatic
        fun decisionTable(): Stream<Arguments> = Stream.of(
            // permission, voip, defaultDialer, probe, expected
            Arguments.of(false, false, true, true, RecordingTier.UNAVAILABLE),
            Arguments.of(false, true, true, true, RecordingTier.UNAVAILABLE),
            Arguments.of(true, true, false, false, RecordingTier.VOIP_TWO_WAY),
            Arguments.of(true, true, true, true, RecordingTier.VOIP_TWO_WAY),
            Arguments.of(true, false, true, true, RecordingTier.SYSTEM_TWO_WAY),
            Arguments.of(true, false, true, false, RecordingTier.SPEAKER_TWO_WAY),
            Arguments.of(true, false, false, true, RecordingTier.SPEAKER_TWO_WAY),
            Arguments.of(true, false, false, false, RecordingTier.SPEAKER_TWO_WAY),
        )
    }
}
