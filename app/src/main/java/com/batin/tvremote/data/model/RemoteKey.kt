package com.batin.tvremote.data.model

/**
 * The fixed set of buttons the UI exposes. This is intentionally transport-agnostic:
 * [com.batin.tvremote.data.remote.atv.AndroidKeyCodeMapper] maps these onto the
 * Android TV Remote Protocol's `RemoteKeyCode`, while
 * [com.batin.tvremote.data.remote.bluetooth.HidKeyMapper] maps them onto USB-HID
 * usage codes for the Bluetooth fallback. Neither transport needs to know about
 * the other.
 */
enum class RemoteKey {
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    DPAD_CENTER,
    HOME,
    BACK,
    MENU,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    PLAY_PAUSE,
    POWER
}

/** Whether a key press should be sent as a single tap or as a held-down gesture. */
enum class KeyPressKind {
    SHORT,
    LONG_PRESS_START,
    LONG_PRESS_END
}
