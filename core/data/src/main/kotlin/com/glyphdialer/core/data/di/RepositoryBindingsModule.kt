// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.data.di

import com.glyphdialer.core.data.datastore.SettingsRepositoryImpl
import com.glyphdialer.core.data.provider.BlockedNumberRepositoryImpl
import com.glyphdialer.core.data.provider.CallLogRepositoryImpl
import com.glyphdialer.core.data.provider.ContactsRepositoryImpl
import com.glyphdialer.core.data.provider.VoicemailRepositoryImpl
import com.glyphdialer.core.data.repository.CallNoteRepositoryImpl
import com.glyphdialer.core.data.repository.CapabilityRepositoryImpl
import com.glyphdialer.core.data.repository.CustomGlyphPatternRepositoryImpl
import com.glyphdialer.core.data.repository.FavoritesRepositoryImpl
import com.glyphdialer.core.data.repository.PhoneNumberFormatterImpl
import com.glyphdialer.core.data.repository.RecordingRepositoryImpl
import com.glyphdialer.core.data.repository.SpeedDialRepositoryImpl
import com.glyphdialer.core.data.repository.TranscriptRepositoryImpl
import com.glyphdialer.core.domain.repository.BlockedNumberRepository
import com.glyphdialer.core.domain.repository.CallLogRepository
import com.glyphdialer.core.domain.repository.CallNoteRepository
import com.glyphdialer.core.domain.repository.CapabilityRepository
import com.glyphdialer.core.domain.repository.ContactsRepository
import com.glyphdialer.core.domain.repository.CustomGlyphPatternRepository
import com.glyphdialer.core.domain.repository.FavoritesRepository
import com.glyphdialer.core.domain.repository.PhoneNumberFormatter
import com.glyphdialer.core.domain.repository.RecordingRepository
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.core.domain.repository.SpeedDialRepository
import com.glyphdialer.core.domain.repository.TranscriptRepository
import com.glyphdialer.core.domain.repository.VoicemailRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every `:core:data` repository implementation to its `:core:domain`
 * interface (CONVENTIONS.md §5). Note that [com.glyphdialer.core.domain.repository.CallRecorder]
 * and [com.glyphdialer.core.domain.repository.TranscriptionEngine] are PERIPHERAL
 * contracts — their impls live in :peripheral:recording / :peripheral:transcription
 * and are bound there. This module consumes them but does not provide them.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindSpeedDialRepository(impl: SpeedDialRepositoryImpl): SpeedDialRepository

    @Binds
    @Singleton
    abstract fun bindCustomGlyphPatternRepository(impl: CustomGlyphPatternRepositoryImpl): CustomGlyphPatternRepository

    @Binds
    @Singleton
    abstract fun bindCallNoteRepository(impl: CallNoteRepositoryImpl): CallNoteRepository

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(impl: RecordingRepositoryImpl): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindTranscriptRepository(impl: TranscriptRepositoryImpl): TranscriptRepository

    @Binds
    @Singleton
    abstract fun bindContactsRepository(impl: ContactsRepositoryImpl): ContactsRepository

    @Binds
    @Singleton
    abstract fun bindCallLogRepository(impl: CallLogRepositoryImpl): CallLogRepository

    @Binds
    @Singleton
    abstract fun bindVoicemailRepository(impl: VoicemailRepositoryImpl): VoicemailRepository

    @Binds
    @Singleton
    abstract fun bindBlockedNumberRepository(impl: BlockedNumberRepositoryImpl): BlockedNumberRepository

    @Binds
    @Singleton
    abstract fun bindCapabilityRepository(impl: CapabilityRepositoryImpl): CapabilityRepository

    @Binds
    @Singleton
    abstract fun bindPhoneNumberFormatter(impl: PhoneNumberFormatterImpl): PhoneNumberFormatter


}
