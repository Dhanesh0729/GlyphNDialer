// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.glyphdialer.feature.contacts.detail.ContactDetailRoute
import com.glyphdialer.feature.contacts.edit.ContactEditRoute
import com.glyphdialer.feature.contacts.list.ContactsRoute

/**
 * Navigation surface for :feature:contacts (CONVENTIONS.md §7).
 *
 * The feature exposes the top-level route constants ([ContactsRoutes]), the screen
 * entry composables ([ContactsRoute] / [ContactDetailRoute] / [ContactEditRoute]),
 * and a single graph extension ([contactsGraph]) that the :app NavHost assembles.
 *
 * Cross-feature actions the feature is NOT allowed to perform directly (placing calls
 * via :telecom, composing SMS) are forwarded UP to the host through the [onDial] /
 * [onMessage] callbacks, keeping the module within its permitted dependency set (§3).
 *
 * The contact lookup key is passed as a query argument. It is URL-encoded on the way in
 * and decoded by the destination's ViewModel (ContactsContract lookup keys can contain
 * URL-reserved characters such as '/').
 */
object ContactsRoutes {
    /** The top-level Contacts list destination (a bottom-nav tab). */
    const val LIST: String = "contacts"

    /** Navigation argument carrying the stable contact lookup key. */
    const val ARG_LOOKUP_KEY: String = "lookupKey"

    private const val DETAIL_BASE = "contacts/detail"
    private const val EDIT_BASE = "contacts/edit"

    /** Route pattern for the contact-detail destination. */
    const val DETAIL: String = "$DETAIL_BASE?$ARG_LOOKUP_KEY={$ARG_LOOKUP_KEY}"

    /**
     * Route pattern for the create/edit destination. The optional [ARG_LOOKUP_KEY]
     * query parameter selects edit mode; its absence means "create".
     */
    const val EDIT: String = "$EDIT_BASE?$ARG_LOOKUP_KEY={$ARG_LOOKUP_KEY}"

    /** Build a concrete detail route for [lookupKey]. */
    fun detailOf(lookupKey: String): String = "$DETAIL_BASE?$ARG_LOOKUP_KEY=${Uri.encode(lookupKey)}"

    /** Build a concrete edit route; pass null to create a new contact. */
    fun editOf(lookupKey: String? = null): String =
        if (lookupKey == null) EDIT_BASE else "$EDIT_BASE?$ARG_LOOKUP_KEY=${Uri.encode(lookupKey)}"
}

/** Type-safe navigation to the Contacts list (top-level tab). */
fun NavController.navigateToContacts(navOptions: NavOptions? = null) {
    navigate(ContactsRoutes.LIST, navOptions)
}

/** Type-safe navigation to a contact's detail. */
fun NavController.navigateToContactDetail(lookupKey: String, navOptions: NavOptions? = null) {
    navigate(ContactsRoutes.detailOf(lookupKey), navOptions)
}

/** Type-safe navigation to the create/edit destination (null [lookupKey] = create). */
fun NavController.navigateToContactEdit(lookupKey: String? = null, navOptions: NavOptions? = null) {
    navigate(ContactsRoutes.editOf(lookupKey), navOptions)
}

/**
 * Register the Contacts list + detail + edit destinations in the host NavGraph
 * (BUILD_SPEC §8 — "Navigation routes + graph ext (contacts list + detail + edit)").
 *
 * @param navController the host controller, used for intra-feature navigation
 *   (list → detail → edit) so :app doesn't have to thread every hop.
 * @param onDial host-provided dialer (TelecomManager / ACTION_CALL).
 * @param onVideoDial host-provided in-app WebRTC video caller.
 * @param onMessage host-provided SMS composer.
 * @param onNavigateUp pop the current destination.
 */
fun NavGraphBuilder.contactsGraph(
    navController: NavController,
    onDial: (String) -> Unit,
    onVideoDial: (String) -> Unit,
    onMessage: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    composable(route = ContactsRoutes.LIST) {
        ContactsRoute(
            onOpenContact = { lookupKey -> navController.navigateToContactDetail(lookupKey) },
            onCreateContact = { navController.navigateToContactEdit(null) },
            onDial = onDial,
        )
    }

    composable(
        route = ContactsRoutes.DETAIL,
        arguments = listOf(
            navArgument(ContactsRoutes.ARG_LOOKUP_KEY) {
                type = NavType.StringType
                nullable = false
            },
        ),
    ) {
        ContactDetailRoute(
            onNavigateUp = onNavigateUp,
            onEdit = { lookupKey -> navController.navigateToContactEdit(lookupKey) },
            onDial = onDial,
            onVideoDial = onVideoDial,
            onMessage = onMessage,
        )
    }

    composable(
        route = ContactsRoutes.EDIT,
        arguments = listOf(
            navArgument(ContactsRoutes.ARG_LOOKUP_KEY) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        ContactEditRoute(
            onNavigateUp = onNavigateUp,
            onSaved = { lookupKey ->
                // Pop the edit screen and land on the (created/updated) detail.
                navController.popBackStack()
                navController.navigateToContactDetail(lookupKey)
            },
        )
    }
}
