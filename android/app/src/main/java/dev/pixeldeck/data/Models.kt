package dev.pixeldeck.data

import kotlinx.serialization.Serializable

/** One step of a macro. `type` selects behaviour, `a` and `b` are its arguments. */
@Serializable
data class MacroAction(
    val type: String = "hotkey",
    val a: String = "",
    val b: String = "",
) {
    fun describe(): String = when (type) {
        "hotkey" -> "PC hotkey: $a"
        "type_text" -> "PC type: $a"
        "media" -> "PC media: $a"
        "launch" -> "PC launch: $a"
        "script" -> "PC script: $a"
        "http" -> "HTTP ${b.ifBlank { "GET" }} $a"
        "ha_service" -> "Home Assistant: $a $b"
        "open_app" -> "Open app: $a"
        "start_timer" -> "Start timer: $a${if (b.isBlank()) "" else " ($b)"}"
        "stop_ring" -> "Silence ringing alarm or timer"
        "delay" -> "Wait ${a}ms"
        else -> "$type $a"
    }

    companion object {
        /** Action types shown in the macro editor, with a hint for what `a` and `b` mean. */
        val TYPES: List<Triple<String, String, String>> = listOf(
            Triple("hotkey", "keys, e.g. ctrl+shift+esc", ""),
            Triple("type_text", "text to type", ""),
            Triple("media", "play_pause | next | prev | stop | vol_up | vol_down | mute", ""),
            Triple("launch", "program, file or URL", "args (optional)"),
            Triple("script", "script name from agent config.toml", ""),
            Triple("http", "URL", "method (GET/POST)"),
            Triple("ha_service", "domain.service, e.g. light.toggle", "entity_id"),
            Triple("open_app", "Android package name", ""),
            Triple("start_timer", "duration, e.g. 10m, 1h30m, 90s", "label (optional)"),
            Triple("stop_ring", "", ""),
            Triple("delay", "milliseconds", ""),
        )
    }
}

/** type: manual | notification | charging | unplugged | time | timer_done | alarm_ring */
@Serializable
data class Trigger(
    val type: String = "manual",
    val arg: String = "",
) {
    fun describe(): String = when (type) {
        "notification" -> "On notification from ${arg.ifBlank { "any app" }}"
        "charging" -> "When charger connected"
        "unplugged" -> "When charger removed"
        "time" -> "Daily at $arg"
        "timer_done" -> "When timer ${arg.ifBlank { "(any)" }} finishes"
        "alarm_ring" -> "When alarm ${arg.ifBlank { "(any)" }} rings"
        else -> "Manual"
    }

    companion object {
        val TYPES = listOf(
            "manual" to "",
            "notification" to "package name, or * for any",
            "charging" to "",
            "unplugged" to "",
            "time" to "HH:mm (24h)",
            "timer_done" to "timer label, or * for any",
            "alarm_ring" to "alarm label, or * for any",
        )
    }
}

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val trigger: Trigger = Trigger(),
    val actions: List<MacroAction> = emptyList(),
)

@Serializable
data class DeckButton(
    val id: String,
    val label: String,
    val icon: String = "bolt",
    val macroId: String,
)

/** Value stored in a sound setting when the user picked "Silent". An empty string means the system default. */
const val SOUND_SILENT = "silent"

@Serializable
data class Settings(
    val pcHost: String = "",
    val pcPort: Int = 47800,
    val pcToken: String = "",
    /** Friendly name of the paired PC, filled in from the pairing QR code. */
    val pcName: String = "",
    val haUrl: String = "",
    val haToken: String = "",
    val keepAwake: Boolean = true,
    val nightMode: Boolean = true,
    val dimAfterMinutes: Int = 5,
    val pixelShift: Boolean = true,
    val use24h: Boolean = false,
    /** Screen saver mode: end immediately unless the phone is on a wireless charger. */
    val dreamWirelessOnly: Boolean = true,
    /** While the phone is locked: allow tapping deck tiles to run macros. */
    val lockedControls: Boolean = false,
    /** While the phone is locked: show notification text and calendar event titles. */
    val lockedShowDetails: Boolean = false,
    /** Home screen columns (left to right), each a top-to-bottom list of panel keys. Panels not listed are hidden. */
    val homeLayout: List<List<String>> = Panels.DEFAULT_LAYOUT,
    /** Number of stream deck columns on the Home screen. */
    val deckColumns: Int = 3,
    /** Names of [dev.pixeldeck.ui.Page] entries hidden from the navigation rail. */
    val hiddenPages: List<String> = emptyList(),
    /** Ringtone URI for alarms; "" = system default alarm sound, "silent" = none. */
    val alarmSound: String = "",
    val timerSound: String = "",
    val notifSound: String = "",
    /** Play [notifSound] when a new notification arrives on the dashboard. */
    val notifChime: Boolean = false,
    val snoozeMinutes: Int = 10,
    /** How long an alarm or timer rings before it stops by itself. */
    val ringSeconds: Int = 60,
) {
    val pcConfigured get() = pcHost.isNotBlank() && pcToken.isNotBlank()
    val haConfigured get() = haUrl.isNotBlank() && haToken.isNotBlank()
}

data class DashNotification(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val time: Long,
)

data class CalEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val color: Int,
)

data class FoundService(
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
)

data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

@Serializable
data class HaEntity(
    val entity_id: String,
    val state: String,
    val attributes: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
) {
    val domain: String get() = entity_id.substringBefore('.')
    val friendlyName: String
        get() = (attributes["friendly_name"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: entity_id
}

data class LogLine(val time: Long, val text: String, val ok: Boolean)
