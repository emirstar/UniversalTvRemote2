package com.batin.tvremote.ui

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

/**
 * The only job of this ViewModel is to give TvRemoteNavHost a single source of truth for
 * "which screen should be visible right now" and to kick off the silent auto-reconnect
 * attempt exactly once when the app starts.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    repository: RemoteControlRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectionState.Idle
    )

    init {
        viewModelScope.launch {
            repository.tryAutoConnect()
        }
    }
}
