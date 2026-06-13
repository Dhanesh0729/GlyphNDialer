// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.glyphdialer.telecom.Constants as TelecomConstants

/**
 * Owns the in-call notification surface (BUILD_SPEC §7/§9): the high-importance
 * full-screen-intent for an incoming call, and the ongoing foreground-style
 * notification for an active call. Channels are created lazily on first use.
 *
 * The in-call Activity itself lives in `:feature:incall`; this module launches it by
 * an implicit ACTION_VIEW intent on a stable scheme so we don't take a dependency on
 * the feature module. `:app` registers that Activity with a matching intent-filter
 * (see [IN_CALL_ACTION] / [IN_CALL_DATA_URI]).
 */
@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    @Volatile private var channelsReady = false

    /** Update both notification surfaces from the current call set. */
    fun update(calls: List<CallModel>, primary: CallModel?) {
        ensureChannels()
        if (primary == null || primary.state.isTerminal) {
            clearAll()
            return
        }
        when {
            primary.isIncomingRinging -> postIncoming(primary)
            else -> postOngoing(primary)
        }
    }

    fun bringInCallToForeground(showDialpad: Boolean) {
        runCatching {
            context.startActivity(inCallIntent(showDialpad).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Timber.tag(TelecomConstants.TAG).w(it, "No in-call Activity registered (:app)") }
    }

    fun clearAll() {
        manager.cancel(Constants.NotificationIds.INCOMING_CALL)
        manager.cancel(Constants.NotificationIds.ONGOING_CALL)
    }

    // --- Builders --------------------------------------------------------------

    private fun postIncoming(call: CallModel) {
        if (!canPost()) return
        val title = call.displayName ?: call.spamLabel ?: call.number.formatted
        val fullScreen = fullScreenPendingIntent()
        val builder = NotificationCompat.Builder(context, Constants.NotificationChannels.INCOMING_CALL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(call.spamLabel?.let { "Suspected: $it" } ?: "Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(contentPendingIntent(showDialpad = false))
        runCatching { manager.notify(Constants.NotificationIds.INCOMING_CALL, builder.build()) }
        manager.cancel(Constants.NotificationIds.ONGOING_CALL)
    }

    private fun postOngoing(call: CallModel) {
        if (!canPost()) return
        manager.cancel(Constants.NotificationIds.INCOMING_CALL)
        val title = call.displayName ?: call.number.formatted
        val text = when (call.state) {
            CallState.HOLDING -> "On hold"
            CallState.DIALING, CallState.CONNECTING -> "Dialing…"
            CallState.CONFERENCE -> "Conference"
            else -> if (call.isVoip) "VoIP call" else "Ongoing call"
        }
        val builder = NotificationCompat.Builder(context, Constants.NotificationChannels.ONGOING_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            // The precise monospaced timer is owned by the in-call UI (it has the exact
            // connectTimeMillis); the notification just shows ongoing status.
            .setContentIntent(contentPendingIntent(showDialpad = false))
        runCatching { manager.notify(Constants.NotificationIds.ONGOING_CALL, builder.build()) }
    }

    /**
     * Exposed so [com.glyphdialer.telecom.service.GlyphInCallService] (or a future
     * foreground-service host) can attach the SAME ongoing notification to a
     * startForeground call with FOREGROUND_SERVICE_TYPE_PHONE_CALL.
     */
    fun buildOngoing(call: CallModel): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, Constants.NotificationChannels.ONGOING_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(call.displayName ?: call.number.formatted)
            .setContentText(if (call.isVoip) "VoIP call" else "Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent(showDialpad = false))
            .build()
    }

    // --- Intents ---------------------------------------------------------------

    private fun inCallIntent(showDialpad: Boolean): Intent =
        Intent(IN_CALL_ACTION).apply {
            setPackage(context.packageName)
            data = android.net.Uri.parse(IN_CALL_DATA_URI)
            putExtra(EXTRA_SHOW_DIALPAD, showDialpad)
        }

    private fun contentPendingIntent(showDialpad: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_CONTENT,
            inCallIntent(showDialpad).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            pendingFlags(),
        )

    private fun fullScreenPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQ_FULLSCREEN,
            inCallIntent(showDialpad = false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_INCOMING, true),
            pendingFlags(),
        )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // --- Channels --------------------------------------------------------------

    private fun ensureChannels() {
        if (channelsReady) return
        synchronized(this) {
            if (channelsReady) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val sys = context.getSystemService(NotificationManager::class.java)
                sys.createNotificationChannel(
                    NotificationChannel(
                        Constants.NotificationChannels.INCOMING_CALL,
                        "Incoming calls",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Ringing / full-screen incoming call alerts"
                        setBypassDnd(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                )
                sys.createNotificationChannel(
                    NotificationChannel(
                        Constants.NotificationChannels.ONGOING_CALL,
                        "Ongoing calls",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Active call status"
                        setShowBadge(false)
                    },
                )
            }
            channelsReady = true
        }
    }

    private fun canPost(): Boolean {
        val granted = manager.areNotificationsEnabled()
        if (!granted) Timber.tag(TelecomConstants.TAG).w("Notifications disabled; cannot post call notification")
        return granted
    }

    companion object {
        /** Implicit action the in-call Activity (`:app`/`:feature:incall`) filters on. */
        const val IN_CALL_ACTION: String = "com.glyphdialer.action.IN_CALL"

        /** Stable data URI so the implicit intent resolves uniquely within our package. */
        const val IN_CALL_DATA_URI: String = "glyphdialer://incall"

        const val EXTRA_SHOW_DIALPAD: String = "com.glyphdialer.extra.SHOW_DIALPAD"
        const val EXTRA_INCOMING: String = "com.glyphdialer.extra.INCOMING"

        private const val REQ_CONTENT = 0x0C01
        private const val REQ_FULLSCREEN = 0x0C02
    }
}
