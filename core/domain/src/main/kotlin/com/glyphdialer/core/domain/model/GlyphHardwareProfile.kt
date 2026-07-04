// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * SDK-free description of the Glyph hardware layout the UI can preview.
 *
 * The real Nothing SDK classes stay inside :peripheral:glyph. Presentation modules use this
 * small domain model to draw the correct on-screen "phone back" for the detected device and
 * to show how custom composer frames will adapt on each hardware family.
 */
data class GlyphHardwareProfile(
    val model: GlyphPhoneModel = GlyphPhoneModel.UNKNOWN,
    val kind: GlyphHardwareKind = GlyphHardwareKind.NONE,
    val displayName: String = "Screen preview",
    val supportedZones: Set<CustomGlyphZone> = emptySet(),
) {
    val isMatrix: Boolean get() = kind == GlyphHardwareKind.MATRIX
    val isLightStrip: Boolean get() = kind == GlyphHardwareKind.LIGHT_STRIP

    fun supports(zone: CustomGlyphZone): Boolean =
        zone == CustomGlyphZone.ALL || zone in supportedZones

    fun adapt(zones: Collection<CustomGlyphZone>): Set<CustomGlyphZone> {
        if (zones.isEmpty()) return emptySet()
        if (CustomGlyphZone.ALL in zones) return supportedZones.ifEmpty { DEFAULT_ZONES }
        val source = supportedZones.ifEmpty { DEFAULT_ZONES }
        return zones.flatMap { zone ->
            if (zone in source) {
                listOf(zone)
            } else {
                fallbackZones(zone).filter { it in source }
            }
        }.toSet()
    }

    private fun fallbackZones(zone: CustomGlyphZone): List<CustomGlyphZone> = when (zone) {
        CustomGlyphZone.TOP_LEFT -> listOf(CustomGlyphZone.TOP_RIGHT, CustomGlyphZone.CAMERA_RING)
        CustomGlyphZone.TOP_RIGHT -> listOf(CustomGlyphZone.CAMERA_RING)
        CustomGlyphZone.CAMERA_RING -> listOf(CustomGlyphZone.TOP_RIGHT, CustomGlyphZone.CENTER)
        CustomGlyphZone.CENTER -> listOf(CustomGlyphZone.CAMERA_RING, CustomGlyphZone.BOTTOM_CENTER)
        CustomGlyphZone.BOTTOM_LEFT -> listOf(CustomGlyphZone.BOTTOM_CENTER)
        CustomGlyphZone.BOTTOM_CENTER -> listOf(CustomGlyphZone.BOTTOM_RIGHT, CustomGlyphZone.BOTTOM_LEFT)
        CustomGlyphZone.BOTTOM_RIGHT -> listOf(CustomGlyphZone.BOTTOM_CENTER)
        CustomGlyphZone.ALL -> listOf(CustomGlyphZone.ALL)
    }

    companion object {
        private val DEFAULT_ZONES = CustomGlyphZone.entries
            .filterNot { it == CustomGlyphZone.ALL }
            .toSet()

        val none = GlyphHardwareProfile(
            model = GlyphPhoneModel.UNKNOWN,
            kind = GlyphHardwareKind.NONE,
            displayName = "Generic Glyph preview",
            supportedZones = DEFAULT_ZONES,
        )

        val matrixPhone3 = GlyphHardwareProfile(
            model = GlyphPhoneModel.PHONE_3_MATRIX,
            kind = GlyphHardwareKind.MATRIX,
            displayName = "Phone (3) Glyph Matrix",
            supportedZones = DEFAULT_ZONES,
        )

        val genericLightStrip = lightStrip(
            model = GlyphPhoneModel.GENERIC_LIGHT_STRIP,
            displayName = "Nothing Glyph",
            zones = DEFAULT_ZONES,
        )

        fun lightStrip(numericModel: Int): GlyphHardwareProfile = when (numericModel) {
            20111 -> lightStrip(
                model = GlyphPhoneModel.PHONE_1,
                displayName = "Phone (1)",
                zones = setOf(
                    CustomGlyphZone.TOP_RIGHT,
                    CustomGlyphZone.CAMERA_RING,
                    CustomGlyphZone.CENTER,
                    CustomGlyphZone.BOTTOM_LEFT,
                    CustomGlyphZone.BOTTOM_CENTER,
                    CustomGlyphZone.BOTTOM_RIGHT,
                ),
            )

            22111 -> lightStrip(
                model = GlyphPhoneModel.PHONE_2,
                displayName = "Phone (2)",
                zones = DEFAULT_ZONES,
            )

            23111 -> lightStrip(
                model = GlyphPhoneModel.PHONE_2A,
                displayName = "Phone (2a)",
                zones = setOf(
                    CustomGlyphZone.TOP_RIGHT,
                    CustomGlyphZone.CAMERA_RING,
                    CustomGlyphZone.CENTER,
                    CustomGlyphZone.BOTTOM_CENTER,
                    CustomGlyphZone.BOTTOM_RIGHT,
                ),
            )

            23113 -> lightStrip(
                model = GlyphPhoneModel.PHONE_2A_PLUS,
                displayName = "Phone (2a Plus)",
                zones = setOf(
                    CustomGlyphZone.TOP_RIGHT,
                    CustomGlyphZone.CAMERA_RING,
                    CustomGlyphZone.CENTER,
                    CustomGlyphZone.BOTTOM_CENTER,
                    CustomGlyphZone.BOTTOM_RIGHT,
                ),
            )

            24111 -> lightStrip(
                model = GlyphPhoneModel.PHONE_3A,
                displayName = "Phone (3a)",
                zones = setOf(
                    CustomGlyphZone.TOP_LEFT,
                    CustomGlyphZone.TOP_RIGHT,
                    CustomGlyphZone.CAMERA_RING,
                    CustomGlyphZone.CENTER,
                    CustomGlyphZone.BOTTOM_CENTER,
                    CustomGlyphZone.BOTTOM_RIGHT,
                ),
            )

            25111 -> lightStrip(
                model = GlyphPhoneModel.PHONE_4A,
                displayName = "Phone (4a)",
                zones = setOf(
                    CustomGlyphZone.TOP_LEFT,
                    CustomGlyphZone.TOP_RIGHT,
                    CustomGlyphZone.CAMERA_RING,
                    CustomGlyphZone.CENTER,
                    CustomGlyphZone.BOTTOM_CENTER,
                    CustomGlyphZone.BOTTOM_RIGHT,
                ),
            )

            else -> genericLightStrip
        }

        private fun lightStrip(
            model: GlyphPhoneModel,
            displayName: String,
            zones: Set<CustomGlyphZone>,
        ): GlyphHardwareProfile = GlyphHardwareProfile(
            model = model,
            kind = GlyphHardwareKind.LIGHT_STRIP,
            displayName = displayName,
            supportedZones = zones,
        )
    }
}

enum class GlyphHardwareKind {
    NONE,
    LIGHT_STRIP,
    MATRIX,
}

enum class GlyphPhoneModel {
    UNKNOWN,
    GENERIC_LIGHT_STRIP,
    PHONE_1,
    PHONE_2,
    PHONE_2A,
    PHONE_2A_PLUS,
    PHONE_3_MATRIX,
    PHONE_3A,
    PHONE_4A,
}
