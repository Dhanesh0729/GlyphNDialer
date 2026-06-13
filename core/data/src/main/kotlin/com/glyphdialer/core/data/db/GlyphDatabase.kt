// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.glyphdialer.core.data.db.dao.BlockedNumberDao
import com.glyphdialer.core.data.db.dao.CallNoteDao
import com.glyphdialer.core.data.db.dao.FavoriteDao
import com.glyphdialer.core.data.db.dao.RecordingDao
import com.glyphdialer.core.data.db.dao.SpeedDialDao
import com.glyphdialer.core.data.db.dao.TranscriptDao
import com.glyphdialer.core.data.db.entity.BlockedNumberEntity
import com.glyphdialer.core.data.db.entity.CallNoteEntity
import com.glyphdialer.core.data.db.entity.FavoriteEntity
import com.glyphdialer.core.data.db.entity.RecordingEntity
import com.glyphdialer.core.data.db.entity.SpeedDialEntity
import com.glyphdialer.core.data.db.entity.TranscriptEntity
import com.glyphdialer.core.data.db.entity.TranscriptSegmentEntity

/**
 * The app's Room database (BUILD_SPEC §19). Holds locally-owned data: recording &
 * transcript metadata, the blocklist cache, favorites, speed-dials, and call notes.
 * Contacts, the call log, and voicemail are NOT stored here — those live in the
 * platform content providers and are read live by the provider-backed repositories.
 *
 * Bump [version] and supply a migration when the schema changes; schemas are
 * exported to `schemas/` for diffing (see build.gradle.kts `room.schemaLocation`).
 */
@Database(
    entities = [
        RecordingEntity::class,
        TranscriptEntity::class,
        TranscriptSegmentEntity::class,
        BlockedNumberEntity::class,
        FavoriteEntity::class,
        CallNoteEntity::class,
        SpeedDialEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GlyphDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun callNoteDao(): CallNoteDao
    abstract fun speedDialDao(): SpeedDialDao
}
