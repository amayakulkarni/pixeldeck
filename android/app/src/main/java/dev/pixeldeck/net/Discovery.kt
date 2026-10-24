package dev.pixeldeck.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dev.pixeldeck.data.FoundService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** mDNS / DNS-SD discovery through Android's NsdManager. */
@Suppress("DEPRECATION")
class Discovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    // NsdManager can only resolve one service at a time, so resolves are serialised.
    private val resolveLock = Mutex()

    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = resolveLock.withLock {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(serviceInfo)
                }
            })
        }
    }

    /** Emits the current list of services of [type] (e.g. "_pixeldeck._tcp") for as long as it is collected. */
    fun discover(type: String): Flow<List<FoundService>> = callbackFlow {
        val found = LinkedHashMap<String, FoundService>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                launch {
                    val r = resolve(serviceInfo) ?: return@launch
                    val host = r.host?.hostAddress ?: return@launch
                    found[r.serviceName] = FoundService(r.serviceName, type, host, r.port)
                    trySend(found.values.toList())
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                trySend(found.values.toList())
            }
        }
        trySend(emptyList())
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    companion object {
        const val PC_SERVICE = "_pixeldeck._tcp"

        /** Service types worth listing as "nearby devices" on a typical home network. */
        val IOT_TYPES = listOf(
            "_googlecast._tcp",
            "_hap._tcp",
            "_esphomelib._tcp",
            "_hue._tcp",
            "_airplay._tcp",
            "_spotify-connect._tcp",
            "_ipp._tcp",
            "_http._tcp",
        )
    }
}
