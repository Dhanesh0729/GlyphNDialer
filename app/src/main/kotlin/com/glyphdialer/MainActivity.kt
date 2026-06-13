// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.usecase.ObservePreferencesUseCase
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.navigation.GlyphApp
import com.glyphdialer.navigation.GlyphAppActions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/**
 * The single Activity (CONVENTIONS.md §7 — single-Activity + Compose NavHost).
 *
 * - Collects [UserPreferences] from the injected [ObservePreferencesUseCase] and feeds
 *   theme/font/accent/Glyph-intensity into [GlyphTheme].
 * - On first run, requests the default-dialer role via [RoleManager.ROLE_DIALER]
 *   (BUILD_SPEC §7.1) using a [registerForActivityResult] launcher.
 * - Owns the host platform actions ([GlyphAppActions]) — dialing, the platform
 *   insert-contact flow, SMS — that feature graphs invoke through lambdas, keeping
 *   features framework-light (§3).
 *
 * Runtime permission requests (Accompanist + the design-system RationaleSheet) are
 * driven inside each feature where the permission is actually needed (e.g. dialpad →
 * CALL_PHONE, contacts → READ/WRITE_CONTACTS), so the ask is contextual; the role
 * request below is the one app-wide prompt that gates the rest.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var observePreferences: ObservePreferencesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hot StateFlow of preferences so the theme recomposes on settings changes.
        val preferencesFlow = observePreferences().stateIn(
            scope = lifecycleScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences(),
        )

        val actions = buildAppActions()

        setContent {
            val prefs by preferencesFlow.collectAsStateWithLifecycle()

            // Request the default-dialer role once, the first time the UI is shown and
            // we don't already hold it. The launcher result simply logs the outcome —
            // the app remains fully usable as a non-default dialer (DIAL intents work),
            // it just can't write the call log / bind the InCallService until granted.
            val roleLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val granted = result.resultCode == RESULT_OK
                Timber.i("Default-dialer role request result: granted=%b", granted)
            }

            LaunchedEffect(Unit) {
                requestDefaultDialerRole { intent -> intent?.let(roleLauncher::launch) }
            }

            GlyphTheme(
                themeMode = prefs.themeMode,
                appFont = prefs.appFont,
                accent = prefs.accentColor,
                glyphIntensity = prefs.glyphIntensity,
            ) {
                GlyphApp(actions = actions)
            }
        }
    }

    // ----------------------------------------------------------------------------
    // Default-dialer role (BUILD_SPEC §7.1)
    // ----------------------------------------------------------------------------

    /**
     * Build the [RoleManager.ROLE_DIALER] request intent and hand it to [launch] when
     * the role is available and not already held. No-ops below API 29 (handled by
     * minSdk) or when the role is unavailable on the device. Never throws.
     */
    private fun requestDefaultDialerRole(launch: (Intent?) -> Unit) {
        val roleManager = getSystemService<RoleManager>()
        if (roleManager == null) {
            Timber.w("RoleManager unavailable; cannot request default-dialer role")
            return
        }
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            Timber.i("ROLE_DIALER not available on this device")
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            Timber.d("Already the default dialer")
            return
        }
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        launch(intent)
    }

    // ----------------------------------------------------------------------------
    // Host platform actions (owned by :app, invoked from feature graphs)
    // ----------------------------------------------------------------------------

    private fun buildAppActions(): GlyphAppActions = GlyphAppActions(
        dial = ::placeCall,
        message = ::composeSms,
        addContact = ::insertContact,
        openNumberDetails = ::placeCall, // tap-to-call-back from a Recents row.
        openStorageExport = ::openStorageSettings,
        openOpenSourceLicenses = ::openOpenSourceLicenses,
    )

    /**
     * Place a call. Prefer [TelecomManager.placeCall] (works as the default dialer and
     * routes through our InCallService); fall back to [Intent.ACTION_DIAL] which always
     * works without CALL_PHONE. CALL_PHONE is requested contextually by the dialpad.
     */
    private fun placeCall(number: String) {
        val uri = Uri.fromParts("tel", number, null)
        val telecom = getSystemService<TelecomManager>()
        val canPlace = telecom != null &&
            checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (canPlace) {
            runCatching { telecom!!.placeCall(uri, Bundle()) }
                .onFailure { Timber.w(it, "placeCall failed; falling back to ACTION_DIAL") }
                .onSuccess { return }
        }
        startActivitySafely(Intent(Intent.ACTION_DIAL, uri), "dial")
    }

    private fun composeSms(number: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        startActivitySafely(intent, "sms")
    }

    /** Open the platform insert-contact sheet, prefilled with [number] (§8). */
    private fun insertContact(number: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        startActivitySafely(intent, "insert-contact")
    }

    private fun openStorageSettings() {
        // App's storage settings; a real recordings export surface is a follow-up.
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivitySafely(intent, "storage-export")
    }

    private fun openOpenSourceLicenses() {
        // TODO: wire OssLicensesMenuActivity (play-services-oss-licenses) once added to
        // the catalog. For now, surface the about page is handled in-app by the
        // settings feature; this is a no-op host hook.
        Timber.i("Open-source licenses screen not yet wired (see TODO)")
    }

    private fun startActivitySafely(intent: Intent, what: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No activity to handle %s intent", what)
        }
    }
}
