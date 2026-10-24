package dev.pixeldeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pixeldeck.alarm.RingInfo
import dev.pixeldeck.alarm.RingService

/** Big "it is ringing" card with Dismiss and Snooze (alarm) or +1 min (timer). Used on the dashboard and on the lock screen. */
@Composable
fun RingCard(info: RingInfo, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (info.isTimer) Icons.Default.Timer else Icons.Default.Alarm, null, tint = Magenta, modifier = Modifier.size(72.dp))
        Text(info.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(if (info.isTimer) "Time's up" else "Alarm", color = Muted, style = MaterialTheme.typography.titleLarge)
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GhostButton(if (info.isTimer) "+1 min" else "Snooze", { RingService.snooze(ctx) })
            PrimaryButton("Dismiss", { RingService.stop(ctx) })
        }
    }
}
