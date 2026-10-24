package dev.pixeldeck.macro

import android.content.Intent
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.alarm.Durations
import dev.pixeldeck.alarm.RingInfo
import dev.pixeldeck.alarm.RingService
import dev.pixeldeck.alarm.Scheduler
import dev.pixeldeck.data.LogLine
import dev.pixeldeck.data.Macro
import dev.pixeldeck.data.MacroAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Runs macros: an ordered list of actions (PC hotkeys, Home Assistant calls, HTTP requests,
 * launching Android apps, delays). Stream deck buttons are macros bound to a tile, and macros
 * can also fire from triggers (notification, charging, time of day).
 */
class MacroEngine(private val app: PixelDeckApp) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log

    private fun log(text: String, ok: Boolean) {
        _log.value = (listOf(LogLine(System.currentTimeMillis(), text, ok)) + _log.value).take(40)
    }

    fun runById(id: String) {
        app.scope.launch {
            val macro = app.store.macros.first().firstOrNull { it.id == id }
            if (macro == null) log("Macro not found", false) else run(macro)
        }
    }

    fun runAsync(macro: Macro) { app.scope.launch { run(macro) } }

    /** Executes all actions in order and stops at the first failure. Returns null on success, else the error. */
    suspend fun run(macro: Macro): String? {
        for ((i, action) in macro.actions.withIndex()) {
            val err = runAction(action)
            if (err != null) {
                log("${macro.name}: step ${i + 1} failed: $err", false)
                return err
            }
        }
        log("${macro.name}: done", true)
        return null
    }

    private suspend fun runAction(a: MacroAction): String? {
        val settings = app.store.settings.first()
        return when (a.type) {
            "hotkey" -> app.pc.hotkey(a.a).errorOrNull()
            "type_text" -> app.pc.typeText(a.a).errorOrNull()
            "media" -> app.pc.media(a.a).errorOrNull()
            "launch" -> app.pc.launch(a.a, a.b).errorOrNull()
            "script" -> app.pc.script(a.a).errorOrNull()
            "ha_service" -> app.iot.callService(settings, a.a, a.b).errorOrNull()
            "delay" -> { delay(a.a.toLongOrNull()?.coerceIn(0, 60_000) ?: 0); null }
            "open_app" -> openApp(a.a)
            "start_timer" -> {
                val ms = Durations.parse(a.a)
                if (ms == null) "'${a.a}' is not a duration (try 10m or 1h30m)"
                else { Scheduler.startTimer(app, ms, a.b); null }
            }
            "stop_ring" -> { RingService.stop(app); null }
            "http" -> httpCall(a.a, a.b)
            else -> "unknown action type '${a.type}'"
        }
    }

    private fun openApp(pkg: String): String? {
        val intent = app.packageManager.getLaunchIntentForPackage(pkg) ?: return "app '$pkg' is not installed"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { app.startActivity(intent) }.exceptionOrNull()?.message
    }

    private suspend fun httpCall(url: String, method: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url)
            if (method.equals("POST", ignoreCase = true)) builder.post("".toRequestBody("text/plain".toMediaType()))
            http.newCall(builder.build()).execute().use { if (!it.isSuccessful) error("HTTP ${it.code}") }
        }.exceptionOrNull()?.message
    }

    // ---- triggers ----

    private fun fire(type: String, matches: (Macro) -> Boolean) {
        app.scope.launch {
            app.store.macros.first()
                .filter { it.trigger.type == type && matches(it) }
                .forEach { run(it) }
        }
    }

    fun onNotification(pkg: String) =
        fire("notification") { it.trigger.arg == "*" || it.trigger.arg.isBlank() || it.trigger.arg == pkg }

    fun onCharging(plugged: Boolean) = fire(if (plugged) "charging" else "unplugged") { true }

    fun onTime(hhmm: String) = fire("time") { it.trigger.arg.trim() == hhmm }

    /** An alarm started ringing or a timer finished. [kind] is RingInfo.KIND_ALARM or KIND_TIMER. */
    fun onClockEvent(kind: String, label: String) =
        fire(if (kind == RingInfo.KIND_TIMER) "timer_done" else "alarm_ring") {
            val arg = it.trigger.arg.trim()
            arg.isEmpty() || arg == "*" || arg.equals(label.trim(), ignoreCase = true)
        }

    private fun Result<*>.errorOrNull(): String? = exceptionOrNull()?.message
}
