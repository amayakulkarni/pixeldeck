package dev.pixeldeck.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import dev.pixeldeck.data.SOUND_SILENT
import dev.pixeldeck.notif.Chime

/**
 * One row of the sound settings: label, current choice, Choose (system ringtone picker) and Test.
 * [type] is RingtoneManager.TYPE_ALARM or TYPE_NOTIFICATION. [value] "" means the system default.
 */
@Composable
fun SoundRow(label: String, type: Int, value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onChange(picked?.toString() ?: SOUND_SILENT)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(Chime.title(ctx, value), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, label)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            if (value.isNotBlank() && value != SOUND_SILENT) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(value))
            }
            launcher.launch(intent)
        }) { Text("Choose") }
        TextButton(onClick = { Chime.play(ctx, value, type, minGapMs = 0) }) { Text("Test") }
    }
}
