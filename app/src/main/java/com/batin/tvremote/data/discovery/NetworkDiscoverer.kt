package com.batin.tvremote.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.batin.tvremote.data.model.ConnectionType
import com.batin.tvremote.data.model.TvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import javax.inject.Inject

/**
 * Discovers Android TV / Google TV devices advertising the Android TV Remote Protocol
 * over mDNS (service type `_androidtvremote2._tcp.`, the same one the official Google TV
 * and Google Home apps look for). [NsdManager.resolveService] is documented to behave
 * unreliably when several resolutions are in flight at once on most Android versions, so
 * found services are resolved one at a time through a small internal queue rather than
 * fired off concurrently.
 */
class NetworkDiscoverer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun discover(): Flow<TvDevice> = callbackFlow {
        // IMPROVEMENT: some devices apply aggressive Wi-Fi power-saving multicast
        // filtering that silently drops the mDNS responses this discovery depends on.
        // Holding a multicast lock while scanning is the standard fix and costs nothing
        // once released.
        val multicastLock = wifiManager.createMulticastLock("tvremote-mdns").apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }

        val pendingQueue = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            val next: NsdServiceInfo?
            synchronized(pendingQueue) {
                if (resolving || pendingQueue.isEmpty()) return
                resolving = true
                next = pendingQueue.removeFirstOrNull()
            }
            val serviceInfo = next ?: run { synchronized(pendingQueue) { resolving = false }; return }

            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    synchronized(pendingQueue) { resolving = false }
                    resolveNext()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val hostAddress = info.host?.hostAddress
                    if (!hostAddress.isNullOrBlank()) {
                        val pairingPort = info.port.takeIf { it > 0 } ?: TvDevice.DEFAULT_PAIRING_PORT
                        trySend(
                            TvDevice(
                                id = "net:${info.serviceName}",
                                displayName = info.serviceName ?: hostAddress,
                                connectionType = ConnectionType.NETWORK_ATV_PROTOCOL,
                                host = hostAddress,
                                pairingPort = pairingPort,
                                remotePort = TvDevice.DEFAULT_REMOTE_PORT
                            )
                        )
                    }
                    synchronized(pendingQueue) { resolving = false }
                    resolveNext()
                }
            })
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(pendingQueue) { pendingQueue.addLast(serviceInfo) }
                resolveNext()
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IOException("mDNS keşfi başlatılamadı (kod=$errorCode)"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
            .onFailure { close(it) }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            runCatching { if (multicastLock.isHeld) multicastLock.release() }
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_androidtvremote2._tcp."
    }
}
