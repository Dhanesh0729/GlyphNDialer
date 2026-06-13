// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.usecase

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observe the user's preferences as an immutable, always-up-to-date snapshot (§21). */
class ObservePreferencesUseCase @Inject constructor(
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = settings.preferences
}

/**
 * Atomically update preferences (§21). Callers pass a transform so reads and writes
 * never race. Convenience [set] applies a full replacement snapshot.
 */
class UpdatePreferencesUseCase @Inject constructor(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(transform: (UserPreferences) -> UserPreferences): AppResult<Unit> =
        settings.update(transform)

    suspend fun set(preferences: UserPreferences): AppResult<Unit> =
        settings.update { preferences }
}
