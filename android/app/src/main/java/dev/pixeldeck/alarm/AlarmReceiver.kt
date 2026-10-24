package dev.pixeldeck.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pixeldeck.PixelDeckApp
import kotlinx.coroutines.launch

/** Receives the exact alarm from AlarmManager and starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_FIRE) return
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND) ?: return
        val id = intent.getStringExtra(Scheduler.EXTRA_ID) ?: return
        val label = intent.getStringExtra(Scheduler.EXTRA_LABEL).orEmpty()
        val app = context.applicationContext as PixelDeckApp

        // Start ringing first: the exact-alarm exemption that allows starting a foreground service is short-lived.
        RingService.start(context, kind, id, label)
        app.macros.onClockEvent(kind, label)

        val pending = goAsync()
        app.scope.launch {
            try {
                Scheduler.onFired(context, kind, id)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-arms timers and alarms after a reboot, app update or clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val app = context.applicationContext as PixelDeckApp
                val pending = goAsync()
                app.scope.launch {
                    try {
                        Scheduler.rescheduleAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
