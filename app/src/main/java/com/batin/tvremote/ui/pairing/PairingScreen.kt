package com.batin.tvremote.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batin.tvremote.R
import com.batin.tvremote.data.model.ConnectionState

@Composable
fun PairingScreen(viewModel: PairingViewModel = hiltViewModel()) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.pairing_waiting_for_tv), style = MaterialTheme.typography.bodyLarge)

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.uppercase() },
                label = { Text(stringResource(R.string.pairing_code_hint)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth()
            )

            when (state) {
                is ConnectionState.Pairing -> CircularProgressIndicator()
                is ConnectionState.Error -> Text(
                    text = (state as ConnectionState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
                else -> Unit
            }

            Button(
                onClick = { viewModel.submitCode(code) },
                enabled = code.length == 6 && state !is ConnectionState.Pairing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pairing_confirm))
            }
            TextButton(onClick = { viewModel.cancel() }) {
                Text(stringResource(R.string.pairing_cancel))
            }
        }
    }
}
