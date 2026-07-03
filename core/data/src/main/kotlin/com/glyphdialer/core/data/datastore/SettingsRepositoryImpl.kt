// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.common.appResultOfSuspend
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.AccentColor
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.AppPlan
import com.glyphdialer.core.domain.model.RetentionWindow
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences-DataStore-backed [SettingsRepository] (CONVENTIONS.md §5/§6,
 * BUILD_SPEC §19/§21).
 *
 * Maps every field of [UserPreferences] to a typed key. Enums are stored by stable
 * [Enum.name] and decoded defensively (unknown/legacy values fall back to the
 * model default) so a renamed enum constant never crashes reads. Missing keys
 * resolve to the [UserPreferences] data-class defaults — i.e. first-run yields a
 * fully-defaulted snapshot with no migration needed.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @Dispatcher(GlyphDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    override val preferences: Flow<UserPreferences> =
        dataStore.data
            .catch { e ->
                if (e is IOException) {
                    Timber.w(e, "Failed to read preferences; emitting empty")
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw e
                }
            }
            .map { it.toUserPreferences() }
            .flowOn(ioDispatcher)

    override suspend fun current(): AppResult<UserPreferences> =
        appResultOfSuspend { preferences.first() }

    override suspend fun update(transform: (UserPreferences) -> UserPreferences): AppResult<Unit> =
        appResultOfSuspend {
            dataStore.edit { prefs ->
                val updated = transform(prefs.toUserPreferences())
                prefs.writeFrom(updated)
            }
            Unit
        }

    override suspend fun reset(): AppResult<Unit> =
        appResultOfSuspend {
            dataStore.edit { it.clear() }
            Unit
        }

    // --- mapping ------------------------------------------------------------

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            appPlan = decodeEnum(this[Keys.APP_PLAN], defaults.appPlan),
            themeMode = decodeEnum(this[Keys.THEME_MODE], defaults.themeMode),
            appFont = decodeEnum(this[Keys.APP_FONT], defaults.appFont),
            accentColor = decodeEnum(this[Keys.ACCENT_COLOR], defaults.accentColor),
            glyphMasterEnabled = this[Keys.GLYPH_MASTER] ?: defaults.glyphMasterEnabled,
            glyphDialpadStrokes = this[Keys.GLYPH_DIALPAD_STROKES] ?: defaults.glyphDialpadStrokes,
            glyphIntensity = this[Keys.GLYPH_INTENSITY] ?: defaults.glyphIntensity,
            glyphIncomingShow = this[Keys.GLYPH_INCOMING_SHOW] ?: defaults.glyphIncomingShow,
            glyphRecordingIndicator = this[Keys.GLYPH_RECORDING_INDICATOR] ?: defaults.glyphRecordingIndicator,
            recordingEnabled = this[Keys.RECORDING_ENABLED] ?: defaults.recordingEnabled,
            recordingNoAnnouncement = this[Keys.RECORDING_NO_ANNOUNCEMENT] ?: defaults.recordingNoAnnouncement,
            retentionWindow = decodeEnum(this[Keys.RETENTION_WINDOW], defaults.retentionWindow),
            transcriptionEngine = decodeEnum(this[Keys.TRANSCRIPTION_ENGINE], defaults.transcriptionEngine),
            liveCaptionsEnabled = this[Keys.LIVE_CAPTIONS] ?: defaults.liveCaptionsEnabled,
            autoTranscribe = this[Keys.AUTO_TRANSCRIBE] ?: defaults.autoTranscribe,
            transcriptionLanguage = this[Keys.TRANSCRIPTION_LANGUAGE],
            callerIdSpamEnabled = this[Keys.CALLER_ID_SPAM] ?: defaults.callerIdSpamEnabled,
            incomingCallVibrationEnabled = this[Keys.INCOMING_CALL_VIBRATION] ?: defaults.incomingCallVibrationEnabled,
            callEndVibrationEnabled = this[Keys.CALL_END_VIBRATION] ?: defaults.callEndVibrationEnabled,
            softDialVibrationEnabled = this[Keys.SOFT_DIAL_VIBRATION] ?: defaults.softDialVibrationEnabled,
            backTapCallControlEnabled = this[Keys.BACK_TAP_CALL_CONTROL] ?: defaults.backTapCallControlEnabled,
            defaultSimSubscriptionId = this[Keys.DEFAULT_SIM_SUB_ID],
            contactsAccountFilter = this[Keys.CONTACTS_ACCOUNT_FILTER],
        )
    }

    private fun MutablePreferences.writeFrom(p: UserPreferences) {
        this[Keys.APP_PLAN] = p.appPlan.name
        this[Keys.THEME_MODE] = p.themeMode.name
        this[Keys.APP_FONT] = p.appFont.name
        this[Keys.ACCENT_COLOR] = p.accentColor.name
        this[Keys.GLYPH_MASTER] = p.glyphMasterEnabled
        this[Keys.GLYPH_DIALPAD_STROKES] = p.glyphDialpadStrokes
        this[Keys.GLYPH_INTENSITY] = p.glyphIntensity
        this[Keys.GLYPH_INCOMING_SHOW] = p.glyphIncomingShow
        this[Keys.GLYPH_RECORDING_INDICATOR] = p.glyphRecordingIndicator
        this[Keys.RECORDING_ENABLED] = p.recordingEnabled
        this[Keys.RECORDING_NO_ANNOUNCEMENT] = p.recordingNoAnnouncement
        this[Keys.RETENTION_WINDOW] = p.retentionWindow.name
        this[Keys.TRANSCRIPTION_ENGINE] = p.transcriptionEngine.name
        this[Keys.LIVE_CAPTIONS] = p.liveCaptionsEnabled
        this[Keys.AUTO_TRANSCRIBE] = p.autoTranscribe
        setOrRemove(Keys.TRANSCRIPTION_LANGUAGE, p.transcriptionLanguage)
        this[Keys.CALLER_ID_SPAM] = p.callerIdSpamEnabled
        this[Keys.INCOMING_CALL_VIBRATION] = p.incomingCallVibrationEnabled
        this[Keys.CALL_END_VIBRATION] = p.callEndVibrationEnabled
        this[Keys.SOFT_DIAL_VIBRATION] = p.softDialVibrationEnabled
        this[Keys.BACK_TAP_CALL_CONTROL] = p.backTapCallControlEnabled
        setOrRemove(Keys.DEFAULT_SIM_SUB_ID, p.defaultSimSubscriptionId)
        setOrRemove(Keys.CONTACTS_ACCOUNT_FILTER, p.contactsAccountFilter)
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun MutablePreferences.setOrRemove(key: Preferences.Key<Int>, value: Int?) {
        if (value == null) remove(key) else this[key] = value
    }

    private inline fun <reified E : Enum<E>> decodeEnum(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    /** Keys for every persisted preference (BUILD_SPEC §19). */
    private object Keys {
        val APP_PLAN = stringPreferencesKey("app_plan")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_FONT = stringPreferencesKey("app_font")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val GLYPH_MASTER = booleanPreferencesKey("glyph_master_enabled")
        val GLYPH_DIALPAD_STROKES = booleanPreferencesKey("glyph_dialpad_strokes")
        val GLYPH_INTENSITY = floatPreferencesKey("glyph_intensity")
        val GLYPH_INCOMING_SHOW = booleanPreferencesKey("glyph_incoming_show")
        val GLYPH_RECORDING_INDICATOR = booleanPreferencesKey("glyph_recording_indicator")
        val RECORDING_ENABLED = booleanPreferencesKey("recording_enabled")
        val RECORDING_NO_ANNOUNCEMENT = booleanPreferencesKey("recording_no_announcement")
        val RETENTION_WINDOW = stringPreferencesKey("retention_window")
        val TRANSCRIPTION_ENGINE = stringPreferencesKey("transcription_engine")
        val LIVE_CAPTIONS = booleanPreferencesKey("live_captions_enabled")
        val AUTO_TRANSCRIBE = booleanPreferencesKey("auto_transcribe")
        val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")
        val CALLER_ID_SPAM = booleanPreferencesKey("caller_id_spam_enabled")
        val INCOMING_CALL_VIBRATION = booleanPreferencesKey("incoming_call_vibration_enabled")
        val CALL_END_VIBRATION = booleanPreferencesKey("call_end_vibration_enabled")
        val SOFT_DIAL_VIBRATION = booleanPreferencesKey("soft_dial_vibration_enabled")
        val BACK_TAP_CALL_CONTROL = booleanPreferencesKey("back_tap_call_control_enabled")
        val DEFAULT_SIM_SUB_ID = intPreferencesKey("default_sim_subscription_id")
        val CONTACTS_ACCOUNT_FILTER = stringPreferencesKey("contacts_account_filter")
    }
}

/** Alias to keep the mapping helpers readable. */
private typealias MutablePreferences = androidx.datastore.preferences.core.MutablePreferences
