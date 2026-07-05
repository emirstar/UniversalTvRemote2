package com.batin.tvremote.ui.discovery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batin.tvremote.R
import com.batin.tvremote.data.model.TvDevice
import com.batin.tvremote.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(viewModel: DiscoveryViewModel = hiltViewModel()) {
    val networkDevices by viewModel.networkDevices.collectAsStateWithLifecycle()
    val bluetoothDevices by viewModel.bluetoothDevices.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var manualIp by remember { mutableStateOf("") }
    var permissionsGranted by remember { mutableStateOf(PermissionUtils.hasAllRuntimePermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> permissionsGranted = results.values.all { it } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.discovery_title)) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!permissionsGranted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.discovery_permission_rationale))
                            Button(onClick = { permissionLauncher.launch(PermissionUtils.requiredRuntimePermissions()) }) {
                                Text(stringResource(R.string.discovery_grant_permissions))
                            }
                        }
                    }
                }
            }

            if (savedDevices.isNotEmpty()) {
                item {
                    Text(
                        text = "Kayıtlı Cihazlar",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(savedDevices, key = { "saved:${it.id}" }) { device ->
                    DeviceRow(device = device, onClick = { viewModel.connect(device) }, subtitle = "Daha önce eşleştirildi")
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.discovery_manual_entry),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            placeholder = { Text(stringResource(R.string.discovery_manual_ip_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { viewModel.connectManual(manualIp) }) {
                            Text(stringResource(R.string.discovery_connect_action))
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.discovery_network_section), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                }
            }
            if (networkDevices.isEmpty()) {
                item { Text(stringResource(R.string.discovery_empty_state), style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(networkDevices, key = { "net:${it.id}" }) { device ->
                    DeviceRow(device = device, onClick = { viewModel.connect(device) }, subtitle = device.host ?: "")
                }
            }

            if (viewModel.bluetoothAvailable) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(stringResource(R.string.discovery_bluetooth_section), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(bluetoothDevices, key = { "bt:${it.id}" }) { device ->
                    DeviceRow(device = device, onClick = { viewModel.connect(device) }, subtitle = device.bluetoothAddress ?: "")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: TvDevice, onClick: () -> Unit, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(device.displayName) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
            trailingContent = { TextButton(onClick = onClick) { Text(stringResource(R.string.discovery_connect_action)) } }
        )
    }
}
