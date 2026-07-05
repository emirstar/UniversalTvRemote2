package com.batin.tvremote.data.remote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.ContextCompat
import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.data.remote.RemoteTransport
import com.batin.tvremote.data.remote.TransportEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Bluetooth HID fallback used only when a TV cannot be reached (or controlled) over the
 * Android TV Remote Protocol at all. The phone registers itself as a combined
 * keyboard/consumer-control/mouse HID *device* (see HidReportDescriptors) and connects to
 * the TV, which plays the HID *host* role - the exact same role it plays for a physical
 * Bluetooth remote or keyboard. App-launching (YouTube/Play Store/TV+ shortcuts) has no
 * equivalent here, since HID has no concept of "start this app": [launchApp] always
 * returns false so the UI can hide those buttons for this transport.
 */
class BluetoothHidClient @Inject constructor(
    @ApplicationContext private val context: Context
) : RemoteTransport {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var hidProxy: BluetoothHidDevice? = null
    private var target: BluetoothDevice? = null

    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<TransportEvent> = _events

    private var registrationSignal: CompletableDeferred<Boolean>? = null
    private var connectionSignal: CompletableDeferred<Boolean>? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            registrationSignal?.complete(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> connectionSignal?.complete(true)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionSignal?.complete(false)
                    _events.tryEmit(TransportEvent.Disconnected)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) = Unit
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) = Unit
        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) = Unit
        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) = Unit
        override fun onVirtualCableUnplug(device: BluetoothDevice?) = Unit
    }

    @SuppressLint("MissingPermission") // Caller (ViewModel/PermissionUtils) verifies grants first.
    suspend fun connect(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bluetoothAdapter = adapter ?: error("Bu cihazda Bluetooth donanımı bulunmuyor")
            check(bluetoothAdapter.isEnabled) { "Bluetooth kapalı. Lütfen açıp tekrar deneyin." }
            target = bluetoothAdapter.getRemoteDevice(address)

            if (hidProxy == null) {
                val proxySignal = CompletableDeferred<BluetoothHidDevice>()
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (profile == BluetoothProfile.HID_DEVICE) {
                            proxySignal.complete(proxy as BluetoothHidDevice)
                        }
                    }
                    override fun onServiceDisconnected(profile: Int) {
                        hidProxy = null
                    }
                }
                bluetoothAdapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)
                hidProxy = withTimeoutOrNull(PROXY_TIMEOUT_MS) { proxySignal.await() }
                    ?: error("HID cihaz profili başlatılamadı")
            }
            val proxy = hidProxy ?: error("HID cihaz profili başlatılamadı")

            val sdp = BluetoothHidDeviceAppSdpSettings(
                SDP_NAME,
                SDP_DESCRIPTION,
                SDP_PROVIDER,
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReportDescriptors.DESCRIPTOR
            )
            val regSignal = CompletableDeferred<Boolean>()
            registrationSignal = regSignal
            proxy.registerApp(sdp, null, null, ContextCompat.getMainExecutor(context), callback)
            val registered = withTimeoutOrNull(REGISTRATION_TIMEOUT_MS) { regSignal.await() } ?: false
            check(registered) { "Telefon, giriş cihazı olarak kaydolamadı" }

            val connSignal = CompletableDeferred<Boolean>()
            connectionSignal = connSignal
            @Suppress("DEPRECATION")
            val connectStarted = proxy.connect(target)
            check(connectStarted) { "Bağlantı başlatılamadı" }
            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connSignal.await() } ?: false
            check(connected) { "TV bağlantıyı kabul etmedi. TV üzerinde eşleştirme isteğini onaylamanız gerekebilir." }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendKey(key: RemoteKey, kind: KeyPressKind) = withContext(Dispatchers.IO) {
        // Bluetooth HID has no long-press semantics of its own; SHORT is what the UI uses
        // for every button, and long-press is only meaningful for the network transport's
        // START_LONG/END_LONG signalling, so it is simply ignored here.
        when (val mapping = HidKeyMapper.map(key)) {
            is HidKeyMapper.Mapping.Keyboard -> pulseKeyboard(mapping.usage, mapping.shift)
            is HidKeyMapper.Mapping.Consumer -> pulseConsumer(mapping.bit)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        for (character in text) {
            val mapping = HidKeyMapper.charToKeyboardUsage(character) ?: continue
            pulseKeyboard(mapping.usage, mapping.shift)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendPointerMove(dx: Float, dy: Float) = withContext(Dispatchers.IO) {
        val device = target ?: return@withContext
        hidProxy?.sendReport(
            device,
            HidReportDescriptors.MOUSE_REPORT_ID.toInt(),
            HidReportDescriptors.mouseReport(buttons = 0, dx = dx.toInt(), dy = dy.toInt())
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendPointerClick() = withContext(Dispatchers.IO) {
        val device = target ?: return@withContext
        val proxy = hidProxy ?: return@withContext
        proxy.sendReport(device, HidReportDescriptors.MOUSE_REPORT_ID.toInt(), HidReportDescriptors.mouseReport(1, 0, 0))
        proxy.sendReport(device, HidReportDescriptors.MOUSE_REPORT_ID.toInt(), HidReportDescriptors.mouseReport(0, 0, 0))
    }

    /** Bluetooth HID cannot launch apps by package name; the UI hides these buttons when this returns false. */
    override suspend fun launchApp(target: String): Boolean = false

    @SuppressLint("MissingPermission")
    private fun pulseKeyboard(usage: Int, shift: Boolean) {
        val device = target ?: return
        val proxy = hidProxy ?: return
        val modifier = if (shift) HidReportDescriptors.MODIFIER_LEFT_SHIFT else 0
        proxy.sendReport(device, HidReportDescriptors.KEYBOARD_REPORT_ID.toInt(), HidReportDescriptors.keyboardReport(modifier, usage))
        proxy.sendReport(device, HidReportDescriptors.KEYBOARD_REPORT_ID.toInt(), HidReportDescriptors.emptyKeyboardReport())
    }

    @SuppressLint("MissingPermission")
    private fun pulseConsumer(bit: Int) {
        val device = target ?: return
        val proxy = hidProxy ?: return
        proxy.sendReport(device, HidReportDescriptors.CONSUMER_REPORT_ID.toInt(), HidReportDescriptors.consumerReport(bit))
        proxy.sendReport(device, HidReportDescriptors.CONSUMER_REPORT_ID.toInt(), HidReportDescriptors.emptyConsumerReport())
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        runCatching {
            val device = target
            val proxy = hidProxy
            if (device != null && proxy != null) {
                proxy.disconnect(device)
                proxy.unregisterApp()
            }
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
        }
        hidProxy = null
        target = null
    }

    companion object {
        private const val SDP_NAME = "TV Uzaktan Kumanda"
        private const val SDP_DESCRIPTION = "Android TV HID Remote"
        private const val SDP_PROVIDER = "batin"
        private const val PROXY_TIMEOUT_MS = 8_000L
        private const val REGISTRATION_TIMEOUT_MS = 8_000L
        private const val CONNECT_TIMEOUT_MS = 20_000L
    }
}
