// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.calllog.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.glyphdialer.feature.calllog.CallLogScreenRoute

/**
 * Public navigation surface for the Recents / Call-log feature (CONVENTIONS.md §7
 * — "each feature exposes top-level route constants + an extension
 * `fun NavGraphBuilder.<feature>Graph(...)`; the `:app` host calls them").
 *
 * Features never depend on one another (CONVENTIONS.md §3): the host assembles
 * cross-feature navigation by passing in lambdas (here [onNavigateToDetails]) that
 * resolve to other features' routes inside `:app`.
 */
object CallLogRoute {

    /** The top-level Recents destination route (bottom-nav tab, CONVENTIONS.md §7). */
    const val ROUTE: String = "calllog"
}

/**
 * Register the Recents / Call-log destination on a [NavGraphBuilder].
 *
 * @param onNavigateToDetails invoked with a phone number when the user opens a
 *   call's detail/history view. The host wires this to the contacts/detail feature.
 */
fun NavGraphBuilder.callLogGraph(
    onNavigateToDetails: (number: String) -> Unit,
) {
    composable(route = CallLogRoute.ROUTE) {
        CallLogScreenRoute(onNavigateToDetails = onNavigateToDetails)
    }
}

/** Convenience navigation helper for the host (CONVENTIONS.md §7). */
fun NavController.navigateToCallLog(navOptions: NavOptions? = null) {
    navigate(CallLogRoute.ROUTE, navOptions)
}
