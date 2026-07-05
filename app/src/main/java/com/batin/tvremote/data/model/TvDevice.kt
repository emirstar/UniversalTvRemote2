package com.batin.tvremote.data.model

/**
 * A controllable TV / Android TV box, whether just discovered or previously paired.
 *
 * [id] is stable across app restarts so it can be used as a Room primary key:
 * for network devices it's the MAC-independent "host:port" pair is not stable enough
 * (DHCP can reassign IPs), so we key network devices by the mDNS service name instead,
 * falling back to host when unavailable. Bluetooth devices are keyed by their MAC
 * address, which Android guarantees is stable for a given bonded device.
 */
data class TvDevice(
    val id: String,
    val displayName: String,
    val connectionType: ConnectionType,
    // --- network-specific fields (null for Bluetooth-only entries) ---
    val host: String? = null,
    val pairingPort: Int = DEFAULT_PAIRING_PORT,
    val remotePort: Int = DEFAULT_REMOTE_PORT,
    // --- bluetooth-specific fields (null for network-only entries) ---
    val bluetoothAddress: String? = null,
    // SHA-256 fingerprint of the TV's certificate, captured the moment pairing succeeded.
    // Every later network connection checks the TV still presents this exact certificate
    // (see AtvRemoteClient) instead of blindly trusting whatever answers on that IP.
    val pairedServerCertFingerprint: String? = null,
    // --- shared bookkeeping ---
    val isPaired: Boolean = false,
    val lastConnectedAtMillis: Long? = null,
    val autoConnect: Boolean = false
) {
    companion object {
        const val DEFAULT_PAIRING_PORT = 6467
        const val DEFAULT_REMOTE_PORT = 6466
    }
}
