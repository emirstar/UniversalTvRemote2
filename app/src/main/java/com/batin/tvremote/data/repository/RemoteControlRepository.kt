package com.batin.tvremote.data.repository

import com.batin.tvremote.data.model.ConnectionState
import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.data.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RemoteControlRepository {

    val connectionState: StateFlow<ConnectionState>
    val savedDevices: Flow<List<TvDevice>>

    /** Called once at app start. Returns true if a previously-used device reconnected silently. */
    suspend fun tryAutoConnect(): Boolean

    /**
     * Starts talking to [device]. For an unpaired network device this leads to
     * [ConnectionState.AwaitingPairingCode]; for an already-paired network device or any
     * Bluetooth device it connects directly.
     */
    suspend fun beginConnection(device: TvDevice)

    suspend fun submitPairingCode(code: String)

    suspend fun cancelPairingOrConnection()

    suspend fun forgetDevice(device: TvDevice)

    suspend fun sendKey(key: RemoteKey, kind: KeyPressKind = KeyPressKind.SHORT)
    suspend fun sendText(text: String)
    suspend fun sendPointerMove(dx: Float, dy: Float)
    suspend fun sendPointerClick()

    /** Returns false when the active transport cannot launch apps (Bluetooth HID). */
    suspend fun launchApp(target: String): Boolean

    fun disconnect()
}
