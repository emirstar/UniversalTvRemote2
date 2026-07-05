package com.batin.tvremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.batin.tvremote.R
import com.batin.tvremote.data.model.ConnectionState
import com.batin.tvremote.data.model.ConnectionType

@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, showProgress, showActions) = when (state) {
        is ConnectionState.Connected -> Triple(
            if (state.via == ConnectionType.NETWORK_ATV_PROTOCOL)
                stringResource(R.string.remote_connected_via_network)
            else stringResource(R.string.remote_connected_via_bluetooth),
            false,
            false
        )
        is ConnectionState.Connecting -> Triple(stringResource(R.string.remote_connecting), true, false)
        is ConnectionState.Error -> Triple(state.message, false, true)
        else -> Triple(stringResource(R.string.remote_disconnected), false, true)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showProgress) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Icon(
                        imageVector = if (state is ConnectionState.Connected && state.via == ConnectionType.BLUETOOTH_HID)
                            Icons.Filled.Bluetooth else Icons.Filled.Wifi,
                        contentDescription = null
                    )
                }
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    val currentApp = (state as? ConnectionState.Connected)?.currentAppPackage
                    if (currentApp != null) {
                        Text(
                            text = stringResource(R.string.remote_now_playing_prefix, currentApp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (showActions) {
                Row {
                    TextButton(onClick = onReconnect) { Text(stringResource(R.string.remote_reconnect)) }
                    TextButton(onClick = onForget) { Text(stringResource(R.string.remote_forget_device)) }
                }
            }
        }
    }
}
