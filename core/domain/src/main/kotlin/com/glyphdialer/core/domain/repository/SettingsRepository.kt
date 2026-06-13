// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * DataStore-backed user preferences (§19/§21). Reads are an observable [Flow];
 * writes are atomic transforms returning [AppResult].
 */
interface SettingsRepository {

    /** Observe the full, immutable preferences snapshot; emits on every change. */
    val preferences: Flow<UserPreferences>

    /** One-shot read of the current preferences. */
    suspend fun current(): AppResult<UserPreferences>

    /**
     * Atomically transform the stored preferences. The [transform] receives the
     * current snapshot and returns the desired one.
     */
    suspend fun update(transform: (UserPreferences) -> UserPreferences): AppResult<Unit>

    /** Reset all preferences to defaults. */
    suspend fun reset(): AppResult<Unit>
}
