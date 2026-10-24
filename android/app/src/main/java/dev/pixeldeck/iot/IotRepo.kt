package dev.pixeldeck.iot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import dev.pixeldeck.data.BleDevice
import dev.pixeldeck.data.FoundService
import dev.pixeldeck.data.HaEntity
import dev.pixeldeck.data.Settings
import dev.pixeldeck.net.Discovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Nearby device hub. Three sources:
 *  - LAN services found through mDNS (Chromecast, HomeKit, ESPHome, Hue, printers...)
 *  - Bluetooth LE advertisers, scanned on demand to save battery
 *  - Home Assistant entities, polled over REST, which is how devices actually get controlled
 */
class IotRepo(
    private val context: Context,
    private val scope: CoroutineScope,
    private val discovery: Discovery,
    private val ha: HaClient,
) {
    private val _lan = MutableStateFlow<List<FoundService>>(emptyList())
    val lan: StateFlow<List<FoundService>> = _lan

    private val _ble = MutableStateFlow<List<BleDevice>>(emptyList())
    val ble: StateFlow<List<BleDevice>> = _ble
    private val _bleScanning = MutableStateFlow(false)
    val bleScanning: StateFlow<Boolean> = _bleScanning

    private val _entities = MutableStateFlow<List<HaEntity>>(emptyList())
    val entities: StateFlow<List<HaEntity>> = _entities
    private val _haError = MutableStateFlow<String?>(null)
    val haError: StateFlow<String?> = _haError

    private val lanJobs = mutableListOf<Job>()
    private var haJob: Job? = null
    private var haKey: Pair<String, String>? = null

    /** Start mDNS discovery for all IoT service types. Safe to call repeatedly. */
    @Synchronized
    fun startLan() {
        if (lanJobs.isNotEmpty()) return
        val perType = HashMap<String, List<FoundService>>()
        for (type in Discovery.IOT_TYPES) {
            lanJobs += scope.launch {
                runCatching {
                    discovery.discover(type).collect { list ->
                        synchronized(perType) {
                            perType[type] = list
                            _lan.value = perType.values.flatten().sortedBy { it.name.lowercase() }
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun stopLan() {
        lanJobs.forEach { it.cancel() }
        lanJobs.clear()
    }

    /** Needs the BLUETOOTH_SCAN runtime permission; the UI asks for it first. */
    @SuppressLint("MissingPermission")
    fun scanBle(durationMs: Long = 8_000) {
        if (_bleScanning.value) return
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        _bleScanning.value = true
        val seen = HashMap<String, BleDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName.orEmpty()
                seen[result.device.address] = BleDevice(result.device.address, name, result.rssi)
                _ble.value = seen.values.sortedByDescending { it.rssi }
            }
        }
        runCatching { scanner.startScan(callback) }.onFailure { _bleScanning.value = false; return }
        scope.launch {
            delay(durationMs)
            runCatching { scanner.stopScan(callback) }
            _bleScanning.value = false
        }
    }

    /** (Re)start Home Assistant polling whenever the URL/token change. */
    @Synchronized
    fun configureHa(s: Settings) {
        val key = s.haUrl to s.haToken
        if (!s.haConfigured) {
            haJob?.cancel(); haJob = null; haKey = null
            _entities.value = emptyList(); _haError.value = null
            return
        }
        if (key == haKey && haJob?.isActive == true) return
        haKey = key
        haJob?.cancel()
        haJob = scope.launch {
            while (isActive) {
                refreshHa(s)
                delay(10_000)
            }
        }
    }

    suspend fun refreshHa(s: Settings) {
        ha.states(s.haUrl, s.haToken)
            .onSuccess { list ->
                _entities.value = list
                    .filter { it.domain in HaClient.CONTROLLABLE }
                    .sortedWith(compareBy({ it.domain }, { it.friendlyName.lowercase() }))
                _haError.value = null
            }
            .onFailure { _haError.value = it.message ?: "Home Assistant unreachable" }
    }

    suspend fun tap(s: Settings, entity: HaEntity): Result<Unit> {
        val (domain, service) = ha.tapService(entity) ?: return Result.failure(IllegalStateException("${entity.domain} has no tap action"))
        val r = ha.call(s.haUrl, s.haToken, domain, service, entity.entity_id)
        if (r.isSuccess) { delay(400); refreshHa(s) }
        return r
    }

    suspend fun callService(s: Settings, domainService: String, entityId: String): Result<Unit> {
        if (!s.haConfigured) return Result.failure(IllegalStateException("Home Assistant is not configured"))
        val domain = domainService.substringBefore('.', "")
        val service = domainService.substringAfter('.', "")
        if (domain.isBlank() || service.isBlank()) return Result.failure(IllegalArgumentException("Use domain.service, e.g. light.toggle"))
        return ha.call(s.haUrl, s.haToken, domain, service, entityId.ifBlank { null })
    }
}
