package com.batin.tvremote.data.remote.bluetooth

import com.batin.tvremote.data.model.RemoteKey

/**
 * Maps [RemoteKey] to the two HID report types this app can send. Home/Back/Menu have no
 * dedicated USB-HID usage in the consumer or keyboard pages, so they fall back to the
 * closest conventional keyboard keys (Home, Escape, Application/Menu key respectively);
 * whether a given TV/Android version reacts to those the same way it would to a real
 * remote's Home/Back/Menu button is TV- and launcher-dependent. This is a genuine
 * limitation of Bluetooth HID remotes in general, not specific to this app.
 */
object HidKeyMapper {

    sealed interface Mapping {
        data class Keyboard(val usage: Int, val shift: Boolean = false) : Mapping
        data class Consumer(val bit: Int) : Mapping
    }

    // USB HID Usage Tables, page 0x07 (Keyboard/Keypad).
    private const val KEY_RIGHT_ARROW = 0x4F
    private const val KEY_LEFT_ARROW = 0x50
    private const val KEY_DOWN_ARROW = 0x51
    private const val KEY_UP_ARROW = 0x52
    private const val KEY_RETURN = 0x28
    private const val KEY_ESCAPE = 0x29
    private const val KEY_HOME = 0x4A
    private const val KEY_APPLICATION = 0x65

    // Bit positions matching HidReportDescriptors' consumer report layout.
    private const val BIT_PLAY_PAUSE = 0
    private const val BIT_VOLUME_UP = 1
    private const val BIT_VOLUME_DOWN = 2
    private const val BIT_MUTE = 3
    private const val BIT_POWER = 4

    fun map(key: RemoteKey): Mapping = when (key) {
        RemoteKey.DPAD_UP -> Mapping.Keyboard(KEY_UP_ARROW)
        RemoteKey.DPAD_DOWN -> Mapping.Keyboard(KEY_DOWN_ARROW)
        RemoteKey.DPAD_LEFT -> Mapping.Keyboard(KEY_LEFT_ARROW)
        RemoteKey.DPAD_RIGHT -> Mapping.Keyboard(KEY_RIGHT_ARROW)
        RemoteKey.DPAD_CENTER -> Mapping.Keyboard(KEY_RETURN)
        RemoteKey.HOME -> Mapping.Keyboard(KEY_HOME)
        RemoteKey.BACK -> Mapping.Keyboard(KEY_ESCAPE)
        RemoteKey.MENU -> Mapping.Keyboard(KEY_APPLICATION)
        RemoteKey.VOLUME_UP -> Mapping.Consumer(BIT_VOLUME_UP)
        RemoteKey.VOLUME_DOWN -> Mapping.Consumer(BIT_VOLUME_DOWN)
        RemoteKey.MUTE -> Mapping.Consumer(BIT_MUTE)
        RemoteKey.PLAY_PAUSE -> Mapping.Consumer(BIT_PLAY_PAUSE)
        RemoteKey.POWER -> Mapping.Consumer(BIT_POWER)
    }

    /** Same fallback table as AndroidKeyCodeMapper, duplicated here to keep each transport self-contained. */
    fun charToKeyboardUsage(char: Char): Mapping.Keyboard? = when {
        char in 'a'..'z' -> Mapping.Keyboard(0x04 + (char - 'a'))
        char in 'A'..'Z' -> Mapping.Keyboard(0x04 + (char.lowercaseChar() - 'a'), shift = true)
        char in '1'..'9' -> Mapping.Keyboard(0x1E + (char - '1'))
        char == '0' -> Mapping.Keyboard(0x27)
        char == ' ' -> Mapping.Keyboard(0x2C)
        char == '\n' -> Mapping.Keyboard(KEY_RETURN)
        char == '.' -> Mapping.Keyboard(0x37)
        char == ',' -> Mapping.Keyboard(0x36)
        char == '-' -> Mapping.Keyboard(0x2D)
        char == '/' -> Mapping.Keyboard(0x38)
        else -> {
            val fallback = transliterate(char)
            if (fallback != null) charToKeyboardUsage(fallback) else null
        }
    }

    private fun transliterate(char: Char): Char? = when (char) {
        'ç' -> 'c'; 'Ç' -> 'C'
        'ğ' -> 'g'; 'Ğ' -> 'G'
        'ı' -> 'i'; 'İ' -> 'I'
        'ö' -> 'o'; 'Ö' -> 'O'
        'ş' -> 's'; 'Ş' -> 'S'
        'ü' -> 'u'; 'Ü' -> 'U'
        else -> null
    }
}
