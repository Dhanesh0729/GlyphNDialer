// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * A contact aggregated from the platform [android.provider.ContactsContract].
 *
 * [lookupKey] is the stable, account-independent identifier Android guarantees
 * across syncs (preferred for navigation/favorites). [id] is the volatile
 * Contacts._ID, useful for direct URIs within a single session.
 */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val numbers: List<PhoneNumber> = emptyList(),
    /** content:// URI of the high-res photo, or null when none. */
    val photoUri: String? = null,
    /** content:// URI of the thumbnail, or null when none. */
    val thumbnailUri: String? = null,
    val isFavorite: Boolean = false,
    /** RawContacts.ACCOUNT_TYPE of the primary raw contact (e.g. "com.google"). */
    val accountType: String? = null,
    /** Whether the contact has been flagged "send to voicemail" in the OS. */
    val sendToVoicemail: Boolean = false,
) {
    /** The number flagged primary, else the first number, else null. */
    val primaryNumber: PhoneNumber?
        get() = numbers.firstOrNull { it.isPrimary } ?: numbers.firstOrNull()

    /** Uppercase first letter for the fast-scroll alphabet index ('#' for non-letters). */
    val sortIndexChar: Char
        get() = displayName.trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
}
