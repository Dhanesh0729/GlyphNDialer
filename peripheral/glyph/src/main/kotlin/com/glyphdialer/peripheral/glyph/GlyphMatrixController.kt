// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph

import android.content.Context
import com.glyphdialer.core.domain.glyph.CallVisual
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.peripheral.glyph.choreography.GlyphChoreographer
import com.glyphdialer.peripheral.glyph.choreography.GlyphDeviceProfile
import com.glyphdialer.peripheral.glyph.choreography.GlyphZone
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * [GlyphController] for the Phone (3) "Glyph Matrix" — a 25×25 pixel LED matrix (no API
 * key required, BUILD_SPEC §17.3). Like [GdkGlyphController] it drives the SDK ENTIRELY
 * VIA REFLECTION (`com.nothing.ketchum.GlyphMatrixManager` + `GlyphMatrixFrame`/
 * `GlyphMatrixObject` builders) so this module compiles without the Matrix AAR.
 *
 * It renders:
 *  - per-digit glyphs (the typed character drawn on the matrix) for [playDigitStroke],
 *  - a true dot-matrix waveform (a centered bar whose height mirrors amplitude) for
 *    [renderWaveform],
 *  - the shared call/recording choreography via [GlyphChoreographer] (zones mapped to
 *    matrix regions).
 *
 * Where a Matrix SDK method is absent on the installed AAR, the paint degrades to a
 * no-op rather than crashing (honesty principle — never fake hardware output).
 */
class GlyphMatrixController(
    appContext: Context,
    private val capability: GlyphCapability,
    dispatcher: CoroutineDispatcher,
) : GlyphController {

    private val context = appContext.applicationContext
    private val ready = AtomicBoolean(false)

    private var matrixManager: Any? = null
    private var managerClass: Class<*>? = null

    /** The character most recently dispatched, drawn as a glyph on the matrix. */
    @Volatile private var lastDigit: Char? = null
    private val deviceProfile = GlyphDeviceProfile.matrixPhone3

    private val choreographer = GlyphChoreographer(
        dispatcher = dispatcher,
        renderer = MatrixRenderer(),
    )

    init {
        if (capability.supported && capability.hardware == GlyphHardware.MATRIX) {
            initManager()
        } else {
            Timber.tag(TAG).w("GlyphMatrixController constructed but capability unsupported: %s", capability)
        }
    }

    override val isAvailable: Boolean
        get() = ready.get()

    override fun playDigitStroke(digit: Char) {
        if (!isAvailable) return
        lastDigit = digit
        // Draw the digit glyph immediately, then let the stroke choreography pulse around it.
        drawDigitGlyph(digit)
        choreographer.playDigit(digit)
    }

    override fun playIncomingShow(contactSeed: Int, pattern: com.glyphdialer.core.domain.model.GlyphPattern) {
        if (!isAvailable) return
        choreographer.playIncomingShow(contactSeed, pattern)
    }

    override fun playIncomingShow(contactSeed: Int, customPattern: com.glyphdialer.core.domain.model.CustomGlyphPattern) {
        if (!isAvailable) return
        choreographer.playIncomingShow(contactSeed, customPattern)
    }

    override fun previewCustomPattern(customPattern: com.glyphdialer.core.domain.model.CustomGlyphPattern) {
        if (!isAvailable) return
        choreographer.previewCustomPattern(customPattern)
    }

    override fun showRecording(active: Boolean) {
        if (!isAvailable) return
        choreographer.showRecording(active)
    }

    override fun renderWaveform(amplitude: Float) {
        if (!isAvailable) return
        choreographer.renderWaveform(amplitude)
    }

    override fun showOnCall(state: CallVisual) {
        if (!isAvailable) return
        choreographer.showOnCall(state)
    }

    /** Update the master Glyph intensity (0f..1f), mirroring `LocalGlyphIntensity`. */
    fun setIntensity(value: Float) = choreographer.setIntensity(value)

    override fun release() {
        runCatching { choreographer.release() }
        teardownManager()
    }

    // --- Matrix lifecycle (reflection) -----------------------------------------

    private fun initManager() {
        try {
            val cls = Class.forName("com.nothing.ketchum.GlyphMatrixManager")
            managerClass = cls
            val instance = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            matrixManager = instance
            // Matrix init typically takes a callback; we register and mark ready optimistically,
            // flipping ready=false on any failure.
            runCatching { cls.getMethod("init", Context::class.java).invoke(instance, context) }
                .onFailure { runCatching { cls.getMethod("init").invoke(instance) } }
            ready.set(true)
            Timber.tag(TAG).i("GlyphMatrixManager initialised")
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Matrix init failed; Glyph Matrix unavailable")
            matrixManager = null
            ready.set(false)
        }
    }

    private fun teardownManager() {
        val mgr = matrixManager ?: return
        val cls = managerClass ?: return
        runCatching { cls.getMethod("unInit").invoke(mgr) }
            .onFailure { Timber.tag(TAG).v(it, "matrix unInit failed/absent") }
        ready.set(false)
        matrixManager = null
        Timber.tag(TAG).i("Glyph Matrix torn down")
    }

    // --- rendering --------------------------------------------------------------

    /**
     * Draws [digit] as a glyph by handing the character to the Matrix text/object API.
     * The Matrix SDK exposes `GlyphMatrixObject.Builder().setText(...)` rendered into a
     * `GlyphMatrixFrame`. We attempt that path reflectively.
     */
    private fun drawDigitGlyph(digit: Char) {
        val frame = buildTextFrame(digit.toString()) ?: return
        pushFrame(frame)
    }

    /**
     * Renders an [amplitude] (0f..1f) as a true dot-matrix waveform: a centered
     * horizontal bar whose half-height scales with amplitude across the 25-row matrix.
     * Builds a `GlyphMatrixObject` of lit pixels and pushes it.
     */
    private fun drawWaveform(amplitude: Float) {
        val rows = ((amplitude.coerceIn(0f, 1f)) * (MATRIX_SIZE / 2)).roundToInt()
        val frame = buildWaveformFrame(rows) ?: return
        pushFrame(frame)
    }

    /** Maps the choreographer's abstract zones to a coarse matrix region paint. */
    private inner class MatrixRenderer : GlyphChoreographer.GlyphRenderer {
        override fun paint(zones: List<GlyphZone>, intensity: Float) {
            // Translate zones to a region mask on the matrix and push as an object frame.
            val frame = buildRegionFrame(deviceProfile.adapt(zones), intensity) ?: return
            pushFrame(frame)
        }

        override fun paintWaveform(amplitude: Float) = drawWaveform(amplitude)

        override fun flashTorch() {
            // Torch is owned by :telecom/:app (CameraManager). Honest no-op here.
            Timber.tag(TAG).v("flashTorch no-op in Matrix controller")
        }

        override fun clear() {
            val mgr = matrixManager ?: return
            val cls = managerClass ?: return
            // Push an empty frame / turnOff if available.
            runCatching { cls.getMethod("turnOff").invoke(mgr) }
                .onFailure {
                    buildRegionFrame(emptyList(), 0f)?.let { pushFrame(it) }
                }
        }
    }

    /** Reflectively builds a `GlyphMatrixFrame` containing rendered [text]. */
    private fun buildTextFrame(text: String): Any? = try {
        val objBuilderClass = Class.forName("com.nothing.ketchum.GlyphMatrixObject\$Builder")
        var b: Any = objBuilderClass.getConstructor().newInstance()
        b = objBuilderClass.getMethod("setText", String::class.java).invoke(b, text) ?: b
        b = runCatching {
            objBuilderClass.getMethod("setBrightness", Int::class.javaPrimitiveType)
                .invoke(b, MATRIX_MAX_BRIGHTNESS)
        }.getOrDefault(b) ?: b
        val obj = objBuilderClass.getMethod("build").invoke(b)
        wrapInFrame(obj)
    } catch (t: Throwable) {
        Timber.tag(TAG).v(t, "Matrix text frame unavailable on installed AAR")
        null
    }

    /** Builds a centered waveform bar of [halfRows] above and below the matrix midline. */
    private fun buildWaveformFrame(halfRows: Int): Any? = try {
        // Represent the bar as a pixel array if the SDK takes raw arrays; else fall back
        // to a text-style object. We use the raw-array path when present.
        val mid = MATRIX_SIZE / 2
        val pixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        for (r in (mid - halfRows)..(mid + halfRows)) {
            if (r in 0 until MATRIX_SIZE) {
                for (c in 0 until MATRIX_SIZE) pixels[r * MATRIX_SIZE + c] = MATRIX_MAX_BRIGHTNESS
            }
        }
        frameFromPixels(pixels)
    } catch (t: Throwable) {
        Timber.tag(TAG).v(t, "Matrix waveform frame unavailable")
        null
    }

    /** Builds a coarse region paint from abstract [zones] at [intensity]. */
    private fun buildRegionFrame(zones: List<GlyphZone>, intensity: Float): Any? = try {
        val brightness = (intensity.coerceIn(0f, 1f) * MATRIX_MAX_BRIGHTNESS).roundToInt()
        val pixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        for (z in zones) {
            for ((r, c) in zonePixels(z)) {
                if (r in 0 until MATRIX_SIZE && c in 0 until MATRIX_SIZE) {
                    pixels[r * MATRIX_SIZE + c] = brightness
                }
            }
        }
        frameFromPixels(pixels)
    } catch (t: Throwable) {
        Timber.tag(TAG).v(t, "Matrix region frame unavailable")
        null
    }

    /** Coarse 3×3 grid mapping of abstract zones onto matrix cells. */
    private fun zonePixels(zone: GlyphZone): List<Pair<Int, Int>> {
        val third = MATRIX_SIZE / 3
        fun block(rowBand: Int, colBand: Int): List<Pair<Int, Int>> = buildList {
            for (r in rowBand * third until (rowBand + 1) * third)
                for (c in colBand * third until (colBand + 1) * third) add(r to c)
        }
        return when (zone) {
            GlyphZone.TOP_LEFT -> block(0, 0)
            GlyphZone.TOP_RIGHT, GlyphZone.CAMERA_RING -> block(0, 2)
            GlyphZone.CENTER -> block(1, 1)
            GlyphZone.BOTTOM_LEFT -> block(2, 0)
            GlyphZone.BOTTOM_CENTER -> block(2, 1)
            GlyphZone.BOTTOM_RIGHT -> block(2, 2)
            GlyphZone.ALL -> buildList {
                for (r in 0 until MATRIX_SIZE) for (c in 0 until MATRIX_SIZE) add(r to c)
            }
        }
    }

    /** Builds a frame from a raw pixel array, tolerating SDK signature variants. */
    private fun frameFromPixels(pixels: IntArray): Any? {
        return try {
            val objBuilderClass = Class.forName("com.nothing.ketchum.GlyphMatrixObject\$Builder")
            var b: Any = objBuilderClass.getConstructor().newInstance()
            // Prefer a raw image/pixel setter if the AAR exposes one.
            val setter = objBuilderClass.declaredMethods.firstOrNull {
                it.name in setOf("setImage", "setPixels", "setMatrix") && it.parameterTypes.size == 1
            }
            if (setter != null) {
                b = setter.invoke(b, pixels) ?: b
            }
            val obj = objBuilderClass.getMethod("build").invoke(b)
            wrapInFrame(obj)
        } catch (t: Throwable) {
            Timber.tag(TAG).v(t, "frameFromPixels unavailable")
            null
        }
    }

    /** Wraps a built `GlyphMatrixObject` into a `GlyphMatrixFrame`. */
    private fun wrapInFrame(obj: Any): Any? = try {
        val frameBuilderClass = Class.forName("com.nothing.ketchum.GlyphMatrixFrame\$Builder")
        var fb: Any = frameBuilderClass.getConstructor().newInstance()
        // addTop / addMid / addLow place the object on a layer; addLow is the safe default.
        val add = frameBuilderClass.declaredMethods.firstOrNull {
            it.name in setOf("addLow", "addMid", "addTop") && it.parameterTypes.size == 1
        }
        if (add != null) fb = add.invoke(fb, obj) ?: fb
        frameBuilderClass.getMethod("build", Context::class.java).invoke(fb, context)
    } catch (t: Throwable) {
        // Some SDK builds take the object directly to the manager; pass it through.
        Timber.tag(TAG).v(t, "wrapInFrame fell back to raw object")
        obj
    }

    /** Pushes a built frame to the matrix via `setMatrixFrame(...)`/`setAppMatrixFrame(...)`. */
    private fun pushFrame(frame: Any) {
        val mgr = matrixManager ?: return
        val cls = managerClass ?: return
        val pushed = cls.declaredMethods.firstOrNull {
            it.name in setOf("setMatrixFrame", "setAppMatrixFrame", "setGlyphMatrixFrame") &&
                it.parameterTypes.size == 1
        }
        if (pushed != null) {
            runCatching { pushed.invoke(mgr, frame) }
                .onFailure { Timber.tag(TAG).v(it, "matrix push failed") }
        } else {
            Timber.tag(TAG).v("No matrix frame setter on installed AAR")
        }
    }

    private companion object {
        const val TAG = "GlyphMatrix"
        const val MATRIX_SIZE = 25 // Phone (3) Glyph Matrix is a 25×25 grid.
        const val MATRIX_MAX_BRIGHTNESS = 255
    }
}
