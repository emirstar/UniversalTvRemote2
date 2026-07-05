package com.batin.tvremote.data.model

/** Overall state of the active remote-control session, surfaced directly to the UI. */
sealed interface ConnectionState {

    /** No device selected yet / fully disconnected. */
    data object Idle : ConnectionState

    /** Scanning the network and/or Bluetooth for candidate devices. */
    data object Discovering : ConnectionState

    /** TLS socket open on the pairing port, waiting for the on-screen code. */
    data class AwaitingPairingCode(val device: TvDevice) : ConnectionState

    /** Pairing code was submitted, waiting for the TV to confirm the secret hash. */
    data class Pairing(val device: TvDevice) : ConnectionState

    /** Opening the control channel (network) or the HID profile (Bluetooth). */
    data class Connecting(val device: TvDevice, val via: ConnectionType) : ConnectionState

    /** Ready to send key/text/pointer events. */
    data class Connected(
        val device: TvDevice,
        val via: ConnectionType,
        val currentAppPackage: String? = null
    ) : ConnectionState

    /** Connection dropped or never succeeded; [retryable] hints whether a retry makes sense. */
    data class Error(val message: String, val retryable: Boolean = true) : ConnectionState
}
