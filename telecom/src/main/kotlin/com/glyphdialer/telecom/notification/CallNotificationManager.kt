// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.telecom.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glyphdialer.core.common.Constants
import com.glyphdialer.core.common.dispatchers.Dispatcher
import com.glyphdialer.core.common.dispatchers.GlyphDispatcher
import com.glyphdialer.core.domain.model.CallModel
import com.glyphdialer.core.domain.model.CallState
import com.glyphdialer.core.domain.model.UserPreferences
import com.glyphdialer.core.domain.repository.SettingsRepository
import com.glyphdialer.telecom.service.GlyphInCallService
import com.glyphdialer.telecom.CallRegistry
import com.glyphdialer.telecom.R
import com.glyphdialer.core.domain.model.AudioRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    settings: SettingsRepository,
    @Dispatcher(GlyphDispatcher.DEFAULT) scopeDispatcher: CoroutineDispatcher,
) {

    private val manager = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(SupervisorJob() + scopeDispatcher)

    @Volatile private var channelsReady = false
    @Volatile private var lastIncomingVibratedCallId: String? = null
    @Volatile private var preferences: UserPreferences = UserPreferences()

    init {
        settings.preferences
            .onEach { preferences = it }
            .launchIn(scope)
    }

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
        lastIncomingVibratedCallId = null
        manager.cancel(Constants.NotificationIds.INCOMING_CALL)
        manager.cancel(Constants.NotificationIds.ONGOING_CALL)
    }

    // --- Builders --------------------------------------------------------------

    @android.annotation.SuppressLint("MissingPermission")
    private fun postIncoming(call: CallModel) {
        if (!canPost()) return
        val title = call.displayName ?: call.spamLabel ?: call.number.formatted
        val fullScreen = fullScreenPendingIntent()
        val person = androidx.core.app.Person.Builder()
            .setName(title)
            .setImportant(true)
            .build()
        val builder = NotificationCompat.Builder(context, Constants.NotificationChannels.INCOMING_CALL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(call.spamLabel?.let { "Suspected: $it" } ?: "Incoming call")
            .setSubText("Glyph Dialer")
            .setTicker("Incoming call from $title")
            .setColor(0xFFD7263D.toInt())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(contentPendingIntent(showDialpad = false))
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    actionPendingIntent(GlyphInCallService.ACTION_DECLINE, call.id),
                    actionPendingIntent(GlyphInCallService.ACTION_ANSWER, call.id)
                )
            )
        if (preferences.incomingCallVibrationEnabled) {
            builder.setVibrate(INCOMING_VIBRATION_PATTERN)
            vibrateIncoming(call.id)
        }
        runCatching { manager.notify(Constants.NotificationIds.INCOMING_CALL, builder.build()) }
        manager.cancel(Constants.NotificationIds.ONGOING_CALL)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun postOngoing(call: CallModel) {
        if (!canPost()) return
        manager.cancel(Constants.NotificationIds.INCOMING_CALL)
        val title = call.displayName ?: call.number.formatted
        val person = androidx.core.app.Person.Builder()
            .setName(title)
            .setImportant(true)
            .build()
        val text = when (call.state) {
            CallState.HOLDING -> "On hold"
            CallState.DIALING, CallState.CONNECTING -> "Dialing…"
            CallState.CONFERENCE -> "Conference"
            else -> if (call.isVoip) "VoIP call" else "Ongoing call"
        }
        val isMuted = CallRegistry.audioState.value.isMuted
        val isSpeaker = CallRegistry.audioState.value.route == AudioRoute.SPEAKER
        
        val muteIcon = if (isMuted) R.drawable.ic_unmute else R.drawable.ic_mute
        val muteLabel = if (isMuted) "Unmute" else "Mute"
        
        val speakerIcon = if (isSpeaker) R.drawable.ic_earpiece else R.drawable.ic_speaker
        val speakerLabel = if (isSpeaker) "Earpiece" else "Speaker"

        val builder = NotificationCompat.Builder(context, Constants.NotificationChannels.ONGOING_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent(showDialpad = false))
            .addAction(
                NotificationCompat.Action.Builder(
                    muteIcon,
                    muteLabel,
                    actionPendingIntent(GlyphInCallService.ACTION_TOGGLE_MUTE, call.id)
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    speakerIcon,
                    speakerLabel,
                    actionPendingIntent(GlyphInCallService.ACTION_TOGGLE_SPEAKER, call.id)
                ).build()
            )
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    actionPendingIntent(GlyphInCallService.ACTION_DISCONNECT, call.id)
                )
            )
        runCatching { manager.notify(Constants.NotificationIds.ONGOING_CALL, builder.build()) }
    }

    /**
     * Exposed so [com.glyphdialer.telecom.service.GlyphInCallService] (or a future
     * foreground-service host) can attach the SAME ongoing notification to a
     * startForeground call with FOREGROUND_SERVICE_TYPE_PHONE_CALL.
     */
    fun buildOngoing(call: CallModel): Notification {
        ensureChannels()
        val title = call.displayName ?: call.number.formatted
        val person = androidx.core.app.Person.Builder()
            .setName(title)
            .setImportant(true)
            .build()
        val isMuted = CallRegistry.audioState.value.isMuted
        val isSpeaker = CallRegistry.audioState.value.route == AudioRoute.SPEAKER
        
        val muteIcon = if (isMuted) R.drawable.ic_unmute else R.drawable.ic_mute
        val muteLabel = if (isMuted) "Unmute" else "Mute"
        
        val speakerIcon = if (isSpeaker) R.drawable.ic_earpiece else R.drawable.ic_speaker
        val speakerLabel = if (isSpeaker) "Earpiece" else "Speaker"

        return NotificationCompat.Builder(context, Constants.NotificationChannels.ONGOING_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentText(if (call.isVoip) "VoIP call" else "Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent(showDialpad = false))
            .addAction(
                NotificationCompat.Action.Builder(
                    muteIcon,
                    muteLabel,
                    actionPendingIntent(GlyphInCallService.ACTION_TOGGLE_MUTE, call.id)
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    speakerIcon,
                    speakerLabel,
                    actionPendingIntent(GlyphInCallService.ACTION_TOGGLE_SPEAKER, call.id)
                ).build()
            )
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    actionPendingIntent(GlyphInCallService.ACTION_DISCONNECT, call.id)
                )
            )
            .build()
    }

    fun vibrateCallEnded() {
        if (!preferences.callEndVibrationEnabled) return
        vibratePattern(CALL_END_VIBRATION_PATTERN, "Call-end vibration failed")
    }

    // --- Intents ---------------------------------------------------------------

    private fun inCallIntent(showDialpad: Boolean): Intent =
        Intent(IN_CALL_ACTION).apply {
            setPackage(context.packageName)
            setClassName(context.packageName, "com.glyphdialer.MainActivity")
            addCategory(Intent.CATEGORY_DEFAULT)
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

    private fun actionPendingIntent(action: String, callId: String): PendingIntent {
        val intent = Intent(context, GlyphInCallService::class.java).apply {
            this.action = action
            putExtra(GlyphInCallService.EXTRA_CALL_ID, callId)
        }
        return PendingIntent.getService(
            context,
            action.hashCode() + callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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
                        enableVibration(true)
                        vibrationPattern = INCOMING_VIBRATION_PATTERN
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

    private fun vibrateIncoming(callId: String) {
        if (lastIncomingVibratedCallId == callId) return
        lastIncomingVibratedCallId = callId
        vibratePattern(INCOMING_VIBRATION_PATTERN, "Incoming-call vibration failed", isRingtone = true)
    }

    private fun vibratePattern(pattern: LongArray, failureLog: String, isRingtone: Boolean = false) {
        val vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull() ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(if (isRingtone) android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE else android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = android.os.VibrationAttributes.Builder()
                        .setUsage(if (isRingtone) android.os.VibrationAttributes.USAGE_RINGTONE else android.os.VibrationAttributes.USAGE_COMMUNICATION_REQUEST)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(effect, audioAttributes)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1, audioAttributes)
            }
        }.onFailure { Timber.tag(TelecomConstants.TAG).w(it, failureLog) }
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

        private val INCOMING_VIBRATION_PATTERN = longArrayOf(0, 90, 70, 90, 180, 220)
        private val CALL_END_VIBRATION_PATTERN = longArrayOf(0, 45, 60, 90)

        private const val REQ_CONTENT = 0x0C01
        private const val REQ_FULLSCREEN = 0x0C02
    }
}
