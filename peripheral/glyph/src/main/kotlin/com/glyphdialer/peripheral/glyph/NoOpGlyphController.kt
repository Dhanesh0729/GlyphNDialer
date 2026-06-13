// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.glyph

import com.glyphdialer.core.domain.glyph.CallVisual
import com.glyphdialer.core.domain.glyph.GlyphController
import timber.log.Timber

/**
 * The universal fallback [GlyphController] (§9/§17.6 HONESTY PRINCIPLE).
 *
 * Used on every non-Nothing device, on pre-Android-14 Nothing devices, when the GDK
 * AAR is absent, or when the API key/permission is missing. [isAvailable] is always
 * `false` and every method is a no-op, so callers may invoke them unconditionally and
 * the app never crashes. The optional [reason] is surfaced by the Settings UI to
 * explain *why* Glyph is unavailable rather than silently hiding it.
 */
class NoOpGlyphController(
    /** Why Glyph is unavailable, for diagnostics/UI; `null` if simply unknown. */
    val reason: String? = null,
) : GlyphController {

    override val isAvailable: Boolean = false

    override fun playDigitStroke(digit: Char) = noOp("playDigitStroke('$digit')")

    override fun playIncomingShow(contactSeed: Int) = noOp("playIncomingShow($contactSeed)")

    override fun showRecording(active: Boolean) = noOp("showRecording($active)")

    override fun renderWaveform(amplitude: Float) {
        // Intentionally silent — called at high frequency; logging would spam.
    }

    override fun showOnCall(state: CallVisual) = noOp("showOnCall($state)")

    override fun release() = noOp("release")

    private fun noOp(action: String) {
        Timber.tag(TAG).v("Glyph unavailable; ignoring %s (reason=%s)", action, reason)
    }

    private companion object {
        const val TAG = "NoOpGlyph"
    }
}
