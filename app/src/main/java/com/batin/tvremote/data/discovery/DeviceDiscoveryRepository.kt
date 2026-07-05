package com.batin.tvremote.data.discovery

import com.batin.tvremote.data.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDiscoveryRepository @Inject constructor(
    private val networkDiscoverer: NetworkDiscoverer,
    private val bluetoothDiscoverer: BluetoothDiscoverer
) {
    val bluetoothAvailable: Boolean get() = bluetoothDiscoverer.isBluetoothAvailable

    fun networkDevices(): Flow<List<TvDevice>> =
        networkDiscoverer.discover().scan(emptyList()) { acc, device ->
            if (acc.any { it.id == device.id }) acc else acc + device
        }

    fun bluetoothDevices(): Flow<List<TvDevice>> =
        bluetoothDiscoverer.discover().scan(emptyList()) { acc, device ->
            val existingIndex = acc.indexOfFirst { it.id == device.id }
            if (existingIndex == -1) acc + device else acc.toMutableList().apply { set(existingIndex, device) }
        }
}
