package com.batin.tvremote.data.repository

import com.batin.tvremote.data.local.db.TvDeviceDao
import com.batin.tvremote.data.local.db.toDomain
import com.batin.tvremote.data.local.db.toEntity
import com.batin.tvremote.data.model.ConnectionState
import com.batin.tvremote.data.model.ConnectionType
import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.data.model.TvDevice
import com.batin.tvremote.data.remote.RemoteTransport
import com.batin.tvremote.data.remote.TransportEvent
import com.batin.tvremote.data.remote.atv.AtvPairingClient
import com.batin.tvremote.data.remote.atv.AtvPairingSession
import com.batin.tvremote.data.remote.atv.AtvRemoteClient
import com.batin.tvremote.data.remote.bluetooth.BluetoothHidClient
import com.batin.tvremote.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RemoteControlRepositoryImpl @Inject constructor(
    private val deviceDao: TvDeviceDao,
    private val pairingClient: AtvPairingClient,
    private val atvClientProvider: Provider<AtvRemoteClient>,
    private val bluetoothClientProvider: Provider<BluetoothHidClient>,
    @ApplicationScope private val externalScope: CoroutineScope
) : RemoteControlRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val savedDevices: Flow<List<TvDevice>> =
        deviceDao.observeAll().map { list -> list.map { it.toDomain() } }

    private var activeTransport: RemoteTransport? = null
    private var pendingSession: AtvPairingSession? = null
    private var pendingDevice: TvDevice? = null
    private var eventsJob: Job? = null

    override suspend fun tryAutoConnect(): Boolean {
        val entity = deviceDao.findAutoConnectCandidate() ?: return false
        return runCatching { connectDirectly(entity.toDomain()) }.isSuccess &&
            _connectionState.value is ConnectionState.Connected
    }

    override suspend fun beginConnection(device: TvDevice) {
        when (device.connectionType) {
            ConnectionType.NETWORK_ATV_PROTOCOL -> {
                if (device.isPaired && device.pairedServerCertFingerprint != null) {
                    connectDirectly(device)
                } else {
                    startPairing(device)
                }
            }
            ConnectionType.BLUETOOTH_HID -> connectDirectly(device)
        }
    }

    private suspend fun startPairing(device: TvDevice) {
        val host = device.host
        if (host == null) {
            _connectionState.value = ConnectionState.Error("Cihazın ağ adresi bilinmiyor")
            return
        }
        _connectionState.value = ConnectionState.AwaitingPairingCode(device)
        pendingDevice = device
        runCatching { pairingClient.beginPairing(host, device.pairingPort) }
            .onSuccess { pendingSession = it }
            .onFailure { e ->
                _connectionState.value =
                    ConnectionState.Error(e.message ?: "Eşleştirme başlatılamadı, TV'nin açık ve aynı ağda olduğundan emin olun")
            }
    }

    override suspend fun submitPairingCode(code: String) {
        val session = pendingSession
        val device = pendingDevice
        if (session == null || device == null) {
            _connectionState.value = ConnectionState.Error("Eşleştirme oturumu bulunamadı, tekrar deneyin")
            return
        }
        _connectionState.value = ConnectionState.Pairing(device)
        val outcome = session.submitCode(code)
        pendingSession = null
        outcome.onSuccess { fingerprint ->
            val paired = device.copy(isPaired = true, pairedServerCertFingerprint = fingerprint)
            deviceDao.upsert(paired.toEntity())
            connectDirectly(paired)
        }.onFailure { e ->
            _connectionState.value = ConnectionState.Error(e.message ?: "Eşleştirme başarısız oldu", retryable = true)
        }
    }

    private suspend fun connectDirectly(device: TvDevice) {
        _connectionState.value = ConnectionState.Connecting(device, device.connectionType)

        val transport: RemoteTransport
        val result: Result<Unit>
        when (device.connectionType) {
            ConnectionType.NETWORK_ATV_PROTOCOL -> {
                val host = device.host
                if (host == null) {
                    _connectionState.value = ConnectionState.Error("Cihazın ağ adresi bilinmiyor")
                    return
                }
                val client = atvClientProvider.get()
                transport = client
                result = client.connect(host, device.remotePort, device.pairedServerCertFingerprint)
            }
            ConnectionType.BLUETOOTH_HID -> {
                val address = device.bluetoothAddress
                if (address == null) {
                    _connectionState.value = ConnectionState.Error("Cihazın Bluetooth adresi bilinmiyor")
                    return
                }
                val client = bluetoothClientProvider.get()
                transport = client
                result = client.connect(address)
            }
        }

        result.onSuccess {
            activeTransport?.disconnect()
            activeTransport = transport
            observeTransportEvents(transport)
            _connectionState.value = ConnectionState.Connected(device, device.connectionType)
            deviceDao.clearAutoConnectFlagOnAll()
            deviceDao.upsert(
                device.copy(
                    isPaired = true,
                    autoConnect = true,
                    lastConnectedAtMillis = System.currentTimeMillis()
                ).toEntity()
            )
        }.onFailure { e ->
            _connectionState.value = ConnectionState.Error(e.message ?: "Bağlantı kurulamadı")
        }
    }

    private fun observeTransportEvents(transport: RemoteTransport) {
        eventsJob?.cancel()
        eventsJob = externalScope.launch {
            transport.events.collect { event ->
                when (event) {
                    is TransportEvent.CurrentAppChanged -> {
                        val current = _connectionState.value
                        if (current is ConnectionState.Connected) {
                            _connectionState.value = current.copy(currentAppPackage = event.packageName)
                        }
                    }
                    TransportEvent.Disconnected -> {
                        _connectionState.value = ConnectionState.Error("Bağlantı kesildi", retryable = true)
                    }
                    is TransportEvent.VolumeChanged -> Unit
                }
            }
        }
    }

    override suspend fun cancelPairingOrConnection() {
        pendingSession?.close()
        pendingSession = null
        pendingDevice = null
        _connectionState.value = ConnectionState.Idle
    }

    override suspend fun forgetDevice(device: TvDevice) {
        deviceDao.delete(device.id)
        val current = _connectionState.value
        if (current is ConnectionState.Connected && current.device.id == device.id) {
            disconnect()
        }
    }

    override suspend fun sendKey(key: RemoteKey, kind: KeyPressKind) {
        activeTransport?.sendKey(key, kind)
    }

    override suspend fun sendText(text: String) {
        activeTransport?.sendText(text)
    }

    override suspend fun sendPointerMove(dx: Float, dy: Float) {
        activeTransport?.sendPointerMove(dx, dy)
    }

    override suspend fun sendPointerClick() {
        activeTransport?.sendPointerClick()
    }

    override suspend fun launchApp(target: String): Boolean =
        activeTransport?.launchApp(target) ?: false

    override fun disconnect() {
        eventsJob?.cancel()
        activeTransport?.disconnect()
        activeTransport = null
        _connectionState.value = ConnectionState.Idle
    }
}
