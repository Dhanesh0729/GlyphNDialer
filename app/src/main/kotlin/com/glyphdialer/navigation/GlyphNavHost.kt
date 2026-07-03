// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.glyphdialer.feature.calllog.navigation.CallLogRoute
import com.glyphdialer.feature.calllog.navigation.callLogGraph
import com.glyphdialer.feature.contacts.navigation.ContactsRoutes
import com.glyphdialer.feature.contacts.navigation.contactsGraph
import com.glyphdialer.feature.contacts.navigation.navigateToContactEdit
import com.glyphdialer.feature.dialpad.navigation.DialpadRoute
import com.glyphdialer.feature.dialpad.navigation.dialpadGraph
import com.glyphdialer.feature.dialpad.navigation.navigateToDialpad
import com.glyphdialer.feature.incall.navigation.InCallRoute
import com.glyphdialer.feature.incall.navigation.inCallGraph
import com.glyphdialer.feature.incall.navigation.navigateToInCall
import com.glyphdialer.feature.settings.navigation.SettingsRoutes
import com.glyphdialer.feature.settings.navigation.settingsGraph
import com.glyphdialer.feature.voicemail.navigation.navigateToVoicemail
import com.glyphdialer.feature.voicemail.navigation.voicemailGraph

/**
 * The top-level navigation destinations shown in the bottom bar (CONVENTIONS.md §7;
 * BUILD_SPEC §8) — Recents / Contacts / Dialpad, with Dialpad as the primary tab.
 */
private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    RECENTS(CallLogRoute.ROUTE, "Recents", Icons.Filled.History),
    CONTACTS(ContactsRoutes.LIST, "Contacts", Icons.Filled.Person),
    DIALPAD(DialpadRoute.ROUTE, "Dialpad", Icons.Filled.Dialpad),
}

/**
 * Root composable assembled in `:app` (CONVENTIONS.md §7). A [Scaffold] with a bottom
 * [NavigationBar] across the three top-level destinations and a [NavHost] that calls
 * every feature's graph extension. Cross-feature navigation is wired here via lambdas
 * (features never depend on each other — §3):
 *  - dialpad "add contact" → contacts edit (prefilled number),
 *  - dialpad speed-dial assignment → contacts list picker,
 *  - call-log row → contact detail,
 *  - settings is reached from the dialpad/overflow (host-routed),
 *  - in-call is launched full-screen by `:telecom` and routed to [InCallRoute].
 *
 * Side effects that leave Compose's world (placing a call, opening the platform
 * insert-contact sheet, SMS, OSS-licenses) are delegated UP to the platform actions in
 * [actions], so feature modules stay framework-light and isolated.
 *
 * @param actions host-owned platform actions (dial / add-contact / message / etc.),
 *   supplied by [com.glyphdialer.MainActivity].
 * @param navController the single nav controller; defaulted for previews/tests.
 */
@Composable
fun GlyphApp(
    actions: GlyphAppActions,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Hide the bottom bar on full-screen / modal destinations (in-call, voicemail,
    // settings, contact detail/edit) — only the three top-level tabs show it.
    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        TopLevelDestination.entries.any { it.route == dest.route }
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                GlyphBottomBar(
                    currentDestination = currentDestination?.route,
                    onSelect = { destination ->
                        navController.navigateToTopLevel(destination.route)
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // Dialpad is the primary/start destination (BUILD_SPEC §8).
            startDestination = DialpadRoute.ROUTE,
            modifier = Modifier.padding(innerPadding),
        ) {
            // -------------------- Dialpad (primary) --------------------
            dialpadGraph(
                onNavigateToAddContact = { number ->
                    // Add-to-contacts from the dialpad opens the contacts edit screen,
                    // prefilled. We hand the number to the host so it can use the
                    // platform insert flow; in-app we route to the edit destination.
                    actions.addContact(number)
                },
                onNavigateToSpeedDialAssignment = { _ ->
                    // Pick a contact to assign to the speed-dial slot. The picked number
                    // is fed back to the dialpad ViewModel by the contacts feature; here
                    // we just surface the contacts list as the picker.
                    navController.navigate(ContactsRoutes.LIST)
                },
                onNavigateToSettings = {
                    navController.openSettings()
                },
            )

            // -------------------- Recents / Call log --------------------
            callLogGraph(
                onNavigateToDetails = { number ->
                    // A call-log row resolves to a contact (or an unknown-number detail).
                    // The contacts feature keys details by lookup key; for a raw number we
                    // route via the platform dial/detail action owned by the host.
                    actions.openNumberDetails(number)
                },
            )

            // -------------------- Contacts (list + detail + edit) --------------------
            contactsGraph(
                navController = navController,
                onDial = actions.dial,
                onMessage = actions.message,
                onNavigateUp = { navController.popBackStack() },
            )

            // -------------------- Voicemail --------------------
            voicemailGraph(
                onNavigateUp = { navController.popBackStack() },
                onDial = actions.dial,
            )

            // -------------------- Settings (root + blocked + about) --------------------
            settingsGraph(
                navController = navController,
                onNavigateUp = { navController.popBackStack() },
                onOpenSpeedDial = { navController.navigate(ContactsRoutes.LIST) },
            )

            // -------------------- In-call (full-screen) --------------------
            inCallGraph(
                onNavigateToDialpad = {
                    // "Add call" during an active call: open the dialpad in add-second-call
                    // mode (it stays on top of the in-call surface).
                    navController.navigateToDialpad()
                },
                onCallEnded = {
                    // Pop the full-screen in-call destination when no live calls remain.
                    navController.popBackStack(route = InCallRoute, inclusive = true)
                },
            )
        }
    }
}

@Composable
private fun GlyphBottomBar(
    currentDestination: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
                alwaysShowLabel = false,
            )
        }
    }
}

/**
 * Navigate to a top-level tab with the standard single-top + save/restore-state
 * behavior so re-selecting a tab does not stack duplicates and inner state survives.
 */
private fun NavController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Convenience wrappers exposed for the host (e.g. launching in-call / voicemail from a
 * notification or intent) so [com.glyphdialer.MainActivity] doesn't reach into route
 * constants directly.
 */
fun NavController.openInCall() = navigateToInCall()

fun NavController.openVoicemail() = navigateToVoicemail()

fun NavController.openSettings() = navigate(SettingsRoutes.ROOT)

/**
 * In-app fallback for creating a contact. The number prefill is the platform
 * insert-contact flow's job (see [GlyphAppActions.addContact], the primary path); this
 * opens the in-app create form when that flow is unavailable.
 */
fun NavController.openContactCreate() = navigateToContactEdit(lookupKey = null)
