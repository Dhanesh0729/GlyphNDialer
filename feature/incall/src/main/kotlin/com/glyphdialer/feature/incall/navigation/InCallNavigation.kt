// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.glyphdialer.feature.incall.InCallScreen

/**
 * Navigation surface for the in-call feature (CONVENTIONS.md §7).
 *
 * The in-call destination is launched full-screen by `:telecom`'s InCallService and
 * routed here by the `:app` NavHost. It exposes only a route constant + a
 * [NavGraphBuilder] extension so `:app` can wire it without depending on internals,
 * and a [NavController.navigateToInCall] helper for the launch.
 */
const val InCallRoute: String = "incall"

/**
 * Register the full-screen in-call destination.
 *
 * @param onNavigateToDialpad invoked for "Add call" — the :app host routes to the
 *   dialpad in "add second call" mode (features never depend on each other — §3).
 * @param onCallEnded invoked when no live calls remain so the host can pop/finish the
 *   full-screen surface.
 */
fun NavGraphBuilder.inCallGraph(
    onNavigateToDialpad: () -> Unit,
    onCallEnded: () -> Unit,
) {
    composable(
        route = InCallRoute,
        deepLinks = listOf(
            navDeepLink {
                action = "com.glyphdialer.action.IN_CALL"
                uriPattern = "glyphdialer://incall"
            }
        )
    ) {
        InCallScreen(
            onNavigateToDialpad = onNavigateToDialpad,
            onCallEnded = onCallEnded,
        )
    }
}

/**
 * Navigate to the full-screen in-call destination, typically launched from the
 * InCallService / a full-screen notification intent. [builder] lets the caller set
 * flags (e.g. `launchSingleTop = true`).
 */
fun NavController.navigateToInCall(builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) {
    navigate(InCallRoute, builder)
}
