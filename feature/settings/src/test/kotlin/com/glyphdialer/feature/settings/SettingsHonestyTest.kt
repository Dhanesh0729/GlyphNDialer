// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

import com.glyphdialer.core.domain.model.CapabilityFlags
import com.glyphdialer.core.domain.model.RecordingTier
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic tests for the Settings honesty principle (CONVENTIONS.md §9/§11). These
 * exercise the capability gating in [SettingsUiState] and the tier labelling in
 * [SettingsLabels] without any Android/Compose dependency.
 */
class SettingsHonestyTest {

    @Test
    fun `glyph group hidden when glyph unavailable`() {
        val state = SettingsUiState(capabilities = CapabilityFlags(glyphAvailable = false))
        assertThat(state.showGlyphGroup).isFalse()
    }

    @Test
    fun `glyph group shown only on capable hardware`() {
        val state = SettingsUiState(capabilities = CapabilityFlags(glyphAvailable = true))
        assertThat(state.showGlyphGroup).isTrue()
    }

    @Test
    fun `recording impossible when tier unavailable`() {
        val state = SettingsUiState(
            capabilities = CapabilityFlags(maxRecordingTier = RecordingTier.UNAVAILABLE),
        )
        assertThat(state.recordingPossible).isFalse()
        assertThat(state.activeRecordingTier).isEqualTo(RecordingTier.UNAVAILABLE)
    }

    @Test
    fun `local one-sided tier is never labelled two-way`() {
        // Honesty principle: a one-sided capture must never read as full two-way.
        val label = SettingsLabels.tier(RecordingTier.LOCAL_ONE_SIDED)
        assertThat(label).ignoringCase().contains("my side only")
        assertThat(label.lowercase()).doesNotContain("two-way")
        assertThat(RecordingTier.LOCAL_ONE_SIDED.isTwoWay).isFalse()
    }

    @Test
    fun `system and voip tiers are honestly two-way`() {
        assertThat(RecordingTier.SYSTEM_TWO_WAY.isTwoWay).isTrue()
        assertThat(RecordingTier.VOIP_TWO_WAY.isTwoWay).isTrue()
        assertThat(SettingsLabels.tier(RecordingTier.SYSTEM_TWO_WAY).lowercase()).contains("two-way")
        assertThat(SettingsLabels.tier(RecordingTier.VOIP_TWO_WAY).lowercase()).contains("two-way")
    }

    @Test
    fun `live captions offerable follows on-device speech capability`() {
        val without = SettingsUiState(capabilities = CapabilityFlags(onDeviceSpeechAvailable = false))
        val with = SettingsUiState(capabilities = CapabilityFlags(onDeviceSpeechAvailable = true))
        assertThat(without.liveCaptionsOfferable).isFalse()
        assertThat(with.liveCaptionsOfferable).isTrue()
    }

    @Test
    fun `language auto-detect maps to null`() {
        assertThat(SettingsLabels.language(null)).isEqualTo("Auto-detect")
        assertThat(SettingsLabels.LANGUAGE_OPTIONS.first().first).isNull()
    }
}
