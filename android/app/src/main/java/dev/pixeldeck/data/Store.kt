package dev.pixeldeck.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "pixeldeck")

/** Everything persisted on the phone: settings, macros and the stream deck layout, as JSON. */
class Store(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val kSettings = stringPreferencesKey("settings")
    private val kMacros = stringPreferencesKey("macros")
    private val kDeck = stringPreferencesKey("deck")
    private val kTimers = stringPreferencesKey("timers")
    private val kAlarms = stringPreferencesKey("alarms")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        decode<Settings>(p, kSettings) ?: Settings()
    }

    val macros: Flow<List<Macro>> = context.dataStore.data.map { p ->
        decode<List<Macro>>(p, kMacros) ?: Defaults.macros
    }

    val deck: Flow<List<DeckButton>> = context.dataStore.data.map { p ->
        decode<List<DeckButton>>(p, kDeck) ?: Defaults.deck
    }

    val timers: Flow<List<TimerItem>> = context.dataStore.data.map { p ->
        decode<List<TimerItem>>(p, kTimers) ?: emptyList()
    }

    val alarms: Flow<List<AlarmItem>> = context.dataStore.data.map { p ->
        decode<List<AlarmItem>>(p, kAlarms) ?: emptyList()
    }

    /** Read-modify-write inside one DataStore transaction, so concurrent updates cannot overwrite each other. */
    suspend fun updateTimers(change: (List<TimerItem>) -> List<TimerItem>) {
        context.dataStore.edit { p ->
            val current = decode<List<TimerItem>>(p, kTimers) ?: emptyList()
            p[kTimers] = json.encodeToString(change(current))
        }
    }

    suspend fun updateAlarms(change: (List<AlarmItem>) -> List<AlarmItem>) {
        context.dataStore.edit { p ->
            val current = decode<List<AlarmItem>>(p, kAlarms) ?: emptyList()
            p[kAlarms] = json.encodeToString(change(current))
        }
    }

    suspend fun saveSettings(s: Settings) = context.dataStore.edit { it[kSettings] = json.encodeToString(s) }
    suspend fun saveMacros(m: List<Macro>) = context.dataStore.edit { it[kMacros] = json.encodeToString(m) }
    suspend fun saveDeck(d: List<DeckButton>) = context.dataStore.edit { it[kDeck] = json.encodeToString(d) }

    /** Whole config as one JSON document, for backup. */
    suspend fun exportJson(settings: Settings, macros: List<Macro>, deck: List<DeckButton>): String =
        json.encodeToString(Backup(settings.copy(pcToken = "", haToken = ""), macros, deck))

    suspend fun importJson(text: String, keepSecretsFrom: Settings) {
        val b = json.decodeFromString<Backup>(text)
        saveSettings(b.settings.copy(pcToken = keepSecretsFrom.pcToken, haToken = keepSecretsFrom.haToken))
        saveMacros(b.macros)
        saveDeck(b.deck)
    }

    @kotlinx.serialization.Serializable
    private data class Backup(val settings: Settings, val macros: List<Macro>, val deck: List<DeckButton>)

    private inline fun <reified T> decode(p: Preferences, key: Preferences.Key<String>): T? =
        p[key]?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}

object Defaults {
    val macros = listOf(
        Macro("m_mute", "Mute PC", actions = listOf(MacroAction("media", "mute"))),
        Macro("m_play", "Play / Pause", actions = listOf(MacroAction("media", "play_pause"))),
        Macro("m_next", "Next track", actions = listOf(MacroAction("media", "next"))),
        Macro("m_prev", "Previous track", actions = listOf(MacroAction("media", "prev"))),
        Macro("m_volup", "Volume up", actions = listOf(MacroAction("media", "vol_up"))),
        Macro("m_voldn", "Volume down", actions = listOf(MacroAction("media", "vol_down"))),
        Macro("m_desktop", "Show desktop", actions = listOf(MacroAction("hotkey", "win+d"))),
        Macro("m_taskmgr", "Task Manager", actions = listOf(MacroAction("hotkey", "ctrl+shift+esc"))),
        Macro("m_lock", "Lock PC", actions = listOf(MacroAction("hotkey", "win+l"))),
        Macro("m_snip", "Screenshot", actions = listOf(MacroAction("hotkey", "win+shift+s"))),
    )

    val deck = listOf(
        DeckButton("d_play", "Play", "play", "m_play"),
        DeckButton("d_prev", "Prev", "skip_previous", "m_prev"),
        DeckButton("d_next", "Next", "skip_next", "m_next"),
        DeckButton("d_mute", "Mute", "volume_off", "m_mute"),
        DeckButton("d_volup", "Vol +", "volume_up", "m_volup"),
        DeckButton("d_voldn", "Vol -", "volume_down", "m_voldn"),
        DeckButton("d_desktop", "Desktop", "desktop", "m_desktop"),
        DeckButton("d_taskmgr", "Tasks", "memory", "m_taskmgr"),
        DeckButton("d_snip", "Snip", "camera", "m_snip"),
        DeckButton("d_lock", "Lock", "lock", "m_lock"),
    )
}
