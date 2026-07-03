// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings

import com.glyphdialer.core.domain.model.AccentColor
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.CapabilityFlags
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.RetentionWindow
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.model.UserPreferences

/**
 * MVVM contract for the Settings screen (BUILD_SPEC §21; CONVENTIONS.md §5).
 *
 * The ViewModel exposes a single immutable [SettingsUiState] via `StateFlow`,
 * receives user intent through [SettingsEvent], and emits one-shot side effects
 * (navigation, external intents, transient messages) through [SettingsEffect].
 *
 * HONESTY PRINCIPLE (CONVENTIONS.md §9 / BUILD_SPEC §2): every capability-gated
 * group consults the live [CapabilityFlags] before it is offered:
 *  - the Glyph group is HIDDEN entirely when [CapabilityFlags.glyphAvailable] is false;
 *  - the Recording group surfaces the *active* [RecordingTier] read-only and never
 *    claims two-way capture when only the local side is available;
 *  - the "no-announcement" toggle is always shown WITH the §2.2 legal disclaimer and
 *    never bypasses an OS-mandated announcement.
 */

/** The collapsible groups on the settings screen, in display order (BUILD_SPEC §21). */
enum class SettingsGroup {
    APPEARANCE,
    GLYPH,
    CALLS,
    RECORDING,
    TRANSCRIPTION,
    CONTACTS,
    ABOUT,
}

/**
 * Immutable UI state for the settings root.
 *
 * [preferences] is the persisted snapshot the controls bind to; [capabilities] is the
 * runtime honesty backbone that decides which groups/controls are offered. Both come
 * from observable repositories, so the screen reacts live to a permission/role/Glyph
 * change without a manual refresh.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val capabilities: CapabilityFlags = CapabilityFlags(),
    /** Contacts sync freshness shown in the Contacts group (best-effort, may be null). */
    val contactsSyncStatus: ContactsSyncStatus = ContactsSyncStatus.Unknown,
    /** A presentable, transient error message (cleared after it is shown). */
    val errorMessage: String? = null,
) {
    /** Glyph settings are offered ONLY on capable Nothing hardware (§9/§17). */
    val showGlyphGroup: Boolean get() = capabilities.glyphAvailable

    /** The best recording tier achievable on this device, surfaced read-only (§12). */
    val activeRecordingTier: RecordingTier get() = capabilities.maxRecordingTier

    /** Whether any honest recording is possible at all (gates the Recording controls). */
    val recordingPossible: Boolean get() = capabilities.recordingPossible

    /** Live captions need on-device speech to be honestly offered (§13/§9). */
    val liveCaptionsOfferable: Boolean get() = capabilities.onDeviceSpeechAvailable
}

/** Coarse contacts-sync freshness for the Contacts group (BUILD_SPEC §14/§21). */
sealed interface ContactsSyncStatus {
    /** Not yet resolved. */
    data object Unknown : ContactsSyncStatus

    /** Android's SyncAdapter last synced at [lastSyncMillis] (null if never). */
    data class Synced(val lastSyncMillis: Long?) : ContactsSyncStatus

    /** A sync is currently in progress. */
    data object Syncing : ContactsSyncStatus
}

/**
 * User intents from the settings screen. One event per discrete control so the
 * ViewModel maps each to a single atomic preference transform (no read-modify-write
 * races; CONVENTIONS.md §5 + [UpdatePreferencesUseCase]).
 */
sealed interface SettingsEvent {
    // ---- Appearance ----------------------------------------------------------------
    data class SetThemeMode(val mode: ThemeMode) : SettingsEvent
    data class SetAppFont(val font: AppFont) : SettingsEvent
    data class SetAccent(val accent: AccentColor) : SettingsEvent

    // ---- Glyph (only emitted when the group is visible) ----------------------------
    data class SetGlyphMasterEnabled(val enabled: Boolean) : SettingsEvent
    data class SetGlyphDialpadStrokes(val enabled: Boolean) : SettingsEvent
    data class SetGlyphIntensity(val intensity: Float) : SettingsEvent
    data class SetGlyphIncomingShow(val enabled: Boolean) : SettingsEvent
    data class SetGlyphRecordingIndicator(val enabled: Boolean) : SettingsEvent

    // ---- Calls ---------------------------------------------------------------------
    data class SetCallerIdSpam(val enabled: Boolean) : SettingsEvent

    // ---- Recording -----------------------------------------------------------------
    data class SetRecordingEnabled(val enabled: Boolean) : SettingsEvent

    /**
     * Toggle "record without announcement" (§2.2). The ViewModel only persists the
     * preference; it NEVER bypasses an OS-mandated announcement. The UI shows the
     * legal disclaimer next to this control unconditionally.
     */
    data class SetNoAnnouncement(val enabled: Boolean) : SettingsEvent
    data class SetRetentionWindow(val window: RetentionWindow) : SettingsEvent

    // ---- Transcription -------------------------------------------------------------
    data class SetTranscriptionEngine(val engine: TranscriptionEngineType) : SettingsEvent
    data class SetLiveCaptions(val enabled: Boolean) : SettingsEvent
    data class SetAutoTranscribe(val enabled: Boolean) : SettingsEvent

    /** BCP-47 tag, or null for auto-detect. */
    data class SetTranscriptionLanguage(val bcp47: String?) : SettingsEvent

    // ---- Contacts ------------------------------------------------------------------
    /** Account type filter (e.g. "com.google"), or null for "all accounts". */
    data class SetContactsAccountFilter(val accountType: String?) : SettingsEvent

    // ---- Navigation requests -------------------------------------------------------
    data object OpenBlockedNumbers : SettingsEvent
    data object OpenSpeedDial : SettingsEvent
    data object OpenAbout : SettingsEvent
    data object OpenOpenSourceLicenses : SettingsEvent

    // ---- Misc ----------------------------------------------------------------------
    data object DismissError : SettingsEvent
}

/**
 * One-shot effects. In-feature sub-screens (blocked numbers, about, open-source
 * licenses) are navigated to via the host [NavController], while speed-dial assignment
 * (which lives in :feature:contacts/:feature:dialpad territory) is delegated to the
 * :app host so this feature stays inside its allowed dependency set (§3).
 */
sealed interface SettingsEffect {
    /** Navigate to the in-feature blocked-numbers sub-screen. */
    data object NavigateToBlockedNumbers : SettingsEffect

    /** Navigate to the in-feature About/legal sub-screen. */
    data object NavigateToAbout : SettingsEffect

    /** Ask the host to open the speed-dial assignment surface. */
    data object OpenSpeedDial : SettingsEffect

    /** Navigate to the in-feature open-source-licenses sub-screen. */
    data object OpenOpenSourceLicenses : SettingsEffect

    /** Show a transient message (snackbar/toast). */
    data class ShowMessage(val message: String) : SettingsEffect
}
