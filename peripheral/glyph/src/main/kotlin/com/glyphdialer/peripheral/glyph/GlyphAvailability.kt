// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * Runtime detection of Glyph capability, honoring the §9/§17.6 HONESTY PRINCIPLE:
 * we never pretend the hardware exists. Detection is reflection-only so the module
 * compiles without the GDK AAR (CONVENTIONS.md §3 forbids importing
 * `com.nothing.ketchum.*` at compile time here, and the AAR isn't present anyway).
 *
 * A device is Glyph-capable only when ALL hold:
 *  - the device manufacturer/brand is Nothing,
 *  - the OS is Android 14+ (the GDK floor — BUILD_SPEC §17.1/§17.2),
 *  - the GDK classes are actually loadable (`Class.forName` of `GlyphManager`),
 *  - the `NothingKey` meta-data is present in the merged manifest.
 *
 * The result also classifies the device into a [GlyphHardware] family so DI can pick
 * the light-strip (GDK) controller, the Phone (3) Glyph Matrix controller, or the
 * universal no-op.
 */

/** Which Glyph hardware family a device belongs to. */
enum class GlyphHardware {
    /** Light-strip phones driven by the GDK (Phone (1)/(2)/(2a)/(2a+)/(3a)/(4a)). */
    LIGHT_STRIP,

    /** Phone (3) pixel "Glyph Matrix" (no API key required). */
    MATRIX,

    /** Not a Glyph device, or capability prerequisites unmet. */
    NONE,
}

/**
 * The outcome of capability detection. [supported] is the single boolean other code
 * branches on; the other fields exist so the Settings UI can explain *why* Glyph is
 * unavailable rather than silently hiding it (§9 — surface availability).
 */
data class GlyphCapability(
    val hardware: GlyphHardware,
    val isNothingDevice: Boolean,
    val isAndroid14Plus: Boolean,
    val gdkPresent: Boolean,
    val apiKeyPresent: Boolean,
    /** Human-readable reason when [supported] is false; `null` when supported. */
    val unavailableReason: String?,
) {
    /** True only when every prerequisite is met for a real Glyph session. */
    val supported: Boolean
        get() = hardware != GlyphHardware.NONE &&
            isAndroid14Plus &&
            gdkPresent &&
            // The Glyph Matrix needs no API key; the GDK light-strip path does.
            (hardware == GlyphHardware.MATRIX || apiKeyPresent)
}

/**
 * Detects [GlyphCapability]. Safe to call on any device; never throws.
 */
class GlyphAvailability(private val appContext: Context) {

    /**
     * Model device constants from BUILD_SPEC §17.2. Used by the GDK controller to call
     * `GlyphManager.register(<const>)` reflectively.
     */
    object DeviceConstants {
        const val PHONE_1 = 20111
        const val PHONE_2 = 22111
        const val PHONE_2A = 23111
        const val PHONE_2A_PLUS = 23113
        const val PHONE_3A = 24111
        const val PHONE_4A = 25111

        /** Marketing models that use the pixel Glyph Matrix rather than light strips. */
        val MATRIX_MODELS = setOf("Phone (3)", "Phone(3)", "A024", "Phone 3")
    }

    /** Fully-qualified GDK entry-point class, probed via reflection. */
    private val gdkManagerClass = "com.nothing.ketchum.GlyphManager"

    /** Fully-qualified Glyph Matrix entry-point class, probed via reflection. */
    private val matrixManagerClass = "com.nothing.ketchum.GlyphMatrixManager"

    fun detect(): GlyphCapability {
        val isNothing = isNothingDevice()
        val isA14 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // API 34 = Android 14
        val hardware = classifyHardware(isNothing)
        val gdk = isClassPresent(if (hardware == GlyphHardware.MATRIX) matrixManagerClass else gdkManagerClass)
        val apiKey = isApiKeyPresent()

        val reason = buildUnavailableReason(hardware, isA14, gdk, apiKey)
        val capability = GlyphCapability(
            // Keep the detected hardware family even when unsupported, so the UI can
            // explain *which* prerequisite failed rather than just hiding Glyph.
            hardware = hardware,
            isNothingDevice = isNothing,
            isAndroid14Plus = isA14,
            gdkPresent = gdk,
            apiKeyPresent = apiKey,
            unavailableReason = reason,
        )
        Timber.tag(TAG).i("Glyph capability: %s", capability)
        return capability
    }

    private fun isNothingDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
        val brand = Build.BRAND?.lowercase().orEmpty()
        return manufacturer.contains("nothing") || brand.contains("nothing")
    }

    private fun classifyHardware(isNothing: Boolean): GlyphHardware {
        if (!isNothing) return GlyphHardware.NONE
        val model = Build.MODEL.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val isMatrix = DeviceConstants.MATRIX_MODELS.any { it.equals(model, ignoreCase = true) } ||
            model.contains("(3)") ||
            device.equals("Pong", ignoreCase = true) // Phone (3) internal codename, best-effort
        return if (isMatrix) GlyphHardware.MATRIX else GlyphHardware.LIGHT_STRIP
    }

    private fun isClassPresent(fqcn: String): Boolean = try {
        Class.forName(fqcn, false, javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (t: Throwable) {
        Timber.tag(TAG).w(t, "Unexpected error probing %s", fqcn)
        false
    }

    /** Reads the `NothingKey` `<meta-data>` from the merged manifest, if any. */
    private fun isApiKeyPresent(): Boolean = try {
        val pm = appContext.packageManager
        val flags = PackageManager.GET_META_DATA
        val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(
                appContext.packageName,
                PackageManager.ApplicationInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(appContext.packageName, flags)
        }
        val key = ai.metaData?.getString("NothingKey")
        !key.isNullOrBlank()
    } catch (t: Throwable) {
        Timber.tag(TAG).w(t, "Could not read NothingKey meta-data")
        false
    }

    private fun buildUnavailableReason(
        hardware: GlyphHardware,
        isA14: Boolean,
        gdk: Boolean,
        apiKey: Boolean,
    ): String? = when {
        hardware == GlyphHardware.NONE -> "Glyph hardware is only present on Nothing phones."
        !isA14 -> "Glyph requires Android 14 or newer."
        !gdk -> "Glyph SDK not found on this build (GDK AAR not installed)."
        hardware == GlyphHardware.LIGHT_STRIP && !apiKey ->
            "Glyph API key missing — set a NothingKey for release builds."
        else -> null
    }

    private companion object {
        const val TAG = "GlyphAvailability"
    }
}
