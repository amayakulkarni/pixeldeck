package dev.pixeldeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.Macro
import dev.pixeldeck.data.MacroAction
import dev.pixeldeck.data.Trigger
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MacrosPanel(modifier: Modifier = Modifier) {
    val app = PixelDeckApp.instance
    val macros by app.store.macros.collectAsStateWithLifecycle(initialValue = emptyList())
    val log by app.macros.log.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Macro?>(null) }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard(
            "Macro hub",
            Modifier.weight(1.6f).fillMaxSize(),
            actions = {
                TextButton(onClick = { editing = Macro(UUID.randomUUID().toString(), "", Trigger(), listOf(MacroAction())) }) { Text("+ New macro") }
            },
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(macros, key = { it.id }) { m ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${m.trigger.describe()}  ·  ${m.actions.size} step${if (m.actions.size == 1) "" else "s"}",
                                    color = Muted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { app.macros.runAsync(m) }) { Icon(Icons.Default.PlayArrow, "Run", tint = Magenta) }
                            IconButton(onClick = { editing = m }) { Icon(Icons.Default.Edit, "Edit", tint = Muted) }
                            IconButton(onClick = { scope.launch { app.store.saveMacros(macros.filterNot { it.id == m.id }) } }) {
                                Icon(Icons.Default.Delete, "Delete", tint = Muted)
                            }
                        }
                    }
                }
            }
        }

        PanelCard("Run log", Modifier.weight(1f).fillMaxSize()) {
            if (log.isEmpty()) Text("Nothing has run yet.", color = Muted)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(log) {
                    Text("${formatTime(it.time)}  ${it.text}", color = if (it.ok) Good else Bad, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    editing?.let { m ->
        MacroEditor(
            initial = m,
            onDismiss = { editing = null },
            onSave = { saved ->
                scope.launch {
                    val updated = if (macros.any { it.id == saved.id }) macros.map { if (it.id == saved.id) saved else it } else macros + saved
                    app.store.saveMacros(updated)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun MacroEditor(initial: Macro, onDismiss: () -> Unit, onSave: (Macro) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var triggerType by remember { mutableStateOf(initial.trigger.type) }
    var triggerArg by remember { mutableStateOf(initial.trigger.arg) }
    var actions by remember { mutableStateOf(initial.actions) }
    val triggerHint = Trigger.TYPES.firstOrNull { it.first == triggerType }?.second.orEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxSize(0.94f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Edit macro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    Text("Trigger", color = Muted)
                    Dropdown(triggerType, Trigger.TYPES.map { it.first }, { triggerType = it; triggerArg = "" })
                    if (triggerHint.isNotBlank()) {
                        OutlinedTextField(triggerArg, { triggerArg = it }, label = { Text(triggerHint) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
                Text("Steps (run in order, stops at first failure)", color = Muted, style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(actions) { i, a ->
                        val hint = MacroAction.TYPES.firstOrNull { it.first == a.type }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = Magenta, fontWeight = FontWeight.Bold)
                            Dropdown(a.type, MacroAction.TYPES.map { it.first }, { t -> actions = actions.toMutableList().also { it[i] = a.copy(type = t, a = "", b = "") } })
                            OutlinedTextField(
                                a.a, { v -> actions = actions.toMutableList().also { it[i] = a.copy(a = v) } },
                                label = { Text(hint?.second.orEmpty()) }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                            if (!hint?.third.isNullOrBlank()) {
                                OutlinedTextField(
                                    a.b, { v -> actions = actions.toMutableList().also { it[i] = a.copy(b = v) } },
                                    label = { Text(hint?.third.orEmpty()) }, singleLine = true, modifier = Modifier.weight(0.7f),
                                )
                            }
                            IconButton(onClick = { actions = actions.toMutableList().also { it.removeAt(i) } }) {
                                Icon(Icons.Default.Close, "Remove step", tint = Muted)
                            }
                        }
                    }
                    item { TextButton(onClick = { actions = actions + MacroAction() }) { Text("+ Add step") } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    PrimaryButton("Save", {
                        onSave(initial.copy(name = name.trim(), trigger = Trigger(triggerType, triggerArg.trim()), actions = actions))
                    })
                }
            }
        }
    }
}
