package com.batin.tvremote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.batin.tvremote.R

@Composable
fun AppShortcutRow(
    modifier: Modifier = Modifier,
    onYoutube: () -> Unit,
    onPlayStore: () -> Unit,
    onTvPlus: () -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = onYoutube, label = { Text(stringResource(R.string.remote_shortcut_youtube)) })
        AssistChip(onClick = onPlayStore, label = { Text(stringResource(R.string.remote_shortcut_play_store)) })
        AssistChip(onClick = onTvPlus, label = { Text(stringResource(R.string.remote_shortcut_tv_plus)) })
    }
}
