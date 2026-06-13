// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.glyphdialer.feature.settings.SettingsRoute
import com.glyphdialer.feature.settings.about.AboutRoute
import com.glyphdialer.feature.settings.blocked.BlockedNumbersRoute

/**
 * Navigation surface for :feature:settings (CONVENTIONS.md §7; BUILD_SPEC §21).
 *
 * The feature owns three destinations — the settings root, the blocked-numbers
 * sub-screen, and the about/legal sub-screen — registered together by [settingsGraph].
 * Internal navigation (root → blocked / root → about) is handled inside the graph via
 * the supplied [NavController]. Actions that LEAVE the feature (speed-dial assignment,
 * recordings storage/export, the platform open-source-licenses screen) are delegated
 * to the :app host through callbacks, so the feature stays within its allowed
 * dependency set (§3) and never imports another feature, :telecom, or :peripheral:*.
 */

/** Top-level route constants exposed for the :app NavHost (CONVENTIONS.md §7). */
object SettingsRoutes {
    /** The settings root / grouped list. */
    const val ROOT: String = "settings"

    /** The blocked-numbers management sub-screen. */
    const val BLOCKED_NUMBERS: String = "settings/blocked_numbers"

    /** The about / legal / attribution sub-screen. */
    const val ABOUT: String = "settings/about"
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
 * @param onOpenStorageExport host-provided recordings storage/export surface.
 * @param onOpenOpenSourceLicenses host-provided platform open-source-licenses screen
 *   (e.g. `OssLicensesMenuActivity`), launched as an Intent by the host.
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onNavigateUp: () -> Unit,
    onOpenSpeedDial: () -> Unit,
    onOpenStorageExport: () -> Unit,
    onOpenOpenSourceLicenses: () -> Unit,
) {
    composable(route = SettingsRoutes.ROOT) {
        SettingsRoute(
            onNavigateUp = onNavigateUp,
            onOpenBlockedNumbers = { navController.navigate(SettingsRoutes.BLOCKED_NUMBERS) },
            onOpenAbout = { navController.navigate(SettingsRoutes.ABOUT) },
            onOpenSpeedDial = onOpenSpeedDial,
            onOpenStorageExport = onOpenStorageExport,
            onOpenOpenSourceLicenses = onOpenOpenSourceLicenses,
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
            onOpenOpenSourceLicenses = onOpenOpenSourceLicenses,
        )
    }
}
