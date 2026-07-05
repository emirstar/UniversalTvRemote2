package com.batin.tvremote.data.remote

import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import kotlinx.coroutines.flow.SharedFlow

/**
 * Uniform surface both the Android TV Remote Protocol client and the Bluetooth HID
 * client implement. RemoteControlRepositoryImpl holds exactly one active
 * [RemoteTransport] at a time and never needs to know which concrete transport is
 * behind it.
 */
interface RemoteTransport {

    val events: SharedFlow<TransportEvent>

    suspend fun sendKey(key: RemoteKey, kind: KeyPressKind = KeyPressKind.SHORT)

    /** Best-effort text entry, one character at a time (see each implementation for limits). */
    suspend fun sendText(text: String)

    /** Relative pointer motion in dp; implementations decide how to translate this. */
    suspend fun sendPointerMove(dx: Float, dy: Float)

    suspend fun sendPointerClick()

    /** Returns false if this transport has no concept of launching an app (e.g. Bluetooth HID). */
    suspend fun launchApp(target: String): Boolean

    fun disconnect()
}

sealed interface TransportEvent {
    data class CurrentAppChanged(val packageName: String) : TransportEvent
    data class VolumeChanged(val level: Int, val max: Int, val muted: Boolean) : TransportEvent
    data object Disconnected : TransportEvent
}
