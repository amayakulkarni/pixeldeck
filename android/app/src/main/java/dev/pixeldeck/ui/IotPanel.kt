package dev.pixeldeck.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.HaEntity
import dev.pixeldeck.data.Settings
import kotlinx.coroutines.launch

private val TAPPABLE = setOf(
    "light", "switch", "fan", "input_boolean", "cover", "lock", "scene", "script", "automation", "button", "input_button", "humidifier",
)

private fun HaEntity.isActive() = state in setOf("on", "open", "unlocked", "playing", "heat", "cool", "home")

@Composable
fun IotPanel(settings: Settings, modifier: Modifier = Modifier) {
    val app = PixelDeckApp.instance
    val ctx = LocalContext.current
    val entities by app.iot.entities.collectAsStateWithLifecycle()
    val haError by app.iot.haError.collectAsStateWithLifecycle()
    val lan by app.iot.lan.collectAsStateWithLifecycle()
    val ble by app.iot.ble.collectAsStateWithLifecycle()
    val scanning by app.iot.bleScanning.collectAsStateWithLifecycle()
    val requestPermissions = LocalRequestPermissions.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    // mDNS discovery only runs while this page is open, to save battery.
    DisposableEffect(Unit) {
        app.iot.startLan()
        onDispose { app.iot.stopLan() }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard("Home Assistant", Modifier.weight(1.4f).fillMaxSize()) {
            when {
                !settings.haConfigured -> Text(
                    "Add your Home Assistant URL and a long-lived access token in Settings to control lights, switches, scenes and more.",
                    color = Muted,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    haError?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
                    message?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entities, key = { it.entity_id }) { e ->
                            val tappable = e.domain in TAPPABLE
                            Surface(
                                color = if (e.isActive()) Magenta.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (e.isActive()) Magenta else PanelEdge),
                                modifier = Modifier.fillMaxWidth().clickable(enabled = tappable) {
                                    scope.launch {
                                        message = app.iot.tap(settings, e).exceptionOrNull()?.message
                                    }
                                },
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(e.friendlyName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    val unit = (e.attributes["unit_of_measurement"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                                    Text("${e.state} $unit".trim(), color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelCard("Nearby on Wi-Fi", Modifier.weight(1f).fillMaxWidth()) {
                if (lan.isEmpty()) Text("Searching the network…", color = Muted)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lan, key = { "${it.type}/${it.name}" }) { s ->
                        Column {
                            Text(s.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${s.type.removePrefix("_").substringBefore('.')} · ${s.host}:${s.port}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            PanelCard(
                "Bluetooth LE",
                Modifier.weight(1f).fillMaxWidth(),
                actions = {
                    TextButton(enabled = !scanning, onClick = {
                        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                        if (ok) app.iot.scanBle() else requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN))
                    }) { Text(if (scanning) "Scanning…" else "Scan") }
                },
            ) {
                if (ble.isEmpty()) Text("Tap Scan to look for nearby Bluetooth devices.", color = Muted)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ble, key = { it.address }) { d ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name.ifBlank { "Unnamed device" }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(d.address, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${d.rssi} dBm", color = Muted)
                        }
                    }
                }
            }
        }
    }
}
