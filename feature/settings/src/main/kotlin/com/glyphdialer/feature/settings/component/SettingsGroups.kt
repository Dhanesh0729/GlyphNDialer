// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.domain.model.AccentColor
import com.glyphdialer.core.domain.model.AppFont
import com.glyphdialer.core.domain.model.AppPlan
import com.glyphdialer.core.domain.model.MonetizationCatalog
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.RetentionWindow
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.domain.model.TranscriptionEngineType
import com.glyphdialer.feature.settings.ContactsSyncStatus
import com.glyphdialer.feature.settings.LegalText
import com.glyphdialer.feature.settings.SettingsEvent
import com.glyphdialer.feature.settings.SettingsLabels
import com.glyphdialer.feature.settings.SettingsUiState
import java.text.DateFormat
import java.util.Date

/**
 * The seven settings groups (BUILD_SPEC §21) as stateless composables. Each takes the
 * immutable [SettingsUiState] and a single `onEvent` lambda, applying the §9 honesty
 * gating (hidden / disabled / read-only) per group. The screen assembles them in
 * order inside a scrollable container.
 */

// --- Appearance --------------------------------------------------------------------

@Composable
fun AppearanceGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Appearance")

        SettingsChipSelector(
            title = "Theme",
            options = ThemeMode.entries,
            selected = prefs.themeMode,
            onSelect = { onEvent(SettingsEvent.SetThemeMode(it)) },
            label = SettingsLabels::theme,
        )

        SettingsChipSelector(
            title = "Font",
            options = AppFont.entries,
            selected = prefs.appFont,
            onSelect = { onEvent(SettingsEvent.SetAppFont(it)) },
            label = SettingsLabels::font,
        )

        AccentPicker(
            selected = prefs.accentColor,
            onSelect = { onEvent(SettingsEvent.SetAccent(it)) },
        )
    }
}

/**
 * A swatch-based accent picker (the live preview is provided by the enclosing
 * [com.glyphdialer.core.designsystem.theme.GlyphTheme], which re-tints `primary` as
 * soon as the preference changes).
 */
@Composable
private fun AccentPicker(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm)) {
        Text(
            text = "Accent",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.padding(top = Dimens.spaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd)) {
            AccentColor.entries.forEach { accent ->
                val isSelected = accent == selected
                val swatch = Color(accent.argb)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(swatch, CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else Dimens.hairline,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(accent) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = SettingsLabels.accent(accent),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}


// --- Plan / billing ---------------------------------------------------------------

@Composable
fun PlanGroup(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    val plan = state.preferences.appPlan
    val basic = MonetizationCatalog.products.first { it.plan == AppPlan.BASIC }
    val pro = MonetizationCatalog.products.first { it.plan == AppPlan.PRO }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Plan")

        SettingsStatusRow(
            title = "Current plan",
            value = SettingsLabels.plan(plan),
            subtitle = when (plan) {
                AppPlan.FREE -> "Free keeps the dialer clean. Glyph customization unlocks with Basic."
                AppPlan.BASIC -> "Glyph incoming-call effects are unlocked."
                AppPlan.PRO -> "Full customization is unlocked."
            },
        )

        SettingsStatusRow(
            title = basic.displayName,
            value = "INR ${basic.targetPriceInr} one-time",
            subtitle = "Glyph flash, contact patterns, ringtone sync, effect library, previews",
        )

        SettingsStatusRow(
            title = pro.displayName,
            value = "INR ${pro.targetPriceInr} one-time",
            subtitle = "Premium themes, custom Glyph creator, presets, groups, profiles",
        )

        DisclaimerBlock(
            text = "Play Store builds must unlock these digital features through " +
                "Google Play Billing. Do not use external payment gateways inside the " +
                "app for Basic or Pro unlocks.",
        )
    }
}
@Composable
fun LockedGlyphGroup(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Glyph")
        SettingsStatusRow(
            title = "Glyph effects",
            value = "Basic",
            subtitle = "Unlock Basic to use incoming-call flash, per-contact patterns, ringtone sync, and effect previews.",
            accent = MaterialTheme.colorScheme.primary,
        )
        SettingsStatusRow(
            title = "Pro customization",
            value = "Pro",
            subtitle = "Custom pattern creator, preset sharing, contact groups, profiles, and charging effects stay locked until Pro.",
        )
    }
}
// --- Glyph (hidden entirely when glyphAvailable == false; §9/§17) ------------------

@Composable
fun GlyphGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    val master = prefs.glyphMasterEnabled
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Glyph")

        SettingsSwitchRow(
            title = "Glyph effects",
            subtitle = "Master switch for all Glyph lighting",
            checked = master,
            onCheckedChange = { onEvent(SettingsEvent.SetGlyphMasterEnabled(it)) },
        )

        // Sub-toggles are disabled (not hidden) when the master is off, so the user
        // can see what would be controlled.
        SettingsSwitchRow(
            title = "Dialpad strokes",
            subtitle = "A distinct light stroke per key",
            checked = prefs.glyphDialpadStrokes,
            enabled = master,
            onCheckedChange = { onEvent(SettingsEvent.SetGlyphDialpadStrokes(it)) },
        )

        SettingsSliderRow(
            title = "Intensity",
            value = prefs.glyphIntensity,
            enabled = master,
            onValueChange = { onEvent(SettingsEvent.SetGlyphIntensity(it)) },
        )

        SettingsSwitchRow(
            title = "Incoming-call show",
            subtitle = "Per-caller light choreography",
            checked = prefs.glyphIncomingShow,
            enabled = master,
            onCheckedChange = { onEvent(SettingsEvent.SetGlyphIncomingShow(it)) },
        )

        SettingsSwitchRow(
            title = "Recording indicator",
            subtitle = "Show a persistent pattern while recording",
            checked = prefs.glyphRecordingIndicator,
            enabled = master,
            onCheckedChange = { onEvent(SettingsEvent.SetGlyphRecordingIndicator(it)) },
        )
    }
}

// --- Calls -------------------------------------------------------------------------

@Composable
fun CallsGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Calls")

        SettingsNavigationRow(
            title = "Speed dial",
            subtitle = "Assign contacts to dialpad keys 2–9",
            onClick = { onEvent(SettingsEvent.OpenSpeedDial) },
        )

        SettingsSwitchRow(
            title = "Caller ID & spam",
            subtitle = "Identify and flag likely spam callers",
            checked = prefs.callerIdSpamEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetCallerIdSpam(it)) },
        )

        SettingsStatusRow(
            title = "Video calls",
            value = when {
                !state.capabilities.cameraAvailable -> "No camera"
                state.capabilities.voipAvailable -> "Ready"
                else -> "Setup"
            },
            subtitle = when {
                !state.capabilities.cameraAvailable -> "This device has no camera for app-to-app video."
                state.capabilities.voipAvailable -> "In-app WebRTC video calls are enabled."
                else -> "Configure a WebRTC signaling backend to enable app-to-app video."
            },
            accent = if (state.capabilities.voipAvailable && state.capabilities.cameraAvailable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        SettingsSwitchRow(
            title = "Incoming vibration",
            subtitle = "Nothing-style pulse when a call arrives",
            checked = prefs.incomingCallVibrationEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetIncomingCallVibration(it)) },
        )

        SettingsSwitchRow(
            title = "Hang-up vibration",
            subtitle = "Short confirmation when a call ends",
            checked = prefs.callEndVibrationEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetCallEndVibration(it)) },
        )

        SettingsSwitchRow(
            title = "Soft dial vibration",
            subtitle = "Subtle haptic tick for dialpad keys",
            checked = prefs.softDialVibrationEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetSoftDialVibration(it)) },
        )

        SettingsSwitchRow(
            title = "Triple back-tap answer/end",
            subtitle = "Experimental accelerometer gesture while a call is active",
            checked = prefs.backTapCallControlEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetBackTapCallControl(it)) },
        )

        SettingsNavigationRow(
            title = "Blocked numbers",
            subtitle = "Manage your block list",
            onClick = { onEvent(SettingsEvent.OpenBlockedNumbers) },
        )
    }
}

// --- Recording (tier read-only; no-announcement carries the §2.2 disclaimer) -------

@Composable
fun RecordingGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    val tier = state.activeRecordingTier
    val recordingPossible = state.recordingPossible
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Recording")

        SettingsSwitchRow(
            title = "Enable call recording",
            subtitle = if (recordingPossible) {
                "Record calls at the highest supported tier"
            } else {
                "Not supported on this device"
            },
            checked = prefs.recordingEnabled,
            // Always interactable so the user can express intent, but honesty messaging
            // fires from the ViewModel when no tier is available (§9).
            onCheckedChange = { onEvent(SettingsEvent.SetRecordingEnabled(it)) },
        )

        // §9/§12: the active tier is READ-ONLY and reflects what the device can actually
        // do — never an editable claim of two-way capture.
        SettingsStatusRow(
            title = "Active tier",
            value = SettingsLabels.tier(tier),
            subtitle = SettingsLabels.tierExplanation(tier),
            accent = if (tier.isTwoWay) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        SettingsSwitchRow(
            title = "Record without announcement",
            checked = prefs.recordingNoAnnouncement,
            enabled = prefs.recordingEnabled,
            onCheckedChange = { onEvent(SettingsEvent.SetNoAnnouncement(it)) },
        )
        // §2.2: the legal disclaimer is shown unconditionally next to the toggle.
        DisclaimerBlock(text = LegalText.NO_ANNOUNCEMENT_DISCLAIMER)

        SettingsChipSelector(
            title = "Auto-delete after",
            options = RetentionWindow.entries,
            selected = prefs.retentionWindow,
            onSelect = { onEvent(SettingsEvent.SetRetentionWindow(it)) },
            label = SettingsLabels::retention,
        )
    }
}

// --- Transcription -----------------------------------------------------------------

@Composable
fun TranscriptionGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    val engine = prefs.transcriptionEngine
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Transcription")

        SettingsChipSelector(
            title = "Engine",
            options = TranscriptionEngineType.entries,
            selected = engine,
            onSelect = { onEvent(SettingsEvent.SetTranscriptionEngine(it)) },
            label = SettingsLabels::engine,
        )
        if (SettingsLabels.engineIsCloud(engine)) {
            DisclaimerBlock(
                text = "Cloud transcription sends call audio to a server. It requires " +
                    "network access and your explicit consent, and is subject to the " +
                    "same call-audio limits as recording (§2.4).",
            )
        }

        val captionsEnabled = prefs.liveCaptionsEnabled
        SettingsSwitchRow(
            title = "Live captions",
            subtitle = if (state.liveCaptionsOfferable) {
                "Real-time ticker captions during calls"
            } else {
                "On-device speech recognition unavailable on this device"
            },
            checked = captionsEnabled,
            // Interactable (intent is saved) but the VM warns when speech isn't present.
            onCheckedChange = { onEvent(SettingsEvent.SetLiveCaptions(it)) },
        )

        SettingsSwitchRow(
            title = "Auto-transcribe recordings",
            subtitle = "Transcribe every new recording automatically",
            checked = prefs.autoTranscribe,
            onCheckedChange = { onEvent(SettingsEvent.SetAutoTranscribe(it)) },
        )

        SettingsChipSelector(
            title = "Language",
            options = SettingsLabels.LANGUAGE_OPTIONS.map { it.first },
            selected = prefs.transcriptionLanguage,
            onSelect = { onEvent(SettingsEvent.SetTranscriptionLanguage(it)) },
            label = { SettingsLabels.language(it) },
        )
    }
}

// --- Contacts ----------------------------------------------------------------------

@Composable
fun ContactsGroup(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.preferences
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("Contacts")

        SettingsNavigationRow(
            title = "Account filter",
            subtitle = "Show contacts from a single account",
            valueLabel = prefs.contactsAccountFilter ?: "All",
            onClick = {
                // Toggle the common Google-account filter on/off as a simple inline
                // control; a richer account chooser belongs to :feature:contacts.
                val next = if (prefs.contactsAccountFilter == null) "com.google" else null
                onEvent(SettingsEvent.SetContactsAccountFilter(next))
            },
        )

        SettingsStatusRow(
            title = "Sync status",
            value = syncStatusLabel(state.contactsSyncStatus),
        )
    }
}

private fun syncStatusLabel(status: ContactsSyncStatus): String = when (status) {
    ContactsSyncStatus.Unknown -> "—"
    ContactsSyncStatus.Syncing -> "Syncing…"
    is ContactsSyncStatus.Synced -> status.lastSyncMillis?.let { millis ->
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
    } ?: "Never"
}

// --- About / legal (link into the sub-screen) --------------------------------------

@Composable
fun AboutGroup(
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader("About")

        SettingsNavigationRow(
            title = "Open-source licenses",
            onClick = { onEvent(SettingsEvent.OpenOpenSourceLicenses) },
        )
        SettingsNavigationRow(
            title = "Legal & attribution",
            subtitle = "Recording law, fonts, Glyph SDK",
            onClick = { onEvent(SettingsEvent.OpenAbout) },
        )
    }
}
