// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.dialpad.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.glyphdialer.feature.dialpad.DialpadRouteScreen

/**
 * Navigation entry points for the dialpad feature (CONVENTIONS.md §7). The `:app`
 * NavHost assembles features by calling [dialpadGraph]; cross-feature navigation uses
 * [NavController.navigateToDialpad] + the [DialpadRoute.ROUTE] constant. The feature
 * never references other features directly.
 */
object DialpadRoute {
    /** The stable route string for the dialpad destination. */
    const val ROUTE: String = "dialpad"
}

/**
 * Register the dialpad destination in a [NavGraphBuilder].
 *
 * @param onNavigateToAddContact host handler that opens the platform insert-contact
 *   flow prefilled with the entered number (the feature emits the number; `:app` owns
 *   the Intent so this module stays framework-light and feature-isolated).
 * @param onNavigateToSpeedDialAssignment host handler that opens a contact picker to
 *   assign the given speed-dial slot; the picked number is fed back to the ViewModel.
 */
fun NavGraphBuilder.dialpadGraph(
    onNavigateToAddContact: (number: String) -> Unit = {},
    onNavigateToSpeedDialAssignment: (slot: Int) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    composable(route = DialpadRoute.ROUTE) {
        DialpadRouteScreen(
            onNavigateToAddContact = onNavigateToAddContact,
            onAssignSpeedDial = onNavigateToSpeedDialAssignment,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * Navigate to the dialpad as a top-level destination (BUILD_SPEC §7 — Dialpad is the
 * primary/FAB destination). [navOptions] lets the host control single-top / pop
 * behavior from the bottom navigation.
 */
fun NavController.navigateToDialpad(navOptions: NavOptions? = null) {
    navigate(route = DialpadRoute.ROUTE, navOptions = navOptions)
}
