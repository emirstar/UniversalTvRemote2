package com.batin.tvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.batin.tvremote.R
import com.batin.tvremote.data.model.RemoteKey

/**
 * A classic cross-shaped D-pad cluster with a round OK button in the middle, sized for
 * comfortable one-thumb reach.
 */
@Composable
fun DPadControl(
    modifier: Modifier = Modifier,
    onKey: (RemoteKey) -> Unit
) {
    Box(modifier = modifier.size(232.dp), contentAlignment = Alignment.Center) {
        DirectionButton(Icons.Filled.KeyboardArrowUp, Modifier.align(Alignment.TopCenter), stringResource(R.string.remote_dpad_up)) { onKey(RemoteKey.DPAD_UP) }
        DirectionButton(Icons.Filled.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter), stringResource(R.string.remote_dpad_down)) { onKey(RemoteKey.DPAD_DOWN) }
        DirectionButton(Icons.Filled.ChevronLeft, Modifier.align(Alignment.CenterStart), stringResource(R.string.remote_dpad_left)) { onKey(RemoteKey.DPAD_LEFT) }
        DirectionButton(Icons.Filled.ChevronRight, Modifier.align(Alignment.CenterEnd), stringResource(R.string.remote_dpad_right)) { onKey(RemoteKey.DPAD_RIGHT) }

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onKey(RemoteKey.DPAD_CENTER) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.remote_ok),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun DirectionButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
