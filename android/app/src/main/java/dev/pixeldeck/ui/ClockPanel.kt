package dev.pixeldeck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun rememberNow(tickMs: Long = 1_000): LocalDateTime {
    val now by produceState(LocalDateTime.now(), tickMs) {
        while (true) {
            value = LocalDateTime.now()
            delay(tickMs)
        }
    }
    return now
}

@Composable
fun ClockPanel(use24h: Boolean, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
    val suffix = if (use24h) "" else now.format(DateTimeFormatter.ofPattern("a"))
    val seconds = now.format(DateTimeFormatter.ofPattern("ss"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    PanelCard("Clock", modifier) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(time, fontSize = 76.sp, fontWeight = FontWeight.Light, color = Color.White, letterSpacing = (-2).sp)
                Column {
                    Text(seconds, fontSize = 22.sp, color = Magenta, fontWeight = FontWeight.Bold)
                    if (suffix.isNotEmpty()) Text(suffix, fontSize = 16.sp, color = Muted)
                }
            }
            Text(date, style = MaterialTheme.typography.titleMedium, color = Muted)
        }
    }
}

/** Dim, burn-in-safe clock shown after the screen has been idle. */
@Composable
fun NightClock(use24h: Boolean, size: TextUnit = 96.sp) {
    val now = rememberNow(tickMs = 10_000)
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm"))
    Text(time, fontSize = size, fontWeight = FontWeight.Light, color = Magenta.copy(alpha = 0.28f))
}
