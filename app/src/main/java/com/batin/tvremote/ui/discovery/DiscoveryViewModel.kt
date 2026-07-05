package com.batin.tvremote.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batin.tvremote.data.discovery.DeviceDiscoveryRepository
import com.batin.tvremote.data.model.ConnectionType
import com.batin.tvremote.data.model.TvDevice
import com.batin.tvremote.data.repository.RemoteControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryRepository: DeviceDiscoveryRepository,
    private val remoteControlRepository: RemoteControlRepository
) : ViewModel() {

    val bluetoothAvailable: Boolean get() = discoveryRepository.bluetoothAvailable

    val networkDevices: StateFlow<List<TvDevice>> = discoveryRepository.networkDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bluetoothDevices: StateFlow<List<TvDevice>> = discoveryRepository.bluetoothDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedDevices: StateFlow<List<TvDevice>> = remoteControlRepository.savedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun connect(device: TvDevice) {
        viewModelScope.launch { remoteControlRepository.beginConnection(device) }
    }

    fun connectManual(hostOrIp: String) {
        if (hostOrIp.isBlank()) return
        connect(
            TvDevice(
                id = "net:manual:$hostOrIp",
                displayName = hostOrIp,
                connectionType = ConnectionType.NETWORK_ATV_PROTOCOL,
                host = hostOrIp.trim()
            )
        )
    }

    fun forget(device: TvDevice) {
        viewModelScope.launch { remoteControlRepository.forgetDevice(device) }
    }
}
