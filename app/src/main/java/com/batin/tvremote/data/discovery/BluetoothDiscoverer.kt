package com.batin.tvremote.data.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.batin.tvremote.data.model.ConnectionType
import com.batin.tvremote.data.model.TvDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class BluetoothDiscoverer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    val isBluetoothAvailable: Boolean get() = adapter != null

    @SuppressLint("MissingPermission") // Caller verifies BLUETOOTH_SCAN/CONNECT first.
    fun discover(): Flow<TvDevice> = callbackFlow {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            close()
            return@callbackFlow
        }

        runCatching {
            bluetoothAdapter.bondedDevices?.forEach { device -> trySend(device.toTvDevice()) }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val device = intent.getBluetoothDeviceExtraCompat() ?: return
                trySend(device.toTvDevice())
            }
        }
        // BUGFIX: with targetSdk 33+, the platform requires every dynamically registered
        // receiver to explicitly declare RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED; calling
        // the old 2-arg registerReceiver(receiver, filter) throws a SecurityException at
        // runtime on Android 13+ instead of just being deprecated. ACTION_FOUND is a system
        // broadcast (sent by the Bluetooth stack, not another app of ours), so it must be
        // RECEIVER_EXPORTED.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        }
        runCatching { bluetoothAdapter.startDiscovery() }

        awaitClose {
            runCatching { bluetoothAdapter.cancelDiscovery() }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toTvDevice(): TvDevice = TvDevice(
        id = "bt:$address",
        displayName = runCatching { name }.getOrNull() ?: address,
        connectionType = ConnectionType.BLUETOOTH_HID,
        bluetoothAddress = address
    )

    private fun Intent.getBluetoothDeviceExtraCompat(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
