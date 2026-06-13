// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

import com.glyphdialer.core.domain.model.AccentColor
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.RetentionWindow
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.model.TranscriptionEngineType

/**
 * Display-label mappings for the settings enums. Kept in one place (next to the
 * contract) so every group, preview, and test renders the same human-readable text.
 * Pure functions — no Android/Compose imports — so they are unit-testable.
 */
internal object SettingsLabels {

    fun theme(mode: ThemeMode): String = when (mode) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.SYSTEM -> "System"
    }

    fun font(font: AppFont): String = when (font) {
        AppFont.OG_DOT_MATRIX -> "OG"
        AppFont.NEW_GROTESQUE -> "New"
    }

    fun accent(accent: AccentColor): String = when (accent) {
        AccentColor.GLYPH_RED -> "Red"
        AccentColor.AMBER -> "Amber"
        AccentColor.LIME -> "Lime"
        AccentColor.CYAN -> "Cyan"
        AccentColor.VIOLET -> "Violet"
        AccentColor.MONO -> "Mono"
    }

    fun retention(window: RetentionWindow): String = when (window) {
        RetentionWindow.DAYS_30 -> "30 days"
        RetentionWindow.DAYS_90 -> "90 days"
        RetentionWindow.DAYS_180 -> "180 days"
        RetentionWindow.NEVER -> "Never"
    }

    fun engine(engine: TranscriptionEngineType): String = when (engine) {
        TranscriptionEngineType.ON_DEVICE_WHISPER -> "On-device (Whisper)"
        TranscriptionEngineType.ANDROID_SPEECH_RECOGNIZER -> "Android speech"
        TranscriptionEngineType.ML_KIT -> "ML Kit"
        TranscriptionEngineType.CLOUD -> "Cloud"
        TranscriptionEngineType.NONE -> "Off"
    }

    /** Whether choosing [engine] requires on-device speech to be honestly usable (§13). */
    fun engineNeedsOnDeviceSpeech(engine: TranscriptionEngineType): Boolean =
        engine == TranscriptionEngineType.ANDROID_SPEECH_RECOGNIZER

    /** Whether [engine] sends audio off-device (network + consent implications, §13). */
    fun engineIsCloud(engine: TranscriptionEngineType): Boolean =
        engine == TranscriptionEngineType.CLOUD

    /**
     * The honesty-critical recording-tier label (§12). Never claims two-way capture
     * when only the local side is available.
     */
    fun tier(tier: RecordingTier): String = when (tier) {
        RecordingTier.SYSTEM_TWO_WAY -> "Two-way (system)"
        RecordingTier.VOIP_TWO_WAY -> "Two-way (VoIP)"
        RecordingTier.LOCAL_ONE_SIDED -> "My side only"
        RecordingTier.UNAVAILABLE -> "Unavailable"
    }

    /** A one-line, plain explanation of what [tier] means for the user. */
    fun tierExplanation(tier: RecordingTier): String = when (tier) {
        RecordingTier.SYSTEM_TWO_WAY ->
            "Your device exposes call audio, so both sides can be recorded."
        RecordingTier.VOIP_TWO_WAY ->
            "In-app VoIP calls own both audio tracks, so both sides can be recorded."
        RecordingTier.LOCAL_ONE_SIDED ->
            "On stock Android only your microphone can be captured — the other party " +
                "is not recorded. Recordings are clearly labelled \"my side only\"."
        RecordingTier.UNAVAILABLE ->
            "Recording is not supported on this device or call."
    }

    /** A coarse BCP-47 language menu used by the transcription group (auto-detect first). */
    val LANGUAGE_OPTIONS: List<Pair<String?, String>> = listOf(
        null to "Auto-detect",
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "es-ES" to "Spanish",
        "fr-FR" to "French",
        "de-DE" to "German",
        "hi-IN" to "Hindi",
        "ja-JP" to "Japanese",
        "zh-CN" to "Chinese",
    )

    fun language(bcp47: String?): String =
        LANGUAGE_OPTIONS.firstOrNull { it.first == bcp47 }?.second ?: (bcp47 ?: "Auto-detect")
}
