package dev.pixeldeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.alarm.AlarmMath
import dev.pixeldeck.alarm.Durations
import dev.pixeldeck.alarm.Scheduler
import dev.pixeldeck.data.AlarmItem
import dev.pixeldeck.data.Settings
import dev.pixeldeck.data.TimerItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
private fun rememberNowMillis(tickMs: Long = 250): State<Long> =
    produceState(System.currentTimeMillis(), tickMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(tickMs)
        }
    }

private fun timeText(hour: Int, minute: Int, use24h: Boolean): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a"))

/** Full page: timers, alarms and a stopwatch side by side. */
@Composable
fun TimersPage(settings: Settings, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard("Timers", Modifier.weight(1f).fillMaxSize()) { TimersList(showEntry = true) }
        PanelCard("Alarms", Modifier.weight(1f).fillMaxSize()) { AlarmsList(settings.use24h) }
        PanelCard("Stopwatch", Modifier.weight(0.8f).fillMaxSize()) { StopwatchView() }
    }
}

/** Small Home panel: running timers, quick start buttons and the next alarm. */
@Composable
fun TimersCompact(settings: Settings, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = PixelDeckApp.instance
    val alarms by app.store.alarms.collectAsStateWithLifecycle(initialValue = emptyList())
    val next = alarms.filter { it.enabled }
        .map { it to Scheduler.nextTrigger(it, LocalDateTime.now()) }
        .minByOrNull { it.second }
    PanelCard(
        "Timers and alarms", modifier,
        actions = { TextButton(onClick = onOpen) { Text("Open") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimersList(showEntry = false, maxRows = 3)
            Text(
                next?.let { (a, at) ->
                    val day = if (at.toLocalDate() == LocalDate.now()) "today" else at.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }
                    "Next alarm: ${timeText(a.hour, a.minute, settings.use24h)} ($day)${if (a.label.isBlank()) "" else " - ${a.label}"}"
                } ?: "No alarms set",
                color = Muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TimersList(showEntry: Boolean, maxRows: Int = Int.MAX_VALUE) {
    val app = PixelDeckApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val timers by app.store.timers.collectAsStateWithLifecycle(initialValue = emptyList())
    val now by rememberNowMillis()
    var text by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun start(ms: Long, lbl: String) {
        scope.launch { Scheduler.startTimer(ctx, ms, lbl) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1m" to 60_000L, "5m" to 300_000L, "10m" to 600_000L, "25m" to 1_500_000L).forEach { (name, ms) ->
                GhostButton(name, { start(ms, "") })
            }
        }
        if (showEntry) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it; error = null }, label = { Text("Duration (10m, 1h30m, 90s)") },
                    singleLine = true, isError = error != null, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.weight(0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Start timer", {
                    val ms = Durations.parse(text)
                    if (ms == null) error = "Try 10m, 1h30m, 90s or 05:00" else { start(ms, label); text = ""; label = "" }
                })
                error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (timers.isEmpty()) {
            Text("No timers running", color = Muted)
        } else {
            val shown = timers.sortedBy { it.endAt }.take(maxRows)
            if (showEntry) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { TimerRow(it, now) { id -> scope.launch { Scheduler.cancelTimer(ctx, id) } } }
                }
            } else {
                shown.forEach { TimerRow(it, now) { id -> scope.launch { Scheduler.cancelTimer(ctx, id) } } }
            }
        }
    }
}

@Composable
private fun TimerRow(t: TimerItem, now: Long, onCancel: (String) -> Unit) {
    val remaining = t.endAt - now
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Durations.format(remaining), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    t.label.ifBlank { Durations.describe(t.durationMs) },
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { onCancel(t.id) }) { Icon(Icons.Default.Close, contentDescription = "Cancel timer") }
        }
        LinearProgressIndicator(
            progress = { (1f - remaining.toFloat() / t.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = Magenta,
        )
    }
}

@Composable
private fun AlarmsList(use24h: Boolean) {
    val app = PixelDeckApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by app.store.alarms.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("New alarm", { creating = true })
        if (alarms.isEmpty()) Text("No alarms yet", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(alarms.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = a }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            timeText(a.hour, a.minute, use24h),
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = if (a.enabled) MaterialTheme.colorScheme.onSurface else Muted,
                        )
                        Text(
                            AlarmMath.describeDays(a.days) + if (a.label.isBlank()) "" else " - ${a.label}",
                            color = Muted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(a.enabled, { on -> scope.launch { Scheduler.saveAlarm(ctx, a.copy(enabled = on)) } })
                    IconButton(onClick = { scope.launch { Scheduler.deleteAlarm(ctx, a.id) } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete alarm")
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        AlarmDialog(
            initial = editing, use24h = use24h,
            onDismiss = { creating = false; editing = null },
            onSave = { alarm ->
                creating = false; editing = null
                scope.launch { Scheduler.saveAlarm(ctx, alarm) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmDialog(initial: AlarmItem?, use24h: Boolean, onDismiss: () -> Unit, onSave: (AlarmItem) -> Unit) {
    val now = LocalTime.now()
    val state = rememberTimePickerState(initial?.hour ?: now.hour, initial?.minute ?: now.minute, use24h)
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var days by remember { mutableStateOf(initial?.days ?: 0) }
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New alarm" else "Edit alarm") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeInput(state)
                OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    letters.forEachIndexed { i, l ->
                        val on = (days and (1 shl i)) != 0
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (on) Magenta else PanelEdge)
                                .clickable { days = days xor (1 shl i) },
                            contentAlignment = Alignment.Center,
                        ) { Text(l) }
                    }
                }
                Text(AlarmMath.describeDays(days), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    AlarmItem(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        label = label.trim(), hour = state.hour, minute = state.minute,
                        days = days, enabled = true,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Stopwatch state lives here so it keeps running while you switch pages. */
private object StopwatchState {
    var running by mutableStateOf(false)
    var startedAt by mutableLongStateOf(0L)
    var accumulated by mutableLongStateOf(0L)
    var laps by mutableStateOf(listOf<Long>())

    fun elapsed(now: Long) = accumulated + if (running) now - startedAt else 0L

    fun toggle(now: Long) {
        if (running) accumulated += now - startedAt else startedAt = now
        running = !running
    }

    fun lap(now: Long) { if (running) laps = laps + elapsed(now) }

    fun reset() { running = false; accumulated = 0; laps = emptyList() }
}

@Composable
private fun StopwatchView() {
    val now by rememberNowMillis(50)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(Durations.formatStopwatch(StopwatchState.elapsed(now)), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (StopwatchState.running) "Stop" else "Start", { StopwatchState.toggle(System.currentTimeMillis()) })
            GhostButton("Lap", { StopwatchState.lap(System.currentTimeMillis()) })
            GhostButton("Reset", { StopwatchState.reset() })
        }
        LazyColumn {
            items(StopwatchState.laps.withIndex().toList().asReversed(), key = { it.index }) { (i, t) ->
                val previous = if (i == 0) 0L else StopwatchState.laps[i - 1]
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Lap ${i + 1}", Modifier.weight(1f), color = Muted)
                    Text("+" + Durations.formatStopwatch(t - previous))
                    Text("  " + Durations.formatStopwatch(t), color = Muted)
                }
            }
        }
    }
}
