package com.batin.tvremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batin.tvremote.R
import com.batin.tvremote.ui.components.AppShortcutRow
import com.batin.tvremote.ui.components.ConnectionStatusBar
import com.batin.tvremote.ui.components.DPadControl
import com.batin.tvremote.ui.components.KeyboardInputSheet
import com.batin.tvremote.ui.components.MediaRow
import com.batin.tvremote.ui.components.NavigationRow
import com.batin.tvremote.ui.components.TouchpadSurface
import com.batin.tvremote.ui.components.VolumeRow

@Composable
fun RemoteScreen(viewModel: RemoteViewModel = hiltViewModel()) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val appShortcutsEnabled by viewModel.appShortcutsEnabled.collectAsStateWithLifecycle()

    var touchpadMode by remember { mutableStateOf(false) }
    var showKeyboardSheet by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (touchpadMode) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    ConnectionStatusBar(
                        state = state,
                        onReconnect = viewModel::reconnect,
                        onForget = viewModel::forgetDevice,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        TouchpadSurface(
                            modifier = Modifier.fillMaxSize(),
                            onMove = viewModel::sendPointerMove,
                            onClick = viewModel::sendPointerClick
                        )
                        IconButton(
                            onClick = { touchpadMode = false },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectionStatusBar(state = state, onReconnect = viewModel::reconnect, onForget = viewModel::forgetDevice)

                    DPadControl(onKey = viewModel::sendKey)
                    NavigationRow(onKey = viewModel::sendKey)
                    VolumeRow(onKey = viewModel::sendKey)
                    MediaRow(onKey = viewModel::sendKey)

                    if (appShortcutsEnabled) {
                        AppShortcutRow(
                            onYoutube = viewModel::launchYoutube,
                            onPlayStore = viewModel::launchPlayStore,
                            onTvPlus = viewModel::launchTvPlus
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.remote_bluetooth_feature_unavailable),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = touchpadMode,
                            onClick = { touchpadMode = true },
                            label = { Text(stringResource(R.string.remote_touchpad_toggle)) },
                            leadingIcon = { Icon(Icons.Filled.TouchApp, contentDescription = null) }
                        )
                        FilterChip(
                            selected = showKeyboardSheet,
                            onClick = { showKeyboardSheet = true },
                            label = { Text(stringResource(R.string.remote_keyboard_toggle)) },
                            leadingIcon = { Icon(Icons.Filled.Keyboard, contentDescription = null) }
                        )
                    }
                }
            }

            if (showKeyboardSheet) {
                KeyboardInputSheet(
                    onDismiss = { showKeyboardSheet = false },
                    onSend = { text -> viewModel.sendText(text) }
                )
            }
        }
    }
}
