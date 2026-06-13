// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * Audio output routes for an active call, mirroring
 * android.telecom.CallAudioState route constants.
 */
enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET,
}

/**
 * Snapshot of the system call-audio state, mirrored from
 * android.telecom.CallAudioState into the domain so the in-call UI can render the
 * route picker without framework imports.
 *
 * [supportedRoutes] is the set the device currently offers; [route] is the active
 * one; [bluetoothDeviceName] names the connected BT device when relevant.
 */
data class AudioState(
    val route: AudioRoute = AudioRoute.EARPIECE,
    val supportedRoutes: Set<AudioRoute> = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    val isMuted: Boolean = false,
    val bluetoothDeviceName: String? = null,
) {
    fun supports(target: AudioRoute): Boolean = target in supportedRoutes
}
