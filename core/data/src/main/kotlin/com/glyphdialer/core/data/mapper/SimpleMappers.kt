// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.mapper

import com.glyphdialer.core.data.db.entity.BlockedNumberEntity
import com.glyphdialer.core.data.db.entity.CallNoteEntity
import com.glyphdialer.core.data.db.entity.FavoriteEntity
import com.glyphdialer.core.data.db.entity.SpeedDialEntity
import com.glyphdialer.core.domain.model.BlockedNumber
import com.glyphdialer.core.domain.model.CallNote
import com.glyphdialer.core.domain.model.Favorite
import com.glyphdialer.core.domain.model.PhoneNumber
import com.glyphdialer.core.domain.model.SpeedDialSlot

// --- BlockedNumber ----------------------------------------------------------

fun BlockedNumberEntity.toDomain(): BlockedNumber = BlockedNumber(
    id = id,
    number = PhoneNumber(raw = rawNumber, normalized = normalizedNumber, formatted = rawNumber),
    createdAtMillis = createdAtMillis,
    reportedAsSpam = reportedAsSpam,
)

// --- Favorite ---------------------------------------------------------------

fun FavoriteEntity.toDomain(): Favorite = Favorite(
    contactLookupKey = contactLookupKey,
    position = position,
    defaultNumber = defaultNumber,
    displayName = displayName,
    photoUri = photoUri,
)

fun Favorite.toEntity(): FavoriteEntity = FavoriteEntity(
    contactLookupKey = contactLookupKey,
    position = position,
    defaultNumber = defaultNumber,
    displayName = displayName,
    photoUri = photoUri,
)

// --- SpeedDial --------------------------------------------------------------

fun SpeedDialEntity.toDomain(): SpeedDialSlot = SpeedDialSlot(
    slot = slot,
    contactLookupKey = contactLookupKey,
    number = number,
    displayName = displayName,
    photoUri = photoUri,
)

fun SpeedDialSlot.toEntity(): SpeedDialEntity = SpeedDialEntity(
    slot = slot,
    contactLookupKey = contactLookupKey,
    number = number,
    displayName = displayName,
    photoUri = photoUri,
)

// --- CallNote ---------------------------------------------------------------

fun CallNoteEntity.toDomain(): CallNote = CallNote(
    id = id,
    callId = callId,
    number = PhoneNumber(raw = rawNumber, normalized = normalizedNumber, formatted = rawNumber),
    text = text,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
