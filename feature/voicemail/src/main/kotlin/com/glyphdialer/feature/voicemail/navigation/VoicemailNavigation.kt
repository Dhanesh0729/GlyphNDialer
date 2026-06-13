// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.voicemail.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.glyphdialer.feature.voicemail.VoicemailRoute

/**
 * Navigation surface for :feature:voicemail (CONVENTIONS.md §7).
 *
 * The feature exposes a top-level route constant ([VOICEMAIL_ROUTE]), the screen
 * entry composable [VoicemailRoute], and a graph extension ([voicemailGraph]) that
 * the :app NavHost assembles. Cross-feature actions (call-back, dialing the carrier
 * voicemail) are NOT handled here — the graph forwards them to the host via [onDial],
 * keeping the feature within its allowed dependency set (no :telecom dependency).
 */

/** Top-level navigation route string for the voicemail destination. */
const val VOICEMAIL_ROUTE: String = "voicemail"

/** Type-safe navigation to the voicemail destination. */
fun NavController.navigateToVoicemail(navOptions: NavOptions? = null) {
    navigate(VOICEMAIL_ROUTE, navOptions)
}

/**
 * Register the voicemail destination in the host NavGraph.
 *
 * @param onNavigateUp pop back from voicemail.
 * @param onDial host-provided dialer (TelecomManager / ACTION_CALL). Receives the
 *   number for both "call back" and the carrier-voicemail fallback shortcut.
 */
fun NavGraphBuilder.voicemailGraph(
    onNavigateUp: () -> Unit,
    onDial: (String) -> Unit,
) {
    composable(route = VOICEMAIL_ROUTE) {
        VoicemailRoute(
            onNavigateUp = onNavigateUp,
            onDial = onDial,
        )
    }
}
