package com.batin.tvremote.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.batin.tvremote.data.model.ConnectionType
import com.batin.tvremote.data.model.TvDevice

@Entity(tableName = "tv_devices")
data class TvDeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val connectionType: String,
    val host: String?,
    val pairingPort: Int,
    val remotePort: Int,
    val bluetoothAddress: String?,
    val pairedServerCertFingerprint: String?,
    val isPaired: Boolean,
    val lastConnectedAtMillis: Long?,
    val autoConnect: Boolean
)

fun TvDeviceEntity.toDomain(): TvDevice = TvDevice(
    id = id,
    displayName = displayName,
    connectionType = ConnectionType.valueOf(connectionType),
    host = host,
    pairingPort = pairingPort,
    remotePort = remotePort,
    bluetoothAddress = bluetoothAddress,
    pairedServerCertFingerprint = pairedServerCertFingerprint,
    isPaired = isPaired,
    lastConnectedAtMillis = lastConnectedAtMillis,
    autoConnect = autoConnect
)

fun TvDevice.toEntity(): TvDeviceEntity = TvDeviceEntity(
    id = id,
    displayName = displayName,
    connectionType = connectionType.name,
    host = host,
    pairingPort = pairingPort,
    remotePort = remotePort,
    bluetoothAddress = bluetoothAddress,
    pairedServerCertFingerprint = pairedServerCertFingerprint,
    isPaired = isPaired,
    lastConnectedAtMillis = lastConnectedAtMillis,
    autoConnect = autoConnect
)
