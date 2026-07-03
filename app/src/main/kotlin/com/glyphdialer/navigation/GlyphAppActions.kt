// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.navigation

import androidx.compose.runtime.Immutable

/**
 * Host-owned platform actions that feature graphs invoke through lambdas, keeping the
 * features themselves framework-light and isolated (CONVENTIONS.md §3/§7). All of
 * these touch Android framework surfaces (TelecomManager, the contacts insert/edit
 * Intent, the SMS composer) that `:app` owns.
 *
 * Implemented by [com.glyphdialer.MainActivity] over its [android.content.Context].
 *
 * @property dial place a call to the given number (TelecomManager.placeCall / ACTION_CALL).
 * @property videoDial place an in-app WebRTC video call to the given number.
 * @property message open the SMS composer for the given number.
 * @property addContact open the platform insert-contact flow, prefilled with the number.
 * @property openNumberDetails open a call-back/details surface for a raw number (a
 *   call-log row tapped — resolves to the contact if known, else offers dial/add).
 */
@Immutable
data class GlyphAppActions(
    val dial: (number: String) -> Unit,
    val videoDial: (number: String) -> Unit,
    val message: (number: String) -> Unit,
    val addContact: (number: String) -> Unit,
    val openNumberDetails: (number: String) -> Unit,
)
