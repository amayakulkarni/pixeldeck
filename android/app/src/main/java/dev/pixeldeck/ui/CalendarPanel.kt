package dev.pixeldeck.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.CalEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarPanel(modifier: Modifier = Modifier) {
    val app = PixelDeckApp.instance
    val events by app.calendar.events.collectAsStateWithLifecycle()
    val granted by app.calendar.granted.collectAsStateWithLifecycle()
    val requestPermissions = LocalRequestPermissions.current

    // Refresh whenever the panel appears so it is never stale after a permission grant.
    LaunchedEffect(Unit) { app.calendar.refresh() }

    PanelCard("Calendar", modifier) {
        when {
            !granted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calendar access is needed to show upcoming events.", color = Muted)
                PrimaryButton("Grant calendar access", { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) })
            }
            events.isEmpty() -> Text("Nothing in the next 7 days.", color = Muted)
            else -> {
                val zone = ZoneId.systemDefault()
                val grouped = events.distinctBy { Triple(it.begin, it.end, it.title) }.groupBy { dayOf(it, zone) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped.forEach { (day, list) ->
                        item(key = "h$day") {
                            Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = Muted, modifier = Modifier.padding(top = 4.dp))
                        }
                        itemsIndexed(list, key = { i, e -> "$day-$i-${e.begin}" }) { _, e -> EventRow(e, LocalLock.current) }
                    }
                }
            }
        }
    }
}

private fun dayOf(e: CalEvent, zone: ZoneId): LocalDate =
    if (e.allDay) Instant.ofEpochMilli(e.begin).atZone(ZoneId.of("UTC")).toLocalDate()
    else Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate()

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
}

@Composable
private fun EventRow(e: CalEvent, lock: LockState) {
    val hide = lock.locked && !lock.showDetails
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                .background(if (e.color != 0) Color(e.color) else Magenta)
        )
        Column {
            Text(if (hide) "Busy" else e.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val sub = buildString {
                append(if (e.allDay) "All day" else "${formatTime(e.begin)} – ${formatTime(e.end)}")
                if (e.location.isNotBlank() && !hide) append("  ·  ${e.location}")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
        }
    }
}
