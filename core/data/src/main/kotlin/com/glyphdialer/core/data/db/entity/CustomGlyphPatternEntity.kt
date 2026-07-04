// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.glyphdialer.core.domain.model.CustomGlyphPattern
import com.glyphdialer.core.domain.model.CustomGlyphFrame
import com.glyphdialer.core.domain.model.GlyphFrameSound
import com.glyphdialer.core.domain.model.GlyphSoundStyle
import com.glyphdialer.core.domain.model.CustomGlyphZone

@Entity(tableName = "custom_glyph_pattern")
data class CustomGlyphPatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    val soundStyle: GlyphSoundStyle,
    val repeatCount: Int,
)

@Entity(tableName = "custom_glyph_frame")
data class CustomGlyphFrameEntity(
    @PrimaryKey(autoGenerate = true) val frameId: Long = 0,
    val patternId: String,
    val indexInSequence: Int,
    val intensity: Float,
    val durationMs: Int,
    val zones: List<CustomGlyphZone>,
    val soundCue: GlyphFrameSound,
)

data class CustomGlyphPatternWithFrames(
    @androidx.room.Embedded val pattern: CustomGlyphPatternEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "patternId"
    )
    val frames: List<CustomGlyphFrameEntity>
) {
    fun toDomain(): CustomGlyphPattern = CustomGlyphPattern(
        id = pattern.id,
        name = pattern.name,
        frames = frames.sortedBy { it.indexInSequence }.map {
            CustomGlyphFrame(
                zones = it.zones,
                intensity = it.intensity,
                durationMs = it.durationMs,
                soundCue = it.soundCue,
            )
        },
        soundStyle = pattern.soundStyle,
        repeatCount = pattern.repeatCount,
    )
}
