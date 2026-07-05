package com.batin.tvremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.batin.tvremote.R
import com.batin.tvremote.data.model.RemoteKey

@Composable
fun NavigationRow(
    modifier: Modifier = Modifier,
    onKey: (RemoteKey) -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        FilledTonalIconButton(onClick = { onKey(RemoteKey.BACK) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.remote_back))
        }
        FilledTonalIconButton(onClick = { onKey(RemoteKey.HOME) }) {
            Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.remote_home))
        }
        FilledTonalIconButton(onClick = { onKey(RemoteKey.MENU) }) {
            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.remote_menu))
        }
    }
}

@Composable
fun VolumeRow(
    modifier: Modifier = Modifier,
    onKey: (RemoteKey) -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        FilledTonalIconButton(onClick = { onKey(RemoteKey.VOLUME_DOWN) }) {
            Icon(Icons.Filled.VolumeDown, contentDescription = stringResource(R.string.remote_volume_down))
        }
        FilledTonalIconButton(onClick = { onKey(RemoteKey.MUTE) }) {
            Icon(Icons.Filled.VolumeOff, contentDescription = stringResource(R.string.remote_mute))
        }
        FilledTonalIconButton(onClick = { onKey(RemoteKey.VOLUME_UP) }) {
            Icon(Icons.Filled.VolumeUp, contentDescription = stringResource(R.string.remote_volume_up))
        }
    }
}

@Composable
fun MediaRow(
    modifier: Modifier = Modifier,
    onKey: (RemoteKey) -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        FilledTonalIconButton(onClick = { onKey(RemoteKey.PLAY_PAUSE) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.remote_play_pause))
        }
        FilledTonalIconButton(
            onClick = { onKey(RemoteKey.POWER) },
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = stringResource(R.string.remote_power))
        }
    }
}
