// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph.choreography

import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.domain.glyph.CallVisual
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Hardware-agnostic, NON-BLOCKING, DEBOUNCED dispatcher for Glyph choreography
 * (BUILD_SPEC §17.4/§17.5). It owns the timing/queuing policy; the actual painting of
 * a frame is delegated to a [GlyphRenderer] supplied by the concrete controller
 * (GDK light-strip or Glyph Matrix). This keeps all the testable scheduling logic here
 * and out of the reflection-heavy controllers.
 *
 * Behaviour:
 *  - Per-digit strokes are dispatched with cancel-and-restart so fast typing overlaps
 *    gracefully (§17.4): a new keypress cancels the previous *digit* job after a short
 *    [Constants.GLYPH_STROKE_DEBOUNCE_MS] debounce window and starts the new stroke.
 *  - Long-running call/recording animations run on their own job so a digit stroke does
 *    not clobber an active-call glow (digit and "ambient" channels are independent).
 *  - Intensity (0f..1f, mirrors `LocalGlyphIntensity`) scales every frame's brightness.
 *  - [renderWaveform] mirrors an audio amplitude onto the ambient channel, throttled to
 *    [Constants.WAVEFORM_THROTTLE_MS].
 *
 * All public methods return immediately; nothing blocks the caller.
 */
class GlyphChoreographer(
    dispatcher: CoroutineDispatcher,
    private val renderer: GlyphRenderer,
) {
    /**
     * The painting surface the choreographer drives. A frame's zones + the resolved
     * (intensity-scaled) brightness are handed to the renderer, which translates them
     * to GDK channels or matrix pixels. [clear] turns everything off.
     */
    interface GlyphRenderer {
        /** Paint [zones] at [intensity] (0f..1f, already intensity-scaled). */
        fun paint(zones: List<GlyphZone>, intensity: Float)

        /** Mirror an audio [amplitude] (0f..1f, already intensity-scaled). */
        fun paintWaveform(amplitude: Float)

        /** Optional torch flash on call connect (§17.5); default no-op. */
        fun flashTorch() {}

        /** Turn everything off. */
        fun clear()
    }

    // Independent supervisor so a failure in one animation never tears down the scope.
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Cancel-and-restart channel for transient per-digit strokes. */
    @Volatile private var digitJob: Job? = null

    /** Channel for the long-lived ambient animation (call state / recording / waveform). */
    @Volatile private var ambientJob: Job? = null

    @Volatile private var intensity: Float = 1f

    @Volatile private var lastWaveformAt: Long = 0L

    /** Update the master intensity (0f..1f) applied to every subsequent frame. */
    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
    }

    /**
     * Dispatch the stroke for [digit]. Cancel-and-restart: a fresh keypress replaces any
     * in-flight digit stroke after a brief debounce so rapid typing overlaps cleanly.
     */
    fun playDigit(digit: Char) {
        val stroke = GlyphStrokeLibrary.strokeFor(digit) ?: run {
            Timber.tag(TAG).v("No stroke for '%s'", digit)
            return
        }
        digitJob?.cancel()
        digitJob = scope.launch {
            // Debounce: coalesce bursts; if a newer key arrives this job is cancelled.
            delay(Constants.GLYPH_STROKE_DEBOUNCE_MS)
            runStroke(stroke)
        }
    }

    /**
     * Per-contact incoming-call show seeded deterministically from [contactSeed] (a hash
     * of the number, §17.5) so each caller is recognizable. Followed by a torch flash on
     * connect cue. Runs on the ambient channel and loops until replaced/cleared.
     */
    fun playIncomingShow(contactSeed: Int) {
        startAmbient {
            val show = seededShow(contactSeed)
            renderer.flashTorch()
            // Loop the seeded show until cancelled (call answered/ended).
            while (coroutineContext.isActive) {
                runStroke(show)
                delay(120)
            }
        }
    }

    /** Persistent recording indicator (§17.5): a steady slow double-blink while [active]. */
    fun showRecording(active: Boolean) {
        if (!active) {
            clearAmbient()
            return
        }
        startAmbient {
            while (coroutineContext.isActive) {
                renderFrame(GlyphFrame(listOf(GlyphZone.CAMERA_RING), 0.8f, 0.8f, 120))
                renderFrame(GlyphFrame(emptyList(), 0f, 0f, 120))
                renderFrame(GlyphFrame(listOf(GlyphZone.CAMERA_RING), 0.8f, 0.8f, 120))
                renderFrame(GlyphFrame(emptyList(), 0f, 0f, 900))
            }
        }
    }

    /**
     * Reflect the in-call [state] with its choreography (§17.5). RINGING/ACTIVE/HOLD/
     * CONFERENCE loop; ENDED plays a one-shot fade then clears.
     */
    fun showOnCall(state: CallVisual, partyCount: Int = 2) {
        when (state) {
            CallVisual.ENDED -> startAmbient(loop = false) {
                renderFrame(GlyphFrame(listOf(GlyphZone.ALL), 0.7f, 0f, 400))
                renderer.clear()
            }
            CallVisual.RINGING -> startAmbient {
                while (coroutineContext.isActive) {
                    renderFrame(GlyphFrame(listOf(GlyphZone.ALL), 0f, 1f, 160))
                    renderFrame(GlyphFrame(listOf(GlyphZone.ALL), 1f, 0f, 160))
                    renderFrame(GlyphFrame(emptyList(), 0f, 0f, 120))
                }
            }
            CallVisual.ACTIVE -> startAmbient {
                // Slow steady breathing glow.
                while (coroutineContext.isActive) {
                    renderFrame(GlyphFrame(listOf(GlyphZone.ALL), 0.15f, 0.5f, 1400))
                    renderFrame(GlyphFrame(listOf(GlyphZone.ALL), 0.5f, 0.15f, 1400))
                }
            }
            CallVisual.HOLD -> startAmbient {
                // Faster, lower breathing to read as "paused".
                while (coroutineContext.isActive) {
                    renderFrame(GlyphFrame(listOf(GlyphZone.CENTER), 0.05f, 0.4f, 700))
                    renderFrame(GlyphFrame(listOf(GlyphZone.CENTER), 0.4f, 0.05f, 700))
                }
            }
            CallVisual.CONFERENCE -> startAmbient {
                // Multi-zone pulse whose lit-zone count scales with party count.
                val zones = conferenceZones(partyCount)
                while (coroutineContext.isActive) {
                    for (z in zones) {
                        renderFrame(GlyphFrame(listOf(z), 0.2f, 1f, 180))
                        renderFrame(GlyphFrame(listOf(z), 1f, 0.2f, 120))
                    }
                    delay(200)
                }
            }
        }
    }

    /**
     * Mirror an audio [amplitude] (0f..1f) onto the Glyph waveform, throttled to
     * [Constants.WAVEFORM_THROTTLE_MS]. Cheap and synchronous — no coroutine churn.
     */
    fun renderWaveform(amplitude: Float) {
        val now = System.currentTimeMillis()
        if (now - lastWaveformAt < Constants.WAVEFORM_THROTTLE_MS) return
        lastWaveformAt = now
        renderer.paintWaveform((amplitude.coerceIn(0f, 1f)) * intensity)
    }

    /** Cancel everything and clear the lights. */
    fun release() {
        digitJob?.cancel()
        ambientJob?.cancel()
        runCatching { renderer.clear() }
        scope.cancel()
    }

    // --- internals --------------------------------------------------------------

    private fun startAmbient(loop: Boolean = true, block: suspend CoroutineScope.() -> Unit) {
        ambientJob?.cancel()
        ambientJob = scope.launch {
            try {
                block()
            } finally {
                // For one-shot animations leave the final state; loops are cancelled
                // and the next startAmbient/clearAmbient resets the surface.
                if (!loop) {
                    // no-op; block already cleared if it wanted to
                }
            }
        }
    }

    private fun clearAmbient() {
        ambientJob?.cancel()
        ambientJob = null
        runCatching { renderer.clear() }
    }

    /** Plays one full stroke (its frames in order), respecting cancellation. */
    private suspend fun runStroke(stroke: GlyphStroke) {
        Timber.tag(TAG).v("stroke '%s' (%s, %dms)", stroke.key, stroke.concept, stroke.periodMs)
        for (frame in stroke.frames) {
            coroutineContext.ensureActive()
            renderFrame(frame)
        }
        renderer.clear()
    }

    /**
     * Renders a single [frame]: lights its zones at the (intensity-scaled) start
     * brightness, holds for the frame duration, then leaves the end brightness for the
     * next frame to pick up. A linear ramp is approximated by a midpoint sub-step so a
     * single frame still reads as a ramp on hardware that can't natively ramp.
     */
    private suspend fun renderFrame(frame: GlyphFrame) {
        if (frame.zones.isEmpty()) {
            renderer.clear()
            delay(frame.durationMs.toLong())
            return
        }
        if (abs(frame.toIntensity - frame.fromIntensity) < 0.01f) {
            // Flat frame: one paint + hold.
            renderer.paint(frame.zones, frame.fromIntensity * intensity)
            delay(frame.durationMs.toLong())
        } else {
            // Ramp frame: split into start / midpoint / end sub-steps for a smoother feel.
            val third = (frame.durationMs / 3).coerceAtLeast(1).toLong()
            val mid = (frame.fromIntensity + frame.toIntensity) / 2f
            renderer.paint(frame.zones, frame.fromIntensity * intensity)
            delay(third)
            coroutineContext.ensureActive()
            renderer.paint(frame.zones, mid * intensity)
            delay(third)
            coroutineContext.ensureActive()
            renderer.paint(frame.zones, frame.toIntensity * intensity)
            delay(frame.durationMs - 2 * third)
        }
    }

    /**
     * Builds a deterministic per-contact "fingerprint" stroke from [seed]. Uses the seed
     * to pick a zone permutation, beat count, and intensities — same number → same show.
     * Pure function of the seed (no SDK), so it can be unit-tested.
     */
    internal fun seededShow(seed: Int): GlyphStroke {
        val zones = GlyphZone.entries.filter { it != GlyphZone.ALL }
        val n = zones.size
        // A small LCG over the seed for reproducible pseudo-randomness.
        var state = (seed.toLong() and 0xFFFFFFFFL) or 1L
        fun next(): Int {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            return ((state ushr 33).toInt() and Int.MAX_VALUE)
        }
        val beats = 3 + next() % 4 // 3..6 beats
        val frames = buildList {
            repeat(beats) {
                val zone = zones[next() % n]
                val intensity = 0.5f + (next() % 51) / 100f // 0.50..1.00
                add(GlyphFrame(listOf(zone), 0.1f, intensity, 90))
                add(GlyphFrame(listOf(zone), intensity, 0.1f, 60))
            }
        }
        return GlyphStroke('@', "incoming#$seed", frames)
    }

    /** How many zones pulse in a conference, scaling with [partyCount]. */
    private fun conferenceZones(partyCount: Int): List<GlyphZone> {
        val pool = GlyphZone.entries.filter { it != GlyphZone.ALL }
        val count = partyCount.coerceIn(2, pool.size)
        return pool.take(count)
    }

    private companion object {
        const val TAG = "GlyphChoreographer"
    }
}
