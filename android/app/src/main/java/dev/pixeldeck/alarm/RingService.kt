package dev.pixeldeck.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.Settings
import dev.pixeldeck.data.SOUND_SILENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Plays the alarm or timer sound, vibrates, and shows a full-screen "ringing" notification until dismissed. */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var autoStop: Job? = null
    private var current: RingInfo? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_SNOOZE -> snooze()
            else -> {
                val kind = intent?.getStringExtra(Scheduler.EXTRA_KIND) ?: RingInfo.KIND_ALARM
                val id = intent?.getStringExtra(Scheduler.EXTRA_ID).orEmpty()
                val label = intent?.getStringExtra(Scheduler.EXTRA_LABEL).orEmpty()
                startRinging(RingInfo(kind, id, label, System.currentTimeMillis()))
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(info: RingInfo) {
        releaseSound()
        current = info
        RingState.set(info)
        startForeground(NOTIFICATION_ID, buildNotification(info), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        scope.launch {
            val settings = PixelDeckApp.instance.store.settings.first()
            if (current !== info) return@launch   // dismissed while settings were loading
            playSound(info, settings)
            vibrate()
            autoStop?.cancel()
            autoStop = launch {
                delay(settings.ringSeconds.coerceIn(10, 600) * 1000L)
                stopRinging()
            }
        }
    }

    private fun soundUri(info: RingInfo, settings: Settings): Uri? {
        val chosen = if (info.isTimer) settings.timerSound else settings.alarmSound
        if (chosen == SOUND_SILENT) return null
        if (chosen.isNotBlank()) return Uri.parse(chosen)
        return RingtoneManager.getDefaultUri(if (info.isTimer) RingtoneManager.TYPE_NOTIFICATION else RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    private fun playSound(info: RingInfo, settings: Settings) {
        val primary = soundUri(info, settings) ?: return   // "silent": vibrate only
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        for (uri in listOfNotNull(primary, fallback).distinct()) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (_: Exception) {
                runCatching { mp.release() }
            }
        }
    }

    private fun vibrate() {
        runCatching {
            val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        }
    }

    private fun releaseSound() {
        autoStop?.cancel()
        autoStop = null
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        runCatching { getSystemService(VibratorManager::class.java).defaultVibrator.cancel() }
    }

    private fun stopRinging() {
        releaseSound()
        current = null
        RingState.set(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Alarm: ring again after the snooze time. Timer: add one more minute. Both are implemented as a new timer. */
    private fun snooze() {
        val info = current
        stopRinging()
        if (info == null) return
        val app = PixelDeckApp.instance
        app.scope.launch {
            val minutes = if (info.isTimer) 1 else app.store.settings.first().snoozeMinutes.coerceIn(1, 60)
            val label = if (info.isTimer) info.label else info.label.ifBlank { "Alarm" } + " (snoozed)"
            Scheduler.startTimer(applicationContext, minutes * 60_000L, label)
        }
    }

    private fun buildNotification(info: RingInfo): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm or timer is ringing"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(info.title)
            .setContentText(if (info.isTimer) "Time's up" else "Alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, if (info.isTimer) "+1 min" else "Snooze", actionIntent(ACTION_SNOOZE, 1))
            .addAction(0, "Dismiss", actionIntent(ACTION_STOP, 2))
            .build()
    }

    private fun actionIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, RingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    override fun onDestroy() {
        releaseSound()
        if (current != null) RingState.set(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ring"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_STOP = "dev.pixeldeck.action.RING_STOP"
        const val ACTION_SNOOZE = "dev.pixeldeck.action.RING_SNOOZE"

        fun start(context: Context, kind: String, id: String, label: String) {
            val intent = Intent(context, RingService::class.java)
                .putExtra(Scheduler.EXTRA_KIND, kind)
                .putExtra(Scheduler.EXTRA_ID, id)
                .putExtra(Scheduler.EXTRA_LABEL, label)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RingService::class.java).setAction(ACTION_STOP))
        }

        fun snooze(context: Context) {
            context.startService(Intent(context, RingService::class.java).setAction(ACTION_SNOOZE))
        }
    }
}
