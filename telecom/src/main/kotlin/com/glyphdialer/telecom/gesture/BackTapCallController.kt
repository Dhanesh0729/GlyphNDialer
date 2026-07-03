// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.gesture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.telecom.CallRegistry
import com.glyphdialer.telecom.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Experimental triple back-tap call control.
 *
 * Android does not expose a public Pixel/Nothing "back tap" API to third-party apps,
 * so this uses accelerometer spikes while a call is live. It starts only when the
 * setting is enabled and the in-call service has a foreground call; otherwise sensors
 * stay unregistered.
 */
@Singleton
class BackTapCallController @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    @Dispatcher(GlyphDispatcher.DEFAULT) scopeDispatcher: CoroutineDispatcher,
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + scopeDispatcher)
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile private var preferences: UserPreferences = UserPreferences()
    @Volatile private var targetCallId: String? = null
    @Volatile private var targetIsIncomingRinging: Boolean = false

    private var listening = false
    private var lastMagnitude = SensorManager.GRAVITY_EARTH
    private var lastTapAt = 0L
    private val tapTimes = ArrayDeque<Long>(3)

    init {
        settings.preferences
            .onEach { prefs ->
                preferences = prefs
                if (!prefs.backTapCallControlEnabled) stop()
            }
            .launchIn(scope)
    }

    fun update(primary: CallModel?) {
        targetCallId = primary?.id
        targetIsIncomingRinging = primary?.isIncomingRinging == true

        if (preferences.backTapCallControlEnabled && primary?.state?.isLive == true) {
            start()
        } else {
            stop()
        }
    }

    fun release() {
        stop()
        targetCallId = null
        targetIsIncomingRinging = false
    }

    private fun start() {
        if (listening) return
        val sensor = accelerometer ?: run {
            Timber.tag(Constants.TAG).w("Back-tap call control unavailable: no accelerometer")
            return
        }
        val registered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) == true
        listening = registered
        if (registered) Timber.tag(Constants.TAG).d("Back-tap call control listening")
    }

    private fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
        tapTimes.clear()
        lastTapAt = 0L
        lastMagnitude = SensorManager.GRAVITY_EARTH
        Timber.tag(Constants.TAG).d("Back-tap call control stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = SystemClock.elapsedRealtime()
        val magnitude = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        val delta = abs(magnitude - lastMagnitude)
        lastMagnitude = magnitude

        if (delta < TAP_DELTA_THRESHOLD) return
        if (now - lastTapAt < MIN_TAP_INTERVAL_MS) return
        lastTapAt = now

        while (tapTimes.isNotEmpty() && now - tapTimes.first() > TRIPLE_TAP_WINDOW_MS) {
            tapTimes.removeFirst()
        }
        tapTimes.addLast(now)

        if (tapTimes.size >= REQUIRED_TAPS) {
            tapTimes.clear()
            triggerCallAction()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun triggerCallAction() {
        val id = targetCallId ?: return
        val ok = if (targetIsIncomingRinging) {
            CallRegistry.answer(id)
        } else {
            CallRegistry.disconnect(id)
        }
        Timber.tag(Constants.TAG).i(
            "Triple back-tap %s call result=%b",
            if (targetIsIncomingRinging) "answered" else "ended",
            ok,
        )
    }

    private companion object {
        const val REQUIRED_TAPS = 3
        const val TRIPLE_TAP_WINDOW_MS = 900L
        const val MIN_TAP_INTERVAL_MS = 90L
        const val TAP_DELTA_THRESHOLD = 7.2f
    }
}
