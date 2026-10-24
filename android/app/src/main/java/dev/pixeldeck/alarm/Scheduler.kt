package dev.pixeldeck.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.pixeldeck.MainActivity
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.AlarmItem
import dev.pixeldeck.data.TimerItem
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.UUID

/**
 * Talks to AlarmManager. Both timers and alarms use setAlarmClock, which fires exactly, wakes the
 * device from Doze and needs no special permission (it is what clock apps use).
 */
object Scheduler {
    const val ACTION_FIRE = "dev.pixeldeck.action.ALARM_FIRE"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val EXTRA_LABEL = "label"

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun firePending(context: Context, kind: String, id: String, label: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // The data URI makes each timer/alarm a distinct PendingIntent (extras are ignored when matching).
            .setData(Uri.parse("pixeldeck://alarm/$kind/$id"))
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun showPending(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun set(context: Context, atMillis: Long, kind: String, id: String, label: String) {
        val info = AlarmManager.AlarmClockInfo(atMillis, showPending(context))
        alarmManager(context).setAlarmClock(info, firePending(context, kind, id, label))
    }

    private fun cancel(context: Context, kind: String, id: String) {
        alarmManager(context).cancel(firePending(context, kind, id, ""))
    }

    // ---- timers ----

    fun scheduleTimer(context: Context, t: TimerItem) = set(context, t.endAt, RingInfo.KIND_TIMER, t.id, t.label)

    suspend fun startTimer(context: Context, durationMs: Long, label: String): TimerItem {
        val timer = TimerItem(UUID.randomUUID().toString(), label.trim(), durationMs, System.currentTimeMillis() + durationMs)
        PixelDeckApp.instance.store.updateTimers { it + timer }
        scheduleTimer(context, timer)
        return timer
    }

    suspend fun cancelTimer(context: Context, id: String) {
        cancel(context, RingInfo.KIND_TIMER, id)
        PixelDeckApp.instance.store.updateTimers { list -> list.filterNot { it.id == id } }
    }

    suspend fun addToTimer(context: Context, id: String, extraMs: Long) {
        var updated: TimerItem? = null
        PixelDeckApp.instance.store.updateTimers { list ->
            list.map { if (it.id == id) it.copy(endAt = it.endAt + extraMs, durationMs = it.durationMs + extraMs).also { n -> updated = n } else it }
        }
        updated?.let { scheduleTimer(context, it) }
    }

    // ---- alarms ----

    fun nextTrigger(a: AlarmItem, now: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        AlarmMath.nextTrigger(a.hour, a.minute, a.days, now)

    fun scheduleAlarm(context: Context, a: AlarmItem) {
        if (!a.enabled) {
            cancel(context, RingInfo.KIND_ALARM, a.id)
            return
        }
        set(context, AlarmMath.toEpochMillis(nextTrigger(a)), RingInfo.KIND_ALARM, a.id, a.label)
    }

    suspend fun saveAlarm(context: Context, a: AlarmItem) {
        PixelDeckApp.instance.store.updateAlarms { list -> if (list.any { it.id == a.id }) list.map { if (it.id == a.id) a else it } else list + a }
        scheduleAlarm(context, a)
    }

    suspend fun deleteAlarm(context: Context, id: String) {
        cancel(context, RingInfo.KIND_ALARM, id)
        PixelDeckApp.instance.store.updateAlarms { list -> list.filterNot { it.id == id } }
    }

    // ---- lifecycle ----

    /** Called when a scheduled alarm fires: finish bookkeeping so the next occurrence is armed or the item removed. */
    suspend fun onFired(context: Context, kind: String, id: String) {
        val store = PixelDeckApp.instance.store
        if (kind == RingInfo.KIND_TIMER) {
            store.updateTimers { list -> list.filterNot { it.id == id } }
        } else {
            var next: AlarmItem? = null
            store.updateAlarms { list ->
                list.map {
                    if (it.id != id) it
                    else if (it.days == 0) it.copy(enabled = false)
                    else it.also { a -> next = a }
                }
            }
            next?.let { scheduleAlarm(context, it) }
        }
    }

    /** AlarmManager forgets everything on reboot or app update, so re-arm from the saved state. */
    suspend fun rescheduleAll(context: Context) {
        val store = PixelDeckApp.instance.store
        val now = System.currentTimeMillis()
        val timers = store.timers.first()
        // Timers that ended while the phone was off are dropped silently; the rest are re-armed.
        store.updateTimers { list -> list.filter { it.endAt > now } }
        timers.filter { it.endAt > now }.forEach { scheduleTimer(context, it) }
        store.alarms.first().forEach { scheduleAlarm(context, it) }
    }
}
