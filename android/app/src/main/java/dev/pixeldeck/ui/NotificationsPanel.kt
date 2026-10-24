package dev.pixeldeck.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.data.DashNotification
import dev.pixeldeck.notif.NotifRepo

@Composable
fun NotificationsPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val items by NotifRepo.items.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(NotifRepo.isAccessGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = NotifRepo.isAccessGranted(ctx) }

    PanelCard(
        "Notifications",
        modifier,
        actions = { if (granted && items.isNotEmpty()) TextButton(onClick = { NotifRepo.dismissAll() }) { Text("Clear all") } },
    ) {
        when {
            !granted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Grant notification access so PixelDeck can show what arrives on this phone. " +
                        "If the switch is greyed out, open App info > ⋮ > Allow restricted settings first.",
                    color = Muted,
                )
                PrimaryButton("Open notification access settings", {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
            items.isEmpty() -> Text("All clear.", color = Muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.key }) { NotifRow(it, LocalLock.current) }
            }
        }
    }
}

@Composable
private fun NotifRow(n: DashNotification, lock: LockState) {
    val hide = lock.locked && !lock.showDetails
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !lock.locked) { NotifRepo.open(n.key) },
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${n.appName} · ${relativeTime(n.time)}", style = MaterialTheme.typography.labelSmall, color = Magenta)
                if (hide) Text("New notification", color = Muted)
                else if (n.title.isNotBlank()) Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (!hide && n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
            }
            IconButton(onClick = { NotifRepo.dismiss(n.key) }) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Muted)
            }
        }
    }
}
