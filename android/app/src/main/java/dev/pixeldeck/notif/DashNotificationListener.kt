package dev.pixeldeck.notif

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import dev.pixeldeck.PixelDeckApp
import android.media.RingtoneManager
import dev.pixeldeck.data.DashNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/** In-memory feed shared between the listener service and the UI. */
object NotifRepo {
    private val _items = MutableStateFlow<List<DashNotification>>(emptyList())
    val items: StateFlow<List<DashNotification>> = _items

    private val intents = ConcurrentHashMap<String, PendingIntent>()
    @Volatile var listener: DashNotificationListener? = null

    fun set(list: List<DashNotification>) { _items.value = list.sortedByDescending { it.time } }

    fun upsert(n: DashNotification, intent: PendingIntent?) {
        if (intent != null) intents[n.key] = intent
        _items.value = (_items.value.filterNot { it.key == n.key } + n).sortedByDescending { it.time }
    }

    fun remove(key: String) {
        intents.remove(key)
        _items.value = _items.value.filterNot { it.key == key }
    }

    fun open(key: String) {
        runCatching { intents[key]?.send() }
    }

    fun dismiss(key: String) {
        listener?.cancelNotification(key)
        remove(key)
    }

    fun dismissAll() {
        listener?.cancelAllNotifications()
        _items.value = emptyList()
        intents.clear()
    }

    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

class DashNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotifRepo.listener = this
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotifRepo.set(emptyList())
        list.forEach { handle(it, fireTriggers = false) }
    }

    override fun onListenerDisconnected() {
        if (NotifRepo.listener === this) NotifRepo.listener = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, fireTriggers = true)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotifRepo.remove(sbn.key)

    private fun handle(sbn: StatusBarNotification, fireTriggers: Boolean) {
        val n = sbn.notification
        // Skip ongoing/foreground-service notifications, group summaries and our own.
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val item = DashNotification(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appLabel(sbn.packageName),
            title = title,
            text = text,
            time = sbn.postTime,
        )
        NotifRepo.upsert(item, n.contentIntent)
        if (fireTriggers) {
            val app = PixelDeckApp.instance
            app.macros.onNotification(sbn.packageName)
            app.scope.launch {
                val settings = app.store.settings.first()
                if (settings.notifChime) Chime.play(app, settings.notifSound, RingtoneManager.TYPE_NOTIFICATION)
            }
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        /** Asks the system to rebind the listener, useful after the process was killed. */
        fun requestRebind(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(context, DashNotificationListener::class.java))
            }
        }
    }
}
