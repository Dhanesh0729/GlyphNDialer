// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.repository

import com.glyphdialer.core.common.AppResult
import com.glyphdialer.core.domain.model.CapabilityFlags
import kotlinx.coroutines.flow.Flow

/**
 * The runtime authority for the HONESTY PRINCIPLE (§9). Resolves what the
 * device/OS can actually do — Glyph hardware, default-dialer role, call-audio
 * access, VoIP reachability, VVM, camera, on-device speech — and exposes it as an
 * observable [CapabilityFlags] the whole UI consults before offering a feature.
 *
 * Impl lives in `:core:data` and re-resolves on relevant system changes (role
 * granted, permission changed, BT connected, network up).
 */
interface CapabilityRepository {

    /** Observe the live capability snapshot; emits whenever a capability changes. */
    val capabilities: Flow<CapabilityFlags>

    /** One-shot read of the current capabilities. */
    suspend fun current(): AppResult<CapabilityFlags>

    /** Force a re-evaluation (e.g. after a permission/role change) and return the result. */
    suspend fun refresh(): AppResult<CapabilityFlags>
}
