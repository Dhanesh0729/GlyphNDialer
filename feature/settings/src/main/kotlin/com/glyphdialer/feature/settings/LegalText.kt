// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

/**
 * Static, user-facing legal and attribution copy surfaced by the Settings and
 * About screens (BUILD_SPEC §2.2, §21; CONVENTIONS.md §9).
 *
 * Kept as plain constants (not Android string resources) so the exact, reviewed
 * wording is version-controlled with the feature and is identical everywhere it is
 * shown. A future localization pass can move these into `strings.xml`.
 */
object LegalText {

    /**
     * The §2.2 "record without announcement" disclaimer. Shown directly beneath the
     * no-announcement toggle in the Recording group AND in the About/legal screen.
     *
     * The honesty principle requires that the app never hard-codes deceptive
     * behaviour: this toggle changes only the in-app announcement; it can never
     * bypass an OS- or carrier-mandated announcement tone.
     */
    const val NO_ANNOUNCEMENT_DISCLAIMER: String =
        "Recording calls without informing the other party is restricted or illegal " +
            "in many regions (two-party-consent jurisdictions). You are solely " +
            "responsible for complying with the laws that apply to you. This setting " +
            "only suppresses Glyph Dialer's own announcement — it can never bypass an " +
            "announcement that your phone's OS or carrier plays."

    /**
     * General recording-law disclaimer for the About/legal screen, covering the
     * platform feasibility reality (§2.1) as well as legality.
     */
    const val RECORDING_LAW_DISCLAIMER: String =
        "Call-recording laws vary by country and state. In two-party-consent regions " +
            "all parties must consent to being recorded. Additionally, since Android " +
            "10 the operating system blocks third-party apps from capturing the remote " +
            "party on ordinary cellular calls — Glyph Dialer records at the highest " +
            "tier your device honestly supports and always shows you whether a " +
            "recording captured both sides or your side only. You are responsible for " +
            "using this feature lawfully."

    /**
     * Open-font attribution (§10/§16). Real OFL `.ttf` binaries are dropped in by the
     * integrator (see FONTS.md); this credits the open families and is NOT a claim of
     * any Nothing proprietary typeface.
     */
    const val FONT_ATTRIBUTION: String =
        "Typefaces are user-supplied open-licensed fonts. The \"OG\" dot-matrix face " +
            "and the \"New\" grotesque face are SIL Open Font License (OFL) substitutes; " +
            "Glyph Dialer does not bundle or claim any proprietary Nothing typeface " +
            "(NDot / NType 82). Monospaced numerals use an OFL monospace family."

    /**
     * Glyph SDK / brand attribution (§9/§17). Glyph integration is optional and only
     * active on Nothing hardware via the official GDK behind the GlyphController
     * abstraction.
     */
    const val GLYPH_SDK_ATTRIBUTION: String =
        "Glyph effects use the official Nothing Glyph Developer Kit (GDK) and are " +
            "available only on supported Nothing hardware running Android 14+ with a " +
            "valid Glyph key. Glyph Dialer is an independent product, visually inspired " +
            "by Nothing OS; it is not affiliated with, endorsed by, or an official " +
            "product of Nothing Technology Limited. \"Nothing\" and \"Glyph\" are " +
            "trademarks of their respective owner."

    /** Short one-line app identity for the About header. */
    const val APP_TAGLINE: String =
        "An independent, Nothing-OS-inspired dialer. Not an official Nothing product."
}
