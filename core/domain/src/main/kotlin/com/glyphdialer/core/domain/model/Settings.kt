// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/** App theme selection; persisted in DataStore (§15.2). */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * User-selectable typeface family (§16). OG = dot-matrix face; NEW = grotesque.
 * Numerals always render in the monospaced cut regardless of selection.
 */
enum class AppFont { OG_DOT_MATRIX, NEW_GROTESQUE }

/**
 * Curated accent colors over the monochrome base (§15.1, §18). The default is the
 * Glyph red. [argb] is a packed 0xAARRGGBB int so domain stays free of Compose/
 * Android color types.
 */
enum class AccentColor(val argb: Int) {
    GLYPH_RED(0xFFD7263D.toInt()),
    AMBER(0xFFE8A100.toInt()),
    LIME(0xFF8BC34A.toInt()),
    CYAN(0xFF00BCD4.toInt()),
    VIOLET(0xFF7C4DFF.toInt()),
    MONO(0xFFEDEDED.toInt());

    companion object {
        val DEFAULT = GLYPH_RED
    }
}

/**
 * The transcription engine the user has selected (§13). Availability of each is a
 * runtime concern surfaced via [CapabilityFlags]; the setting is the *preference*.
 */
enum class TranscriptionEngineType {
    /** Bundled on-device Whisper model — privacy-first default. */
    ON_DEVICE_WHISPER,

    /** Android SpeechRecognizer — low-latency live captions where supported. */
    ANDROID_SPEECH_RECOGNIZER,

    /** Google ML Kit on-device. */
    ML_KIT,

    /** Optional cloud STT behind a flag (network + consent required). */
    CLOUD,

    /** Transcription disabled entirely. */
    NONE,
}

/**
 * Auto-purge retention window for recordings/transcripts (§12, §19). Used by the
 * purge worker to compute the cutoff.
 */
enum class RetentionWindow(val days: Int?) {
    DAYS_30(30),
    DAYS_90(90),
    DAYS_180(180),
    NEVER(null);

    /** The cutoff timestamp (millis) before which data should be purged, or null for NEVER. */
    fun cutoffMillis(now: Long): Long? = days?.let { now - it * MILLIS_PER_DAY }

    companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        val DEFAULT = DAYS_180
    }
}

/**
 * Aggregated, persisted user preferences (DataStore-backed, §19/§21). This is the
 * single immutable settings snapshot the rest of the app observes via
 * [com.glyphdialer.core.domain.repository.SettingsRepository].
 */
data class UserPreferences(
    /** Cached entitlement restored from Google Play Billing. Never user-editable. */
    val appPlan: AppPlan = AppPlan.FREE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appFont: AppFont = AppFont.OG_DOT_MATRIX,
    val accentColor: AccentColor = AccentColor.DEFAULT,
    // Glyph
    val glyphMasterEnabled: Boolean = true,
    val glyphDialpadStrokes: Boolean = true,
    /** Glyph intensity 0f..1f. */
    val glyphIntensity: Float = 0.8f,
    val glyphIncomingShow: Boolean = true,
    val glyphRecordingIndicator: Boolean = true,
    // Recording
    val recordingEnabled: Boolean = false,
    /** §2.2: user-chosen, with disclaimer; never bypasses an OS-mandated announcement. */
    val recordingNoAnnouncement: Boolean = false,
    val retentionWindow: RetentionWindow = RetentionWindow.DEFAULT,
    // Transcription
    val transcriptionEngine: TranscriptionEngineType = TranscriptionEngineType.ON_DEVICE_WHISPER,
    val liveCaptionsEnabled: Boolean = false,
    val autoTranscribe: Boolean = false,
    /** BCP-47 tag for forced transcription language, or null for auto-detect. */
    val transcriptionLanguage: String? = null,
    // Calls
    val callerIdSpamEnabled: Boolean = true,
    val incomingCallVibrationEnabled: Boolean = true,
    val callEndVibrationEnabled: Boolean = true,
    val softDialVibrationEnabled: Boolean = true,
    /**
     * Best-effort accelerometer gesture while an in-call service is active. Android
     * exposes no public Pixel/Nothing "back tap" API, so this is intentionally
     * experimental and user-controlled.
     */
    val backTapCallControlEnabled: Boolean = false,
    /** Subscription id of the preferred default SIM on dual-SIM devices, or null. */
    val defaultSimSubscriptionId: Int? = null,
    /** Filter contacts to a single account type (e.g. "com.google"), or null for all. */
    val contactsAccountFilter: String? = null,
)

/**
 * Runtime capability/availability flags — the machine-readable backbone of the
 * honesty principle (§9). Resolved at startup/per-device and surfaced in the UI so
 * features are never offered when the platform can't honestly deliver them.
 *
 * These are NOT user preferences — they describe what the device/OS can actually do.
 */
data class CapabilityFlags(
    /** Nothing hardware on Android 14+ with a valid Glyph key (§17). */
    val glyphAvailable: Boolean = false,
    /** Is the app currently the default dialer (gates call-log write, etc.)? */
    val isDefaultDialer: Boolean = false,
    /** OS/OEM exposes call audio for two-way capture (SYSTEM_TWO_WAY tier — §2.1). */
    val systemCallAudio: Boolean = false,
    /** In-app WebRTC VoIP is available (signaling backend reachable — §11). */
    val voipAvailable: Boolean = false,
    /** Carrier visual-voicemail is supported on this line (§8). */
    val vvmSupported: Boolean = false,
    /** RECORD_AUDIO granted and a recorder is usable (at least LOCAL_ONE_SIDED). */
    val microphoneAvailable: Boolean = false,
    /** A camera exists for in-app video calling (§11). */
    val cameraAvailable: Boolean = false,
    /** On-device speech recognition is present for live captions (§13). */
    val onDeviceSpeechAvailable: Boolean = false,
    /** The best recording tier achievable on this device, resolved at runtime (§12). */
    val maxRecordingTier: RecordingTier = RecordingTier.UNAVAILABLE,
) {
    /** Whether any honest call-recording is possible at all. */
    val recordingPossible: Boolean get() = maxRecordingTier.isAvailable
}
