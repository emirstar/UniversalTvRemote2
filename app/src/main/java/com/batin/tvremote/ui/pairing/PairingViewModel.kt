package com.batin.tvremote.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batin.tvremote.data.model.ConnectionState
import com.batin.tvremote.data.repository.RemoteControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val repository: RemoteControlRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Idle
    )

    fun submitCode(code: String) {
        viewModelScope.launch { repository.submitPairingCode(code) }
    }

    fun cancel() {
        viewModelScope.launch { repository.cancelPairingOrConnection() }
    }
}
