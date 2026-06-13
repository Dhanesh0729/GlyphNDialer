// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.db

import androidx.room.TypeConverter
import com.glyphdialer.core.domain.model.RecordingTier
import com.glyphdialer.core.domain.model.TranscriptionEngineType

/**
 * Room [TypeConverter]s for the domain enums persisted by the entities. Enums are
 * stored by stable [Enum.name] (NOT ordinal) so reordering the enum never corrupts
 * existing rows. Unknown/legacy names degrade gracefully to a conservative default.
 */
class Converters {

    @TypeConverter
    fun recordingTierToString(tier: RecordingTier): String = tier.name

    @TypeConverter
    fun stringToRecordingTier(value: String): RecordingTier =
        runCatching { RecordingTier.valueOf(value) }.getOrDefault(RecordingTier.UNAVAILABLE)

    @TypeConverter
    fun engineToString(engine: TranscriptionEngineType): String = engine.name

    @TypeConverter
    fun stringToEngine(value: String): TranscriptionEngineType =
        runCatching { TranscriptionEngineType.valueOf(value) }
            .getOrDefault(TranscriptionEngineType.ON_DEVICE_WHISPER)
}
