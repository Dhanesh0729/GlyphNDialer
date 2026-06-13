// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * A favorite contact pinned to the favorites grid (RoomEntity: FavoriteEntity).
 *
 * Keyed by [contactLookupKey] for sync stability. [position] is the user-defined
 * order in the grid; [defaultNumber] is the number to dial when tapped (a contact
 * may have several).
 */
data class Favorite(
    val contactLookupKey: String,
    val position: Int,
    val defaultNumber: String,
    // Denormalized snapshot for fast rendering before the contact is resolved.
    val displayName: String? = null,
    val photoUri: String? = null,
)

/**
 * A speed-dial assignment for dialpad long-press keys 2–9 (1 is reserved for
 * voicemail, 0 for "+"). RoomEntity: SpeedDialEntity.
 *
 * [slot] is in 2..9.
 */
data class SpeedDialSlot(
    val slot: Int,
    val contactLookupKey: String? = null,
    val number: String,
    val displayName: String? = null,
    val photoUri: String? = null,
) {
    init {
        require(slot in MIN_SLOT..MAX_SLOT) { "Speed-dial slot must be in $MIN_SLOT..$MAX_SLOT (was $slot)" }
    }

    companion object {
        const val MIN_SLOT = 2
        const val MAX_SLOT = 9
    }
}
