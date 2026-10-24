package dev.pixeldeck.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dev.pixeldeck.data.CalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads the device calendar (which already includes your synced Google calendars). No OAuth needed. */
class CalendarRepo(private val context: Context, scope: CoroutineScope) {
    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())
    val events: StateFlow<List<CalEvent>> = _events

    private val _granted = MutableStateFlow(hasPermission())
    val granted: StateFlow<Boolean> = _granted

    init {
        scope.launch {
            while (isActive) {
                refresh()
                delay(5 * 60_000L)
            }
        }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(days: Int = 7) {
        _granted.value = hasPermission()
        if (!_granted.value) { _events.value = emptyList(); return }
        // The same event often arrives from two synced calendars (e.g. duplicate holiday calendars): show it once.
        _events.value = withContext(Dispatchers.IO) { query(days) }
            .distinctBy { Triple(it.begin, it.end, it.title) }
    }

    private fun query(days: Int): List<CalEvent> {
        val now = System.currentTimeMillis()
        val end = now + days * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_COLOR,
        )
        val out = mutableListOf<CalEvent>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.size < 40) {
                    out += CalEvent(
                        title = c.getString(0).orEmpty().ifBlank { "(no title)" },
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                        location = c.getString(4).orEmpty(),
                        color = c.getInt(5),
                    )
                }
            }
        }
        return out
    }
}
