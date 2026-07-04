// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.glyphdialer.feature.settings.SettingsRoute
import com.glyphdialer.feature.settings.BasicSettingsRoute
import com.glyphdialer.feature.settings.ProSettingsRoute
import com.glyphdialer.feature.settings.about.AboutRoute
import com.glyphdialer.feature.settings.blocked.BlockedNumbersRoute
import com.glyphdialer.feature.settings.licenses.LicensesRoute
import com.glyphdialer.feature.settings.composer.GlyphComposerRoute

/**
 * Navigation surface for :feature:settings (CONVENTIONS.md §7; BUILD_SPEC §21).
 */

/** Top-level route constants exposed for the :app NavHost (CONVENTIONS.md §7). */
object SettingsRoutes {
    /** The settings root / grouped list. */
    const val ROOT: String = "settings"

    /** The basic features sub-screen. */
    const val BASIC: String = "settings/basic"

    /** The pro features sub-screen. */
    const val PRO: String = "settings/pro"

    /** The blocked-numbers management sub-screen. */
    const val BLOCKED_NUMBERS: String = "settings/blocked_numbers"

    /** The about / legal / attribution sub-screen. */
    const val ABOUT: String = "settings/about"

    /** The open-source-licenses sub-screen. */
    const val LICENSES: String = "settings/licenses"

    /** The custom Glyph composer sub-screen. */
    const val COMPOSER = "settings/composer"
    const val SAVED_PATTERNS = "settings/saved_patterns"
    const val ASSIGN_PATTERN = "settings/assign_pattern/{patternId}"
    
    fun assignPattern(patternId: String) = "settings/assign_pattern/$patternId"
}

/** Type-safe navigation to the settings root. */
fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoutes.ROOT, navOptions)
}

/**
 * Register the settings destinations in the host NavGraph.
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
            onOpenBasic = { navController.navigate(SettingsRoutes.BASIC) },
            onOpenPro = { navController.navigate(SettingsRoutes.PRO) },
            onOpenBlockedNumbers = { navController.navigate(SettingsRoutes.BLOCKED_NUMBERS) },
            onOpenAbout = { navController.navigate(SettingsRoutes.ABOUT) },
            onOpenSpeedDial = onOpenSpeedDial,
            onOpenOpenSourceLicenses = openLicenses,
            onOpenGlyphComposer = { navController.navigate(SettingsRoutes.COMPOSER) },
        )
    }

    composable(route = SettingsRoutes.BASIC) {
        BasicSettingsRoute(
            onNavigateUp = { navController.popBackStack() },
        )
    }

    composable(route = SettingsRoutes.PRO) {
        ProSettingsRoute(
            onNavigateUp = { navController.popBackStack() },
            onOpenGlyphComposer = { navController.navigate(SettingsRoutes.SAVED_PATTERNS) }
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

    composable(route = SettingsRoutes.COMPOSER) {
        com.glyphdialer.feature.settings.composer.GlyphComposerRoute(
            onNavigateUp = { navController.popBackStack() }
        )
    }

    composable(route = SettingsRoutes.SAVED_PATTERNS) {
        com.glyphdialer.feature.settings.composer.SavedPatternsRoute(
            onNavigateUp = { navController.popBackStack() },
            onNavigateToComposer = { navController.navigate(SettingsRoutes.COMPOSER) },
            onNavigateToAssign = { patternId -> navController.navigate(SettingsRoutes.assignPattern(patternId)) }
        )
    }

    composable(
        route = SettingsRoutes.ASSIGN_PATTERN,
        arguments = listOf(androidx.navigation.navArgument("patternId") { type = androidx.navigation.NavType.StringType })
    ) {
        com.glyphdialer.feature.settings.composer.AssignPatternRoute(
            onNavigateUp = { navController.popBackStack() }
        )
    }
}
