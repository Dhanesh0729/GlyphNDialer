// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph.choreography

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GlyphDeviceProfileTest {

    @Test
    fun `phone 2a adapts unsupported top-left to supported zones`() {
        val profile = GlyphDeviceProfile.lightStrip(23111)

        val adapted = profile.adapt(listOf(GlyphZone.TOP_LEFT))

        assertThat(adapted).isNotEmpty()
        assertThat(adapted).containsNoneIn(listOf(GlyphZone.TOP_LEFT))
        assertThat(profile.supportedZones).containsAtLeastElementsIn(adapted)
    }

    @Test
    fun `all zone remains all on every profile`() {
        val profiles = listOf(
            GlyphDeviceProfile.lightStrip(20111),
            GlyphDeviceProfile.lightStrip(22111),
            GlyphDeviceProfile.lightStrip(23111),
            GlyphDeviceProfile.lightStrip(24111),
            GlyphDeviceProfile.matrixPhone3,
        )

        profiles.forEach { profile ->
            assertThat(profile.adapt(listOf(GlyphZone.ALL))).containsExactly(GlyphZone.ALL)
        }
    }
}
