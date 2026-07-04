// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph.choreography

/**
 * SDK-free description of a Nothing device's available Glyph regions.
 *
 * Exact channel constants live in the Nothing SDK and vary per model. This profile
 * keeps the app's choreography stable by declaring which abstract zones are useful on
 * each family, and by adapting unsupported zones to a nearby physical region.
 */
data class GlyphDeviceProfile(
    val label: String,
    val hardware: Hardware,
    val supportedZones: Set<GlyphZone>,
    val prefixBuckets: Map<GlyphZone, Set<String>>,
) {
    enum class Hardware { LIGHT_STRIP, MATRIX }

    fun adapt(zones: List<GlyphZone>): List<GlyphZone> {
        if (zones.isEmpty()) return emptyList()
        if (GlyphZone.ALL in zones) return listOf(GlyphZone.ALL)
        return zones.flatMap { zone ->
            if (zone in supportedZones) {
                listOf(zone)
            } else {
                fallbackZones(zone)
            }
        }.distinct()
    }

    private fun fallbackZones(zone: GlyphZone): List<GlyphZone> = when (zone) {
        GlyphZone.TOP_LEFT -> listOf(GlyphZone.TOP_RIGHT, GlyphZone.CAMERA_RING)
        GlyphZone.TOP_RIGHT -> listOf(GlyphZone.CAMERA_RING)
        GlyphZone.CAMERA_RING -> listOf(GlyphZone.TOP_RIGHT, GlyphZone.CENTER)
        GlyphZone.CENTER -> listOf(GlyphZone.CAMERA_RING, GlyphZone.BOTTOM_CENTER)
        GlyphZone.BOTTOM_LEFT -> listOf(GlyphZone.BOTTOM_CENTER)
        GlyphZone.BOTTOM_CENTER -> listOf(GlyphZone.BOTTOM_RIGHT, GlyphZone.BOTTOM_LEFT)
        GlyphZone.BOTTOM_RIGHT -> listOf(GlyphZone.BOTTOM_CENTER)
        GlyphZone.ALL -> listOf(GlyphZone.ALL)
    }.filter { it in supportedZones }

    companion object {
        fun lightStrip(numericModel: Int): GlyphDeviceProfile = when (numericModel) {
            20111 -> phone1
            22111 -> phone2
            23111, 23113 -> phone2a
            24111, 25111 -> phone3aOr4a
            else -> genericLightStrip
        }

        val matrixPhone3 = GlyphDeviceProfile(
            label = "Phone (3) Glyph Matrix",
            hardware = Hardware.MATRIX,
            supportedZones = GlyphZone.entries.toSet(),
            prefixBuckets = emptyMap(),
        )

        private val phone1 = lightProfile(
            label = "Phone (1)",
            supportedZones = setOf(
                GlyphZone.TOP_RIGHT,
                GlyphZone.CAMERA_RING,
                GlyphZone.CENTER,
                GlyphZone.BOTTOM_LEFT,
                GlyphZone.BOTTOM_CENTER,
                GlyphZone.BOTTOM_RIGHT,
                GlyphZone.ALL,
            ),
        )

        private val phone2 = lightProfile(
            label = "Phone (2)",
            supportedZones = GlyphZone.entries.toSet(),
        )

        private val phone2a = lightProfile(
            label = "Phone (2a / 2a Plus)",
            supportedZones = setOf(
                GlyphZone.TOP_RIGHT,
                GlyphZone.CAMERA_RING,
                GlyphZone.CENTER,
                GlyphZone.BOTTOM_CENTER,
                GlyphZone.BOTTOM_RIGHT,
                GlyphZone.ALL,
            ),
        )

        private val phone3aOr4a = lightProfile(
            label = "Phone (3a / 4a)",
            supportedZones = setOf(
                GlyphZone.TOP_LEFT,
                GlyphZone.TOP_RIGHT,
                GlyphZone.CAMERA_RING,
                GlyphZone.CENTER,
                GlyphZone.BOTTOM_CENTER,
                GlyphZone.BOTTOM_RIGHT,
                GlyphZone.ALL,
            ),
        )

        val genericLightStrip = lightProfile(
            label = "Nothing Glyph",
            supportedZones = GlyphZone.entries.toSet(),
        )

        private fun lightProfile(
            label: String,
            supportedZones: Set<GlyphZone>,
        ): GlyphDeviceProfile = GlyphDeviceProfile(
            label = label,
            hardware = Hardware.LIGHT_STRIP,
            supportedZones = supportedZones,
            prefixBuckets = mapOf(
                GlyphZone.TOP_LEFT to setOf("A", "TL", "TOP_LEFT"),
                GlyphZone.TOP_RIGHT to setOf("B", "TR", "TOP_RIGHT"),
                GlyphZone.CAMERA_RING to setOf("B", "C", "CAMERA", "RING"),
                GlyphZone.CENTER to setOf("C", "CENTER", "MID"),
                GlyphZone.BOTTOM_LEFT to setOf("D", "BL", "BOTTOM_LEFT"),
                GlyphZone.BOTTOM_CENTER to setOf("E", "BC", "BOTTOM_CENTER", "BOTTOM"),
                GlyphZone.BOTTOM_RIGHT to setOf("E", "BR", "BOTTOM_RIGHT"),
            ),
        )
    }
}
