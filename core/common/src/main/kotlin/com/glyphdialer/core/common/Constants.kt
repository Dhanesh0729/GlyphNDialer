// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.common

/**
 * App-wide constants shared across modules. Keep these stable: notification
 * channel ids and DataStore names are persisted/registered against the OS, so
 * renaming them post-release orphans channels and preferences.
 */
object Constants {

    /** Stable app/log tag and base for derived identifiers. */
    const val APP_TAG: String = "GlyphDialer"

    // --- Data retention (see BUILD_SPEC §12 / §19) -------------------------

    /** Default auto-purge window for recordings & transcripts, in days. */
    const val DEFAULT_RETENTION_DAYS: Int = 180

    /** Selectable retention windows in days; 0 is treated as "never purge". */
    const val RETENTION_DAYS_30: Int = 30
    const val RETENTION_DAYS_90: Int = 90
    const val RETENTION_DAYS_180: Int = 180
    const val RETENTION_NEVER: Int = 0

    // --- DataStore (settings) ----------------------------------------------

    /** Name of the Preferences DataStore backing user settings. */
    const val PREFERENCES_DATASTORE_NAME: String = "glyph_dialer_prefs"

    // --- Room ---------------------------------------------------------------

    /** Filename of the app's Room database. */
    const val DATABASE_NAME: String = "glyph_dialer.db"

    // --- Notification channels (registered at app start) -------------------

    object NotificationChannels {
        /** Ongoing in-call foreground-service notification. */
        const val ONGOING_CALL: String = "glyph_dialer.channel.ongoing_call"

        /** Incoming-call high-importance / full-screen-intent notification. */
        const val INCOMING_CALL: String = "glyph_dialer.channel.incoming_call"

        /** Missed-call alerts. */
        const val MISSED_CALL: String = "glyph_dialer.channel.missed_call"

        /** Active call-recording indicator. */
        const val RECORDING: String = "glyph_dialer.channel.recording"

        /** Background transcription progress/results. */
        const val TRANSCRIPTION: String = "glyph_dialer.channel.transcription"

        /** New visual-voicemail notifications. */
        const val VOICEMAIL: String = "glyph_dialer.channel.voicemail"
    }

    // --- Notification ids ---------------------------------------------------

    object NotificationIds {
        const val ONGOING_CALL: Int = 1001
        const val INCOMING_CALL: Int = 1002
        const val RECORDING: Int = 1003
        const val TRANSCRIPTION: Int = 1004
    }

    // --- WorkManager unique work names (see BUILD_SPEC §19) -----------------

    object Work {
        const val PURGE_OLD_DATA: String = "glyph_dialer.work.purge_old_data"
        const val CONTACTS_CACHE: String = "glyph_dialer.work.contacts_cache"
        const val TRANSCRIPTION: String = "glyph_dialer.work.transcription"
    }

    // --- Telephony / formatting --------------------------------------------

    /** Region used as a libphonenumber fallback when none can be inferred. */
    const val DEFAULT_REGION: String = "US"

    /** `tel:` URI scheme for placing calls. */
    const val TEL_SCHEME: String = "tel"

    /** `voicemail:` URI scheme. */
    const val VOICEMAIL_SCHEME: String = "voicemail"

    // --- Glyph / UI timing --------------------------------------------------

    /**
     * Debounce window (ms) for queuing per-digit Glyph strokes during fast
     * typing, so taps overlap gracefully rather than blocking (BUILD_SPEC §17.4).
     */
    const val GLYPH_STROKE_DEBOUNCE_MS: Long = 60L

    /** Throttle window (ms) for waveform/amplitude UI + Glyph rendering updates. */
    const val WAVEFORM_THROTTLE_MS: Long = 50L
}
