package dev.pixeldeck.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dev.pixeldeck.MainActivity
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.net.PcStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that keeps the process alive while the phone is a dock display:
 * holds the PC link, evaluates time and charger triggers, and keeps the notification
 * feed bound. Started from MainActivity.
 */
class DashboardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var ticker: Job? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> PixelDeckApp.instance.macros.onCharging(true)
                Intent.ACTION_POWER_DISCONNECTED -> PixelDeckApp.instance.macros.onCharging(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Dashboard", NotificationManager.IMPORTANCE_MIN)
        )
        startForeground(1, buildNotification("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        registerReceiver(
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            Context.RECEIVER_EXPORTED, // system broadcasts must be received as exported
        )

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pixeldeck:dashboard").apply { acquire() }

        val app = PixelDeckApp.instance
        ticker = scope.launch {
            var lastMinute = ""
            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            while (isActive) {
                val hhmm = fmt.format(Date())
                if (hhmm != lastMinute) {
                    lastMinute = hhmm
                    app.macros.onTime(hhmm)
                }
                val status = when (app.pc.status.value) {
                    PcStatus.Connected -> "PC: ${app.pc.pcName.value ?: "connected"}"
                    PcStatus.Connecting -> "PC: connecting…"
                    PcStatus.Rejected -> "PC: wrong token"
                    PcStatus.NotConfigured -> "PC link not set up"
                    PcStatus.Disconnected -> "PC: offline"
                }
                getSystemService(NotificationManager::class.java).notify(1, buildNotification(status))
                delay(15_000)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("PixelDeck running")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerReceiver) }
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "dashboard"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DashboardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DashboardService::class.java))
        }
    }
}
