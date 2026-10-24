package dev.pixeldeck.notif

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import dev.pixeldeck.data.SOUND_SILENT

/** Plays the dashboard's own notification sound (in addition to the phone's normal one). */
object Chime {
    private var active: Ringtone? = null
    private var lastPlayed = 0L

    /** [setting] is a Settings.*Sound value: "" = default, "silent" = nothing, otherwise a URI. */
    fun play(context: Context, setting: String, type: Int = RingtoneManager.TYPE_NOTIFICATION, minGapMs: Long = 1_500) {
        if (setting == SOUND_SILENT) return
        val now = System.currentTimeMillis()
        if (now - lastPlayed < minGapMs) return
        lastPlayed = now
        val uri: Uri = if (setting.isBlank()) RingtoneManager.getDefaultUri(type) else Uri.parse(setting)
        runCatching {
            active?.stop()
            val tone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
            tone.audioAttributes = AudioAttributes.Builder()
                .setUsage(if (type == RingtoneManager.TYPE_ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            tone.play()
            active = tone
        }
    }

    /** Human readable name of a sound setting, for the settings screen. */
    fun title(context: Context, setting: String): String = when {
        setting.isBlank() -> "Default"
        setting == SOUND_SILENT -> "Silent"
        else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(setting))?.getTitle(context) }.getOrNull() ?: "Custom"
    }
}
