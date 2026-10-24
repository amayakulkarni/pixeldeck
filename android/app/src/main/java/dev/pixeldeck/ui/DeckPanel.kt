package dev.pixeldeck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.DeckButton
import dev.pixeldeck.net.PcStatus
import kotlinx.coroutines.launch
import java.util.UUID

/** The stream deck: a grid of tiles, each bound to a macro. */
@Composable
fun DeckPanel(columns: Int, modifier: Modifier = Modifier, showLog: Boolean = false) {
    val app = PixelDeckApp.instance
    val deck by app.store.deck.collectAsStateWithLifecycle(initialValue = emptyList())
    val macros by app.store.macros.collectAsStateWithLifecycle(initialValue = emptyList())
    val status by app.pc.status.collectAsStateWithLifecycle()
    val log by app.macros.log.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lock = LocalLock.current
    // Over a locked phone, anyone can touch the screen: tiles only work if the user allowed it.
    val blocked = lock.locked && !lock.controls

    var editMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DeckButton?>(null) }

    PanelCard(
        "Stream deck",
        modifier,
        actions = {
            Text(
                when (status) {
                    PcStatus.Connected -> "PC connected"
                    PcStatus.Connecting -> "connecting…"
                    PcStatus.Rejected -> "wrong token"
                    PcStatus.NotConfigured -> "PC not set up"
                    PcStatus.Disconnected -> "PC offline"
                },
                color = if (status == PcStatus.Connected) Good else Muted,
                style = MaterialTheme.typography.labelMedium,
            )
            if (!lock.locked) TextButton(onClick = { editMode = !editMode }) { Text(if (editMode) "Done" else "Edit") }
            else if (blocked) Text("  unlock to use", color = Muted, style = MaterialTheme.typography.labelMedium)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(deck, key = { it.id }) { b ->
                    DeckTile(
                        b, editMode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (blocked) Unit else if (editMode) editing = b else app.macros.runById(b.macroId)
                        },
                    )
                }
                if (editMode) {
                    item {
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Magenta),
                            modifier = Modifier.aspectRatio(1.15f).clickable {
                                editing = DeckButton(UUID.randomUUID().toString(), "", "bolt", macros.firstOrNull()?.id ?: "")
                            },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "Add button", tint = Magenta)
                            }
                        }
                    }
                }
            }
            if (showLog) {
                log.firstOrNull()?.let {
                    Text(
                        "Last: ${it.text}",
                        color = if (it.ok) Good else Bad,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    editing?.let { b ->
        DeckButtonDialog(
            initial = b,
            isNew = deck.none { it.id == b.id },
            macros = macros.map { it.id to it.name },
            onDismiss = { editing = null },
            onSave = { saved ->
                scope.launch {
                    val updated = if (deck.any { it.id == saved.id }) deck.map { if (it.id == saved.id) saved else it } else deck + saved
                    app.store.saveDeck(updated)
                }
                editing = null
            },
            onDelete = {
                scope.launch { app.store.saveDeck(deck.filterNot { it.id == b.id }) }
                editing = null
            },
        )
    }
}

@Composable
private fun DeckTile(b: DeckButton, editMode: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (editMode) Magenta else PanelEdge),
        modifier = Modifier.aspectRatio(1.15f).clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(iconFor(b.icon), contentDescription = null, tint = Magenta, modifier = Modifier.size(30.dp))
            Text(b.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DeckButtonDialog(
    initial: DeckButton,
    isNew: Boolean,
    macros: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (DeckButton) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember { mutableStateOf(initial.label) }
    var icon by remember { mutableStateOf(initial.icon) }
    var macroId by remember { mutableStateOf(initial.macroId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New button" else "Edit button") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true)
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = Muted)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ICONS.keys.toList()) { name ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (name == icon) Magenta else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { icon = name },
                        ) { Icon(iconFor(name), null, Modifier.padding(8.dp).size(22.dp), tint = Color.White) }
                    }
                }
                Text("Runs macro", style = MaterialTheme.typography.labelMedium, color = Muted)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(macros) { (id, name) ->
                        Row(Modifier.fillMaxWidth().clickable { macroId = id }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = id == macroId, onClick = { macroId = id })
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && macroId.isNotBlank(),
                onClick = { onSave(initial.copy(label = label.trim(), icon = icon, macroId = macroId)) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Bad) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
