// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph

import android.content.Context
import com.glyphdialer.core.domain.glyph.CallVisual
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.peripheral.glyph.choreography.GlyphChoreographer
import com.glyphdialer.peripheral.glyph.choreography.GlyphZone
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [GlyphController] for light-strip Nothing phones (Phone (1)/(2)/(2a)/(2a+)/(3a)/(4a)),
 * driving the Nothing GDK `GlyphManager` ENTIRELY VIA REFLECTION.
 *
 * WHY REFLECTION: CONVENTIONS.md §3 forbids compile-time imports of
 * `com.nothing.ketchum.*` outside this module, and — more practically — the GDK AAR is
 * NOT present at build time (see the TODO in build.gradle.kts / notes). Reflection lets
 * this module compile standalone and still light up at runtime when the AAR is dropped
 * into `peripheral/glyph/libs/`.
 *
 * Lifecycle (BUILD_SPEC §17.2):
 *   init(context, callback) → onServiceConnected → register(<deviceConstant>) →
 *   openSession() → build frames (Builder + buildChannel/buildPeriod/buildCycles/…) →
 *   toggle()/animate() → closeSession() → unInit() on teardown.
 *
 * Frame building from raw channels is GDK-version-sensitive, so this controller keeps a
 * conservative whole-strip fallback: it tries `GlyphFrame.Builder().buildChannel(...)`
 * reflectively and, if a method is missing on the installed AAR, degrades gracefully to
 * a no-op for that paint rather than crashing (honesty principle — never fake success).
 */
class GdkGlyphController(
    appContext: Context,
    private val capability: GlyphCapability,
    dispatcher: CoroutineDispatcher,
) : GlyphController {

    private val context = appContext.applicationContext
    private val ready = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)

    /** Reflective handle to the live `GlyphManager` instance, if obtained. */
    private var glyphManager: Any? = null
    private var managerClass: Class<*>? = null

    /** Zone → GDK channel-int mapping; resolved when the service connects. */
    private var zoneChannels: Map<GlyphZone, List<Int>> = emptyMap()

    private val choreographer = GlyphChoreographer(
        dispatcher = dispatcher,
        renderer = GdkRenderer(),
    )

    init {
        if (capability.supported && capability.hardware == GlyphHardware.LIGHT_STRIP) {
            initManager()
        } else {
            Timber.tag(TAG).w("GdkGlyphController constructed but capability unsupported: %s", capability)
        }
    }

    override val isAvailable: Boolean
        get() = ready.get() && registered.get()

    override fun playDigitStroke(digit: Char) {
        if (!isAvailable) return
        choreographer.playDigit(digit)
    }

    override fun playIncomingShow(contactSeed: Int) {
        if (!isAvailable) return
        choreographer.playIncomingShow(contactSeed)
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

    // --- GDK lifecycle (reflection) --------------------------------------------

    private fun initManager() {
        try {
            val mgrClass = Class.forName("com.nothing.ketchum.GlyphManager")
            managerClass = mgrClass
            // GlyphManager.getInstance(Context)
            val instance = mgrClass
                .getMethod("getInstance", Context::class.java)
                .invoke(null, context)
            glyphManager = instance

            // Build a dynamic-proxy callback implementing GlyphManager.Callback so we can
            // react to onServiceConnected/onServiceDisconnected without compile-time types.
            val callbackClass = Class.forName("com.nothing.ketchum.GlyphManager\$Callback")
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
                GdkCallbackHandler(),
            )
            // GlyphManager.init(Callback)
            mgrClass.getMethod("init", callbackClass).invoke(instance, callback)
            Timber.tag(TAG).i("GlyphManager.init() invoked; awaiting service connection")
        } catch (t: Throwable) {
            // AAR absent or signature drift — stay unavailable (never fake it).
            Timber.tag(TAG).w(t, "GDK init failed; Glyph remains unavailable")
            glyphManager = null
            ready.set(false)
        }
    }

    /** Invoked (reflectively) when the GDK service connects. */
    private fun onServiceConnected() {
        val mgr = glyphManager ?: return
        val cls = managerClass ?: return
        try {
            val glyphClass = Class.forName("com.nothing.ketchum.Glyph")
            val numeric = detectNumericModel(glyphClass)
            
            val byNumeric = mapOf(
                GlyphAvailability.DeviceConstants.PHONE_1 to "DEVICE_20111",
                GlyphAvailability.DeviceConstants.PHONE_2 to "DEVICE_22111",
                GlyphAvailability.DeviceConstants.PHONE_2A to "DEVICE_23111",
                GlyphAvailability.DeviceConstants.PHONE_2A_PLUS to "DEVICE_23113",
                GlyphAvailability.DeviceConstants.PHONE_3A to "DEVICE_24111",
                GlyphAvailability.DeviceConstants.PHONE_4A to "DEVICE_25111",
            )
            val fieldName = byNumeric[numeric] ?: "DEVICE_23111"
            val deviceConst = runCatching {
                glyphClass.getField(fieldName).get(null) as String
            }.getOrDefault("A142")

            // GlyphManager.register(String) — the constant is exposed on com.nothing.ketchum.Glyph
            cls.getMethod("register", String::class.java).invoke(mgr, deviceConst)
            registered.set(true)
            // GlyphManager.openSession()
            cls.getMethod("openSession").invoke(mgr)
            ready.set(true)
            Timber.tag(TAG).i("GDK session open for device constant=%s, numeric=%d", deviceConst, numeric)
            buildZoneChannelMap(numeric.toString())
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "GDK register/openSession failed")
            ready.set(false)
            registered.set(false)
        }
    }

    /** Uses the GDK `is20111()/is22111()/…` boolean helpers to detect the live model. */
    private fun detectNumericModel(glyphClass: Class<*>): Int {
        val helpers = mapOf(
            "is20111" to GlyphAvailability.DeviceConstants.PHONE_1,
            "is22111" to GlyphAvailability.DeviceConstants.PHONE_2,
            "is23111" to GlyphAvailability.DeviceConstants.PHONE_2A,
            "is23113" to GlyphAvailability.DeviceConstants.PHONE_2A_PLUS,
            "is24111" to GlyphAvailability.DeviceConstants.PHONE_3A,
            "is25111" to GlyphAvailability.DeviceConstants.PHONE_4A,
        )
        for ((method, numeric) in helpers) {
            runCatching {
                val m: Method = glyphClass.getMethod(method)
                if (m.invoke(null) as? Boolean == true) return numeric
            }
        }
        
        // Robust fallback based on Build.MODEL / Build.DEVICE
        val model = android.os.Build.MODEL.orEmpty().lowercase()
        val device = android.os.Build.DEVICE.orEmpty().lowercase()
        return when {
            model.contains("a142") || model.contains("2a") || device.contains("a142") || device.contains("2a") -> {
                if (model.contains("plus") || device.contains("plus")) {
                    GlyphAvailability.DeviceConstants.PHONE_2A_PLUS
                } else {
                    GlyphAvailability.DeviceConstants.PHONE_2A
                }
            }
            model.contains("a065") || model.contains("phone (2)") || device.contains("a065") -> {
                GlyphAvailability.DeviceConstants.PHONE_2
            }
            model.contains("a063") || model.contains("phone (1)") || device.contains("a063") -> {
                GlyphAvailability.DeviceConstants.PHONE_1
            }
            else -> GlyphAvailability.DeviceConstants.PHONE_2A
        }
    }

    private fun teardownManager() {
        val mgr = glyphManager ?: return
        val cls = managerClass ?: return
        runCatching { cls.getMethod("closeSession").invoke(mgr) }
            .onFailure { Timber.tag(TAG).v(it, "closeSession failed (already closed?)") }
        runCatching { cls.getMethod("unInit").invoke(mgr) }
            .onFailure { Timber.tag(TAG).v(it, "unInit failed") }
        ready.set(false)
        registered.set(false)
        glyphManager = null
        Timber.tag(TAG).i("GDK session torn down")
    }

    // --- frame painting (reflection) -------------------------------------------

    /**
     * Bridges the choreographer's abstract zones onto GDK channels. Each paint builds a
     * `GlyphFrame` reflectively (`GlyphFrame.Builder().buildChannel(int...).build()`) and
     * applies it with `GlyphManager.toggle(frame)` at the requested intensity.
     */
    private inner class GdkRenderer : GlyphChoreographer.GlyphRenderer {
        override fun paint(zones: List<GlyphZone>, intensity: Float) {
            val mgr = glyphManager ?: return
            val cls = managerClass ?: return
            val brightness = (intensity.coerceIn(0f, 1f) * MAX_BRIGHTNESS).toInt()
            try {
                var builder: Any = cls.getMethod("getGlyphFrameBuilder").invoke(mgr)
                val frameBuilderClass = builder.javaClass
                // buildChannel(int) per zone-channel; chained.
                val channels = zones.flatMap { zoneChannels[it].orEmpty() }
                val buildChannel = runCatching {
                    frameBuilderClass.getMethod("buildChannel", Int::class.javaPrimitiveType)
                }.getOrNull()
                if (buildChannel != null) {
                    for (channelInt in channels) {
                        builder = buildChannel.invoke(builder, channelInt) ?: builder
                    }
                }
                
                // Many GDK implementations require a period/cycles to be set, otherwise toggle() does nothing.
                // We set a long period; the Choreographer will call turnOff() when it wants to end the frame.
                runCatching {
                    builder = frameBuilderClass.getMethod("buildPeriod", Int::class.javaPrimitiveType).invoke(builder, 10000) ?: builder
                    builder = frameBuilderClass.getMethod("buildCycles", Int::class.javaPrimitiveType).invoke(builder, 1) ?: builder
                }
                
                // buildPeriod(int)/buildCycles(int) are optional in the static-toggle path.
                val frame = frameBuilderClass.getMethod("build").invoke(builder)
                // GlyphManager.toggle(GlyphFrame) lights it; brightness applied where supported.
                applyBrightness(cls, mgr, brightness)
                val frameClass = Class.forName("com.nothing.ketchum.GlyphFrame")
                cls.getMethod("toggle", frameClass).invoke(mgr, frame)
            } catch (t: Throwable) {
                Timber.tag(TAG).v(t, "GDK paint skipped (method missing on installed AAR)")
            }
        }

        override fun paintWaveform(amplitude: Float) {
            // Mirror amplitude onto the whole strip via a single intensity paint. A true
            // GDK has `displayProgress`/`animate`; we keep the conservative toggle path.
            paint(listOf(GlyphZone.ALL), amplitude)
        }

        override fun flashTorch() {
            // §17.5 torch-on-connect: the GDK has no public torch API; torch belongs to
            // CameraManager and is owned by :telecom/:app. We intentionally do NOT fake it
            // here. Left as a documented no-op (honesty principle).
            Timber.tag(TAG).v("flashTorch is a no-op in GDK controller (owned elsewhere)")
        }

        override fun clear() {
            val mgr = glyphManager ?: return
            val cls = managerClass ?: return
            runCatching { cls.getMethod("turnOff").invoke(mgr) }
                .onFailure { Timber.tag(TAG).v(it, "turnOff failed/absent") }
        }
    }

    /** Apply brightness via `GlyphFrame.Builder.buildPeriod`-style API if present. */
    private fun applyBrightness(cls: Class<*>, mgr: Any, brightness: Int) {
        // Some GDK versions expose setBrightness; tolerate its absence.
        runCatching {
            cls.getMethod("setBrightness", Int::class.javaPrimitiveType).invoke(mgr, brightness)
        }
    }

    /** Resolves a GDK channel field name (e.g. `Glyph.Code_20111.A`) to its int value. */
    private fun resolveChannelInt(channelName: String): Int? = try {
        // channelName is a fully-qualified static field path "<ClassFqcn>#<FIELD>".
        val (clsName, field) = channelName.split("#", limit = 2)
        val c = Class.forName(clsName)
        c.getField(field).getInt(null)
    } catch (t: Throwable) {
        Timber.tag(TAG).v(t, "channel %s not resolvable on installed AAR", channelName)
        null
    }

    /**
     * Abstract zone → GDK channel field paths. These are the *common* channel groups
     * exposed by the GDK across light-strip models; resolution is best-effort and any
     * unresolved channel is simply skipped at paint time.
     *
     * NOTE: exact channel field names differ per model class (Glyph.Code_20111,
     * Glyph.Code_22111, …). We list the Phone (1) "Code_20111" set as the representative
     * mapping; the real AAR drop-in should refine per model if pixel-perfect zones matter.
     */
    private fun onServiceDisconnected() {
        Timber.tag(TAG).i("GDK service disconnected")
        ready.set(false)
        registered.set(false)
    }

    private fun buildZoneChannelMap(codeStr: String) {
        val intMap = mutableMapOf<GlyphZone, MutableList<Int>>()
        for (zone in GlyphZone.values()) {
            intMap[zone] = mutableListOf()
        }
        
        try {
            val clsName = "com.nothing.ketchum.Glyph\$Code_$codeStr"
            val c = Class.forName(clsName)
            val fields = c.getDeclaredFields()
            val allList = mutableListOf<Int>()
            
            for (f in fields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) {
                    f.isAccessible = true
                    val value = f.get(null) as? Int ?: continue
                    val name = f.name.uppercase()
                    allList.add(value)
                    
                    when {
                        name.startsWith("A") -> {
                            intMap[GlyphZone.TOP_LEFT]?.add(value)
                        }
                        name.startsWith("B") -> {
                            intMap[GlyphZone.TOP_RIGHT]?.add(value)
                            intMap[GlyphZone.CAMERA_RING]?.add(value)
                        }
                        name.startsWith("C") -> {
                            intMap[GlyphZone.CENTER]?.add(value)
                        }
                        name.startsWith("D") -> {
                            intMap[GlyphZone.BOTTOM_LEFT]?.add(value)
                        }
                        name.startsWith("E") -> {
                            intMap[GlyphZone.BOTTOM_RIGHT]?.add(value)
                            intMap[GlyphZone.BOTTOM_CENTER]?.add(value)
                        }
                    }
                }
            }
            intMap[GlyphZone.ALL] = allList.distinct().toMutableList()
            Timber.tag(TAG).i("Mapped Glyph zones reflectively for %s: %s", codeStr, intMap)
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "Reflective buildZoneChannelMap failed for %s", codeStr)
        }
        
        zoneChannels = intMap
    }

    /** Reflective dispatch for the dynamic-proxy GlyphManager.Callback. */
    private inner class GdkCallbackHandler : java.lang.reflect.InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "onServiceConnected" -> onServiceConnected()
                "onServiceDisconnected" -> onServiceDisconnected()
                // Object methods on the proxy.
                "toString" -> return "GdkCallbackHandler"
                "hashCode" -> return System.identityHashCode(this)
                "equals" -> return proxy === args?.getOrNull(0)
            }
            return null
        }
    }

    private companion object {
        const val TAG = "GdkGlyph"
        const val MAX_BRIGHTNESS = 4095 // GDK brightness is 12-bit on light-strip phones.
    }
}
