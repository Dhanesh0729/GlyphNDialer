// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.glyphdialer.feature.settings.SettingsRoute
import com.glyphdialer.feature.settings.about.AboutRoute
import com.glyphdialer.feature.settings.blocked.BlockedNumbersRoute
import com.glyphdialer.feature.settings.licenses.LicensesRoute

/**
 * Navigation surface for :feature:settings (CONVENTIONS.md §7; BUILD_SPEC §21).
 *
 * The feature owns four destinations — the settings root, the blocked-numbers
 * sub-screen, the about/legal sub-screen, and the open-source-licenses sub-screen —
 * registered together by [settingsGraph]. Internal navigation between them is handled
 * inside the graph via the supplied [NavController]. The only action that LEAVES the
 * feature is speed-dial assignment (which lives in :feature:contacts/:feature:dialpad
 * territory) — it is delegated to the :app host through a callback, so the feature
 * stays within its allowed dependency set (§3) and never imports another feature,
 * :telecom, or :peripheral:*.
 */

/** Top-level route constants exposed for the :app NavHost (CONVENTIONS.md §7). */
object SettingsRoutes {
    /** The settings root / grouped list. */
    const val ROOT: String = "settings"

    /** The blocked-numbers management sub-screen. */
    const val BLOCKED_NUMBERS: String = "settings/blocked_numbers"

    /** The about / legal / attribution sub-screen. */
    const val ABOUT: String = "settings/about"

    /** The open-source-licenses sub-screen. */
    const val LICENSES: String = "settings/licenses"
}

/** Type-safe navigation to the settings root. */
fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoutes.ROOT, navOptions)
}

/**
 * Register the settings destinations in the host NavGraph.
 *
 * @param navController the host controller, used for in-feature navigation between the
 *   root and its sub-screens.
 * @param onNavigateUp pop the current settings destination (typically [NavController.popBackStack]).
 * @param onOpenSpeedDial host-provided speed-dial assignment surface (lives outside this
 *   feature; the :app host routes to :feature:dialpad/:feature:contacts territory).
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onNavigateUp: () -> Unit,
    onOpenSpeedDial: () -> Unit,
) {
    // Open-source licenses is an in-feature destination (no external Play-Services
    // dependency): the root and the about screen both route to it internally.
    val openLicenses = { navController.navigate(SettingsRoutes.LICENSES); Unit }

    composable(route = SettingsRoutes.ROOT) {
        SettingsRoute(
            onNavigateUp = onNavigateUp,
            onOpenBlockedNumbers = { navController.navigate(SettingsRoutes.BLOCKED_NUMBERS) },
            onOpenAbout = { navController.navigate(SettingsRoutes.ABOUT) },
            onOpenSpeedDial = onOpenSpeedDial,
            onOpenOpenSourceLicenses = openLicenses,
        )
    }

    composable(route = SettingsRoutes.BLOCKED_NUMBERS) {
        BlockedNumbersRoute(
            onNavigateUp = { navController.popBackStack() },
        )
    }

    composable(route = SettingsRoutes.ABOUT) {
        AboutRoute(
            onNavigateUp = { navController.popBackStack() },
            onOpenOpenSourceLicenses = openLicenses,
        )
    }

    composable(route = SettingsRoutes.LICENSES) {
        LicensesRoute(
            onNavigateUp = { navController.popBackStack() },
        )
    }
}
