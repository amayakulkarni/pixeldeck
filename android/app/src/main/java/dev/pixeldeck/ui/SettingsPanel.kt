package dev.pixeldeck.ui

import android.Manifest
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.Settings
import dev.pixeldeck.net.Discovery
import dev.pixeldeck.net.Pairing
import dev.pixeldeck.net.PcStatus
import dev.pixeldeck.notif.NotifRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsPanel(settings: Settings, modifier: Modifier = Modifier) {
    val app = PixelDeckApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val requestPermissions = LocalRequestPermissions.current

    var draft by remember(settings) { mutableStateOf(settings) }
    var portText by remember(settings) { mutableStateOf(settings.pcPort.toString()) }
    var pasted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    val status by app.pc.status.collectAsStateWithLifecycle()
    val pcName by app.pc.pcName.collectAsStateWithLifecycle()
    val lastError by app.pc.lastError.collectAsStateWithLifecycle()
    val pcs by remember { app.discovery.discover(Discovery.PC_SERVICE) }.collectAsStateWithLifecycle(initialValue = emptyList())

    val calendarOk by rememberHasPermission(Manifest.permission.READ_CALENDAR)
    val bleOk by rememberHasPermission(Manifest.permission.BLUETOOTH_SCAN)
    val postOk by rememberHasPermission(Manifest.permission.POST_NOTIFICATIONS)
    val listenerOk = NotifRepo.isAccessGranted(ctx)

    fun save() {
        val port = portText.toIntOrNull()?.coerceIn(1, 65535) ?: draft.pcPort
        scope.launch { app.store.saveSettings(draft.copy(pcPort = port)) }
        note = "Saved"
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelCard("PC link and Home Assistant", Modifier.weight(1f).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Scan QR code", {
                        scanPairing(
                            ctx,
                            onResult = { p ->
                                scope.launch { app.applyPairing(p) }
                                note = "Paired with ${p.name.ifBlank { p.host }}"
                            },
                            onMessage = { note = it },
                        )
                    })
                    Text("or paste the pairing string:", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    when (status) {
                        PcStatus.Connected -> "Connected to ${pcName ?: "PC"}"
                        PcStatus.Connecting -> "Connecting…"
                        PcStatus.Rejected -> "Rejected: ${lastError ?: "wrong token"}"
                        PcStatus.NotConfigured -> "Not set up. Run the agent on your PC and paste its pairing string below."
                        PcStatus.Disconnected -> "Offline${lastError?.let { ": $it" } ?: ""}"
                    },
                    color = if (status == PcStatus.Connected) Good else if (status == PcStatus.Rejected) Bad else Muted,
                )
                OutlinedTextField(
                    pasted,
                    {
                        pasted = it
                        Pairing.parse(it)?.let { p ->
                            draft = draft.copy(pcHost = p.host, pcPort = p.port, pcToken = p.token, pcName = p.name)
                            portText = p.port.toString()
                        }
                    },
                    label = { Text("Paste pairing string (pixeldeck://host:port/token)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft.pcHost, { draft = draft.copy(pcHost = it) }, label = { Text("PC host / IP") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(
                        portText, { portText = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    draft.pcToken, { draft = draft.copy(pcToken = it) }, label = { Text("Pairing token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                if (pcs.isNotEmpty()) {
                    Text("PCs found on this network", color = Muted, style = MaterialTheme.typography.labelMedium)
                    pcs.forEach { s ->
                        TextButton(onClick = { draft = draft.copy(pcHost = s.host, pcPort = s.port); portText = s.port.toString() }) {
                            Text("${s.name}  (${s.host}:${s.port}) — use")
                        }
                    }
                }
                Text("Home Assistant", color = Muted, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    draft.haUrl, { draft = draft.copy(haUrl = it) }, label = { Text("URL, e.g. http://homeassistant.local:8123") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    draft.haToken, { draft = draft.copy(haToken = it) }, label = { Text("Long-lived access token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Save", ::save)
                    note?.let { Text(it, color = Good) }
                }
            }
        }

        PanelCard("Display, permissions, backup", Modifier.weight(1f).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Keep screen on", draft.keepAwake) { draft = draft.copy(keepAwake = it) }
                ToggleRow("Night mode after idle (dim clock)", draft.nightMode) { draft = draft.copy(nightMode = it) }
                ToggleRow("Slow pixel shift (burn-in protection)", draft.pixelShift) { draft = draft.copy(pixelShift = it) }
                ToggleRow("24-hour clock", draft.use24h) { draft = draft.copy(use24h = it) }
                Text(
                    "Screen saver: Android Settings > Display > Screen saver > PixelDeck, start while charging.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                ToggleRow("Screen saver only on a wireless charger", draft.dreamWirelessOnly) { draft = draft.copy(dreamWirelessOnly = it) }
                ToggleRow("Locked phone: allow deck taps", draft.lockedControls) { draft = draft.copy(lockedControls = it) }
                ToggleRow("Locked phone: show notification and event details", draft.lockedShowDetails) { draft = draft.copy(lockedShowDetails = it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Idle before night mode: ${draft.dimAfterMinutes} min", Modifier.weight(1f))
                    TextButton(onClick = { draft = draft.copy(dimAfterMinutes = (draft.dimAfterMinutes - 1).coerceAtLeast(1)) }) { Text("−") }
                    TextButton(onClick = { draft = draft.copy(dimAfterMinutes = (draft.dimAfterMinutes + 1).coerceAtMost(60)) }) { Text("+") }
                }
                Text("Sounds and timers", color = Muted, style = MaterialTheme.typography.labelMedium)
                SoundRow("Alarm sound", RingtoneManager.TYPE_ALARM, draft.alarmSound) { draft = draft.copy(alarmSound = it) }
                SoundRow("Timer sound", RingtoneManager.TYPE_NOTIFICATION, draft.timerSound) { draft = draft.copy(timerSound = it) }
                ToggleRow("Dashboard chime for new notifications", draft.notifChime) { draft = draft.copy(notifChime = it) }
                SoundRow("Notification chime", RingtoneManager.TYPE_NOTIFICATION, draft.notifSound) { draft = draft.copy(notifSound = it) }
                Text(
                    "The phone still plays its own notification sounds; the chime is an extra one from the dashboard.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Snooze: ${draft.snoozeMinutes} min", Modifier.weight(1f))
                    TextButton(onClick = { draft = draft.copy(snoozeMinutes = (draft.snoozeMinutes - 1).coerceAtLeast(1)) }) { Text("−") }
                    TextButton(onClick = { draft = draft.copy(snoozeMinutes = (draft.snoozeMinutes + 1).coerceAtMost(60)) }) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stop ringing after ${draft.ringSeconds} s", Modifier.weight(1f))
                    TextButton(onClick = { draft = draft.copy(ringSeconds = (draft.ringSeconds - 10).coerceAtLeast(10)) }) { Text("−") }
                    TextButton(onClick = { draft = draft.copy(ringSeconds = (draft.ringSeconds + 10).coerceAtMost(600)) }) { Text("+") }
                }
                PrimaryButton("Save options", ::save)

                Text("Permissions", color = Muted, style = MaterialTheme.typography.labelMedium)
                PermRow("Calendar", calendarOk) { requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) }
                PermRow("Bluetooth scan", bleOk) { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN)) }
                PermRow("Post notifications", postOk) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                PermRow("Notification access", listenerOk) {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }

                Text("Backup (macros, deck, display options; tokens are not exported)", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Copy to clipboard", {
                        scope.launch {
                            val text = app.store.exportJson(app.store.settings.first(), app.store.macros.first(), app.store.deck.first())
                            clipboard.setText(AnnotatedString(text))
                            note = "Backup copied"
                        }
                    })
                    GhostButton("Restore from clipboard", {
                        scope.launch {
                            runCatching { app.store.importJson(clipboard.getText()?.text.orEmpty(), app.store.settings.first()) }
                                .onSuccess { note = "Backup restored" }
                                .onFailure { note = "Clipboard does not contain a PixelDeck backup" }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (granted) Text("granted", color = Good) else TextButton(onClick = onGrant) { Text("Grant") }
    }
}
