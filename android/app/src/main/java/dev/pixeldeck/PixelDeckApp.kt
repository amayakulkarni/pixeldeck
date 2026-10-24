package dev.pixeldeck

import android.app.Application
import dev.pixeldeck.alarm.Scheduler
import dev.pixeldeck.calendar.CalendarRepo
import dev.pixeldeck.data.Store
import dev.pixeldeck.iot.HaClient
import dev.pixeldeck.iot.IotRepo
import dev.pixeldeck.macro.MacroEngine
import dev.pixeldeck.net.Discovery
import dev.pixeldeck.net.Pairing
import dev.pixeldeck.net.PcClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Hand-wired dependency container (no DI framework). Everything lives as long as the process. */
class PixelDeckApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var store: Store
    lateinit var pc: PcClient
    lateinit var discovery: Discovery
    lateinit var iot: IotRepo
    lateinit var calendar: CalendarRepo
    lateinit var macros: MacroEngine

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = Store(this)
        pc = PcClient(scope)
        discovery = Discovery(this)
        iot = IotRepo(this, scope, discovery, HaClient())
        calendar = CalendarRepo(this, scope)
        macros = MacroEngine(this)

        // AlarmManager can lose entries (reboot, update, force stop), so re-arm from saved state on every start.
        scope.launch { runCatching { Scheduler.rescheduleAll(this@PixelDeckApp) } }

        // Keep the PC link and Home Assistant polling in sync with the saved settings.
        scope.launch {
            store.settings.collectLatest {
                pc.configure(it)
                iot.configureHa(it)
            }
        }
    }

    /** Saves a scanned or pasted pairing as the PC link; the settings collector reconnects automatically. */
    suspend fun applyPairing(p: Pairing) {
        val current = store.settings.first()
        store.saveSettings(current.copy(pcHost = p.host, pcPort = p.port, pcToken = p.token, pcName = p.name))
    }

    companion object {
        lateinit var instance: PixelDeckApp
            private set
    }
}
