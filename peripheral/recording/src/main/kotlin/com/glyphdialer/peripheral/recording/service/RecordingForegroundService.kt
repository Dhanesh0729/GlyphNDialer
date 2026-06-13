// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.peripheral.recording.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.core.domain.model.RecordingTier
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive WHILE a recording is in progress (§12)
 * and surfaces the mandatory ongoing notification. It also mirrors the recording onto the
 * Glyph hardware via the injected [GlyphController] (a graceful no-op on non-Nothing
 * devices, per §9).
 *
 * Service TYPE is chosen honestly from the active [RecordingTier]:
 *  - SYSTEM_TWO_WAY (Tier A) -> `phoneCall|microphone` (attaches to the call session)
 *  - everything else         -> `microphone`
 *
 * This service does NOT itself own the AudioRecord; [com.glyphdialer.peripheral.recording.CallRecorderImpl]
 * holds the capture engines. The service exists to satisfy the foreground-service
 * requirement for mic access during a call and to host the notification + Glyph mirror.
 */
@AndroidEntryPoint
class RecordingForegroundService : android.app.Service() {

    @Inject lateinit var glyphController: GlyphController

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> {
                Timber.w("RecordingForegroundService started with no/unknown action; stopping")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val tier = intent.getStringExtra(EXTRA_TIER)
            ?.let { runCatching { RecordingTier.valueOf(it) }.getOrNull() }
            ?: RecordingTier.LOCAL_ONE_SIDED
        val title = intent.getStringExtra(EXTRA_TITLE) ?: DEFAULT_TITLE

        val notification = buildNotification(tier, title)
        startAsForeground(notification, tier)

        // Glyph recording indicator (no-op when unavailable, §9).
        runCatching { glyphController.showRecording(active = true) }
            .onFailure { Timber.w(it, "Glyph showRecording failed") }

        Timber.d("RecordingForegroundService started (tier=%s)", tier)
    }

    private fun handleStop() {
        runCatching { glyphController.showRecording(active = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("RecordingForegroundService stopped")
    }

    private fun startAsForeground(notification: Notification, tier: RecordingTier) {
        val type = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
            tier == RecordingTier.SYSTEM_TWO_WAY ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            ServiceCompat.startForeground(
                this,
                Constants.NotificationIds.RECORDING,
                notification,
                type,
            )
        } catch (t: Throwable) {
            // e.g. ForegroundServiceStartNotAllowedException / missing mic permission.
            Timber.e(t, "startForeground failed; stopping recording service")
            stopSelf()
        }
    }

    private fun buildNotification(tier: RecordingTier, title: String): Notification {
        val text = when (tier) {
            RecordingTier.SYSTEM_TWO_WAY -> "Recording (two-way)"
            RecordingTier.VOIP_TWO_WAY -> "Recording (two-way, in-app)"
            RecordingTier.LOCAL_ONE_SIDED -> "Recording (my side only)"
            RecordingTier.UNAVAILABLE -> "Recording unavailable"
        }
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecordingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Constants.NotificationChannels.RECORDING)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(Constants.NotificationChannels.RECORDING) != null) return
        val channel = NotificationChannel(
            Constants.NotificationChannels.RECORDING,
            "Call recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while a call is being recorded."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        // Ensure the Glyph indicator is cleared even on an abrupt kill (no-op when
        // Glyph is unavailable, §9).
        runCatching { glyphController.showRecording(active = false) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.glyphdialer.peripheral.recording.action.START"
        const val ACTION_STOP = "com.glyphdialer.peripheral.recording.action.STOP"
        const val EXTRA_TIER = "extra_tier"
        const val EXTRA_TITLE = "extra_title"
        private const val DEFAULT_TITLE = "Glyph Dialer"

        /** Builds the start intent for the resolved [tier] and [title]. */
        fun startIntent(context: Context, tier: RecordingTier, title: String): Intent =
            Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TIER, tier.name)
                .putExtra(EXTRA_TITLE, title)

        /** Builds the stop intent. */
        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)

        /** Whether POST_NOTIFICATIONS is granted (Android 13+); informational for callers. */
        fun canPostNotifications(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
