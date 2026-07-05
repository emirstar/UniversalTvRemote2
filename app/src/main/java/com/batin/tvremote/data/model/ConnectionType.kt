package com.batin.tvremote.data.model

/**
 * How a [TvDevice] is (or should be) reached.
 *
 * The app always prefers [NETWORK_ATV_PROTOCOL]: it is faster, richer (volume level,
 * app launching, current-app awareness) and does not require Bluetooth pairing.
 * [BLUETOOTH_HID] only comes into play when a device cannot be discovered or
 * controlled over the network at all - see RemoteControlRepositoryImpl for the
 * selection logic.
 */
enum class ConnectionType {
    NETWORK_ATV_PROTOCOL,
    BLUETOOTH_HID
}
