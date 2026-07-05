package com.batin.tvremote.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batin.tvremote.data.model.ConnectionState
import com.batin.tvremote.data.model.KeyPressKind
import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.data.model.TvDevice
import com.batin.tvremote.data.repository.RemoteControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Well-known package names used by the shortcut row. market://launch?id=<pkg> works across
// both older and newer Android TV Remote Service builds (see README for sourcing notes).
private const val PKG_YOUTUBE_TV = "com.google.android.youtube.tv"
private const val PKG_PLAY_STORE = "com.android.vending"
private const val PKG_TV_PLUS = "com.turkcell.ott"

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val repository: RemoteControlRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Idle
    )

    private var lastDevice: TvDevice? = null

    private val _appShortcutsEnabled = MutableStateFlow(true)
    val appShortcutsEnabled: StateFlow<Boolean> = _appShortcutsEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        lastDevice = state.device
                        _appShortcutsEnabled.value =
                            state.via == com.batin.tvremote.data.model.ConnectionType.NETWORK_ATV_PROTOCOL
                    }
                    is ConnectionState.Connecting -> lastDevice = state.device
                    else -> Unit
                }
            }
        }
    }

    fun sendKey(key: RemoteKey) = viewModelScope.launch { repository.sendKey(key, KeyPressKind.SHORT) }
    fun sendKeyDown(key: RemoteKey) = viewModelScope.launch { repository.sendKey(key, KeyPressKind.LONG_PRESS_START) }
    fun sendKeyUp(key: RemoteKey) = viewModelScope.launch { repository.sendKey(key, KeyPressKind.LONG_PRESS_END) }
    fun sendText(text: String) = viewModelScope.launch { repository.sendText(text) }
    fun sendPointerMove(dx: Float, dy: Float) = viewModelScope.launch { repository.sendPointerMove(dx, dy) }
    fun sendPointerClick() = viewModelScope.launch { repository.sendPointerClick() }

    fun launchYoutube() = viewModelScope.launch { repository.launchApp("market://launch?id=$PKG_YOUTUBE_TV") }
    fun launchPlayStore() = viewModelScope.launch { repository.launchApp("market://launch?id=$PKG_PLAY_STORE") }
    fun launchTvPlus() = viewModelScope.launch { repository.launchApp("market://launch?id=$PKG_TV_PLUS") }

    fun reconnect() = viewModelScope.launch { lastDevice?.let { repository.beginConnection(it) } }

    fun forgetDevice() = viewModelScope.launch {
        lastDevice?.let { repository.forgetDevice(it) }
    }

    fun disconnect() = repository.disconnect()
}
