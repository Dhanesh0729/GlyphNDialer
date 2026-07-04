// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.data.db.GlyphDatabase
import com.glyphdialer.core.data.db.dao.BlockedNumberDao
import com.glyphdialer.core.data.db.dao.CallNoteDao
import com.glyphdialer.core.data.db.dao.FavoriteDao
import com.glyphdialer.core.data.db.dao.RecordingDao
import com.glyphdialer.core.data.db.dao.SpeedDialDao
import com.glyphdialer.core.data.db.dao.CustomGlyphPatternDao
import com.glyphdialer.core.data.db.dao.ContactGlyphPatternDao
import com.glyphdialer.core.data.db.dao.TranscriptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides the Room database, its DAOs, and the Preferences DataStore
 * (CONVENTIONS.md §5/§6, BUILD_SPEC §19). Binding of repository interfaces lives in
 * [RepositoryBindingsModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): GlyphDatabase = Room.databaseBuilder(
        context,
        GlyphDatabase::class.java,
        Constants.DATABASE_NAME,
    )
        // Schema is versioned; replace with explicit migrations before shipping
        // schema changes. fallbackToDestructiveMigration is acceptable pre-release
        // for locally-owned, regenerable data only.
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideRecordingDao(db: GlyphDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideTranscriptDao(db: GlyphDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    fun provideBlockedNumberDao(db: GlyphDatabase): BlockedNumberDao = db.blockedNumberDao()

    @Provides
    fun provideFavoriteDao(db: GlyphDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideCallNoteDao(db: GlyphDatabase): CallNoteDao = db.callNoteDao()

    @Provides
    fun provideSpeedDialDao(db: GlyphDatabase): SpeedDialDao = db.speedDialDao()

    @Provides
    fun provideCustomGlyphPatternDao(db: GlyphDatabase): CustomGlyphPatternDao = db.customGlyphPatternDao()

    @Provides
    fun provideContactGlyphPatternDao(db: GlyphDatabase): ContactGlyphPatternDao = db.contactGlyphPatternDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(GlyphDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(Constants.PREFERENCES_DATASTORE_NAME) },
    )
}
