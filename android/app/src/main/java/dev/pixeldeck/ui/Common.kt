package dev.pixeldeck.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.text.DateFormat
import java.util.Date

/** Lets any panel ask the activity for runtime permissions. */
val LocalRequestPermissions = staticCompositionLocalOf<(Array<String>) -> Unit> { {} }

/** Privacy policy applied while the dashboard is shown as a screen saver over a locked phone. */
data class LockState(val locked: Boolean, val controls: Boolean, val showDetails: Boolean)

val LocalLock = staticCompositionLocalOf { LockState(locked = false, controls = true, showDetails = true) }

val ICONS: Map<String, ImageVector> = linkedMapOf(
    "bolt" to Icons.Default.Bolt,
    "play" to Icons.Default.PlayArrow,
    "skip_previous" to Icons.Default.SkipPrevious,
    "skip_next" to Icons.Default.SkipNext,
    "volume_off" to Icons.Default.VolumeOff,
    "volume_up" to Icons.Default.VolumeUp,
    "volume_down" to Icons.Default.VolumeDown,
    "desktop" to Icons.Default.DesktopWindows,
    "memory" to Icons.Default.Memory,
    "camera" to Icons.Default.Videocam,
    "lock" to Icons.Default.Lock,
    "terminal" to Icons.Default.Terminal,
    "keyboard" to Icons.Default.Keyboard,
    "web" to Icons.Default.Language,
    "mic" to Icons.Default.Mic,
    "music" to Icons.Default.MusicNote,
    "light" to Icons.Default.Lightbulb,
    "home" to Icons.Default.Home,
)

fun iconFor(name: String): ImageVector = ICONS[name] ?: Icons.Default.Bolt

@Composable
fun PanelCard(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = PanelBg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PanelEdge),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Magenta, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Column(Modifier.padding(top = 10.dp)) { content() }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

/** Small dependency-free dropdown: a button that opens a menu. */
@Composable
fun Dropdown(selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(onClick = { open = true }) { Text(selected) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = { open = false; onSelect(it) })
            }
        }
    }
}

@Composable
fun rememberHasPermission(permission: String): State<Boolean> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED) }
    // Re-check when returning from the system permission dialog or settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.value = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
    }
    return state
}

fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - then) / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 24 * 60 -> "${m / 60}h"
        else -> "${m / (24 * 60)}d"
    }
}

fun formatTime(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
