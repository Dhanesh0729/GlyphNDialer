// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom

import com.glyphdialer.core.common.Constants as CommonConstants

/**
 * Telecom-module-local constants. Logging tag delegates to the shared
 * [CommonConstants.APP_TAG] so all modules log under one tag (CONVENTIONS.md §12).
 */
internal object Constants {

    /** Shared Timber tag for the whole app. */
    const val TAG: String = CommonConstants.APP_TAG

    /**
     * PhoneAccount handle id for our self-managed VoIP ConnectionService (§7.4).
     * Stable: the OS persists the registered account against it.
     */
    const val SELF_MANAGED_ACCOUNT_ID: String = "glyph_dialer.voip"

    /** Extra key used to thread our session id through TelecomManager.placeCall extras. */
    const val EXTRA_SESSION_ID: String = "com.glyphdialer.telecom.EXTRA_SESSION_ID"

    /** Extra flag marking an outgoing request as the self-managed VoIP path. */
    const val EXTRA_IS_VOIP: String = "com.glyphdialer.telecom.EXTRA_IS_VOIP"

    /** Extra flag requesting a VoIP call to negotiate video from the first offer. */
    const val EXTRA_START_WITH_VIDEO: String = "com.glyphdialer.telecom.EXTRA_START_WITH_VIDEO"
}
