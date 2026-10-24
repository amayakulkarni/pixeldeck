package dev.pixeldeck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.Panels
import dev.pixeldeck.data.Settings
import kotlinx.coroutines.launch

/** Pages that can be hidden from the navigation rail. Home, Layout and Settings always stay. */
val HIDEABLE_PAGES = listOf(Page.Timers, Page.Deck, Page.Devices, Page.Macros)

/** Layout organizer: choose which panels the Home screen shows, and where. Changes apply immediately. */
@Composable
fun LayoutPanel(settings: Settings, modifier: Modifier = Modifier) {
    val app = PixelDeckApp.instance
    val scope = rememberCoroutineScope()
    val layout = Panels.sanitize(settings.homeLayout)
    val hidden = Panels.hidden(layout)

    fun save(change: Settings) { scope.launch { app.store.saveSettings(change) } }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard("Home screen panels", Modifier.weight(2f).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Three columns, left to right. Use the arrows to move a panel, the cross to hide it. Leave a column empty to give the others more room.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    layout.forEachIndexed { c, keys ->
                        Surface(
                            Modifier.weight(1f).fillMaxHeight(),
                            color = androidx.compose.ui.graphics.Color(0xFF151515),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PanelEdge),
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Column ${c + 1}", color = Muted, style = MaterialTheme.typography.labelSmall)
                                if (keys.isEmpty()) Text("empty", color = Muted)
                                keys.forEach { key ->
                                    PanelChip(
                                        key,
                                        canLeft = c > 0, canRight = c < Panels.COLUMNS - 1,
                                        canUp = keys.indexOf(key) > 0, canDown = keys.indexOf(key) < keys.size - 1,
                                        onMove = { dx, dy -> save(settings.copy(homeLayout = Panels.move(layout, key, dx, dy))) },
                                        onHide = { save(settings.copy(homeLayout = Panels.setVisible(layout, key, false))) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (hidden.isNotEmpty()) {
                    Text("Hidden", color = Muted, style = MaterialTheme.typography.labelMedium)
                    hidden.forEach { key ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(Panels.label(key), Modifier.weight(1f))
                            TextButton(onClick = { save(settings.copy(homeLayout = Panels.setVisible(layout, key, true))) }) { Text("Show") }
                        }
                    }
                }
                TextButton(onClick = { save(settings.copy(homeLayout = Panels.DEFAULT_LAYOUT, deckColumns = 3)) }) { Text("Reset to default layout") }
            }
        }

        PanelCard("Pages and stream deck", Modifier.weight(1f).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pages in the side bar", color = Muted, style = MaterialTheme.typography.labelMedium)
                HIDEABLE_PAGES.forEach { page ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(page.label, Modifier.weight(1f))
                        Switch(page.name !in settings.hiddenPages, { on ->
                            val hiddenNow = if (on) settings.hiddenPages - page.name else (settings.hiddenPages + page.name).distinct()
                            save(settings.copy(hiddenPages = hiddenNow))
                        })
                    }
                }
                Text("Home and Settings are always available.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stream deck columns on Home: ${settings.deckColumns}", Modifier.weight(1f))
                    TextButton(onClick = { save(settings.copy(deckColumns = (settings.deckColumns - 1).coerceAtLeast(1))) }) { Text("−") }
                    TextButton(onClick = { save(settings.copy(deckColumns = (settings.deckColumns + 1).coerceAtMost(6))) }) { Text("+") }
                }
            }
        }
    }
}

@Composable
private fun PanelChip(
    key: String,
    canLeft: Boolean, canRight: Boolean, canUp: Boolean, canDown: Boolean,
    onMove: (dx: Int, dy: Int) -> Unit,
    onHide: () -> Unit,
) {
    Surface(color = PanelBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Magenta)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(Panels.label(key), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniIcon(Icons.AutoMirrored.Filled.ArrowBack, "Move left", canLeft) { onMove(-1, 0) }
                MiniIcon(Icons.Default.ArrowUpward, "Move up", canUp) { onMove(0, -1) }
                MiniIcon(Icons.Default.ArrowDownward, "Move down", canDown) { onMove(0, 1) }
                MiniIcon(Icons.AutoMirrored.Filled.ArrowForward, "Move right", canRight) { onMove(1, 0) }
                MiniIcon(Icons.Default.Close, "Hide", true, onHide)
            }
        }
    }
}

@Composable
private fun MiniIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}
