// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.glyph

/**
 * Abstraction over the Nothing Glyph Interface (§17).
 *
 * Implementations live ONLY in `:peripheral:glyph` (the one module allowed to
 * import `com.nothing.ketchum.*` / the Glyph Matrix SDK). Every other module talks
 * to this interface so the app degrades to a graceful no-op on non-Nothing
 * hardware.
 *
 * HONESTY PRINCIPLE (§9/§17.6): when Glyph is unavailable — non-Nothing device,
 * pre-Android-14, missing API key/permission — [isAvailable] is `false` and EVERY
 * method is a no-op. Callers may invoke methods unconditionally; the UI hides Glyph
 * settings when [isAvailable] is false and the app never crashes.
 */
interface GlyphController {
    /**
     * True only on supported Nothing hardware with the Glyph session established.
     * Must be safe to read on any device.
     */
    val isAvailable: Boolean

    /** Fire the distinct per-key light stroke for a dialpad [digit] (§17.4). */
    fun playDigitStroke(digit: Char)

    /**
     * Play the per-contact incoming-call light show, seeded deterministically from
     * a hash of the caller's number ([contactSeed]) so each caller is recognizable
     * (§17.5).
     */
    fun playIncomingShow(contactSeed: Int)

    /** Show/clear the persistent recording indicator pattern (§17.5). */
    fun showRecording(active: Boolean)

    /** Mirror an audio amplitude [amplitude] (0f..1f) onto the Glyph waveform (§17.5). */
    fun renderWaveform(amplitude: Float)

    /** Reflect the in-call state with the matching choreography (§17.5). */
    fun showOnCall(state: CallVisual)

    /** Release the Glyph session and any held resources. Safe to call repeatedly. */
    fun release()
}

/** The in-call visual states the Glyph choreography reflects (§17.5). */
enum class CallVisual {
    /** Incoming/outgoing ringing — attention pattern. */
    RINGING,

    /** Active connected call — slow steady glow. */
    ACTIVE,

    /** Call on hold — breathing. */
    HOLD,

    /** Conference — multi-zone pulse by party count. */
    CONFERENCE,

    /** Call ended — brief fade-out. */
    ENDED,
}
