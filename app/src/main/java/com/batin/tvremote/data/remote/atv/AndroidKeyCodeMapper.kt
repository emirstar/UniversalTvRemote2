package com.batin.tvremote.data.remote.atv

import com.batin.tvremote.data.model.RemoteKey
import com.batin.tvremote.proto.remote.RemoteKeyCode

/**
 * Maps the app's transport-agnostic [RemoteKey] buttons, and plain-text characters typed
 * in the keyboard sheet, onto the protocol's RemoteKeyCode enum (which mirrors
 * android.view.KeyEvent.KEYCODE_* values).
 */
object AndroidKeyCodeMapper {

    fun map(key: RemoteKey): RemoteKeyCode = when (key) {
        RemoteKey.DPAD_UP -> RemoteKeyCode.KEYCODE_DPAD_UP
        RemoteKey.DPAD_DOWN -> RemoteKeyCode.KEYCODE_DPAD_DOWN
        RemoteKey.DPAD_LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
        RemoteKey.DPAD_RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
        RemoteKey.DPAD_CENTER -> RemoteKeyCode.KEYCODE_DPAD_CENTER
        RemoteKey.HOME -> RemoteKeyCode.KEYCODE_HOME
        RemoteKey.BACK -> RemoteKeyCode.KEYCODE_BACK
        RemoteKey.MENU -> RemoteKeyCode.KEYCODE_MENU
        RemoteKey.VOLUME_UP -> RemoteKeyCode.KEYCODE_VOLUME_UP
        RemoteKey.VOLUME_DOWN -> RemoteKeyCode.KEYCODE_VOLUME_DOWN
        RemoteKey.MUTE -> RemoteKeyCode.KEYCODE_VOLUME_MUTE
        RemoteKey.PLAY_PAUSE -> RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteKey.POWER -> RemoteKeyCode.KEYCODE_POWER
    }

    /**
     * Returns the key code needed to type [char], plus whether Shift must be held while
     * pressing it. Returns null for characters with no direct keycode - callers fall back
     * to a transliterated ASCII approximation (see charFallback) before giving up on a
     * character entirely, since neither this protocol nor a standard HID keyboard has a
     * generic "insert this exact Unicode codepoint" primitive.
     */
    fun mapChar(char: Char): Pair<RemoteKeyCode, Boolean>? {
        return when {
            char in 'a'..'z' -> letterCode(char) to false
            char in 'A'..'Z' -> letterCode(char.lowercaseChar()) to true
            char in '0'..'9' -> digitCode(char) to false
            char == ' ' -> RemoteKeyCode.KEYCODE_SPACE to false
            char == '\n' -> RemoteKeyCode.KEYCODE_ENTER to false
            char == ',' -> RemoteKeyCode.KEYCODE_COMMA to false
            char == '.' -> RemoteKeyCode.KEYCODE_PERIOD to false
            char == '-' -> RemoteKeyCode.KEYCODE_MINUS to false
            char == '/' -> RemoteKeyCode.KEYCODE_SLASH to false
            char == '@' -> RemoteKeyCode.KEYCODE_AT to false
            else -> {
                val fallback = charFallback(char) ?: return null
                mapChar(fallback)
            }
        }
    }

    /** Best-effort transliteration for Turkish letters that have no direct key code. */
    private fun charFallback(char: Char): Char? = when (char) {
        'ç' -> 'c'; 'Ç' -> 'C'
        'ğ' -> 'g'; 'Ğ' -> 'G'
        'ı' -> 'i'; 'İ' -> 'I'
        'ö' -> 'o'; 'Ö' -> 'O'
        'ş' -> 's'; 'Ş' -> 'S'
        'ü' -> 'u'; 'Ü' -> 'U'
        else -> null
    }

    private fun letterCode(lower: Char): RemoteKeyCode {
        val ordinal = RemoteKeyCode.KEYCODE_A.number + (lower - 'a')
        return RemoteKeyCode.forNumber(ordinal) ?: RemoteKeyCode.KEYCODE_UNKNOWN
    }

    private fun digitCode(digit: Char): RemoteKeyCode {
        val ordinal = RemoteKeyCode.KEYCODE_0.number + (digit - '0')
        return RemoteKeyCode.forNumber(ordinal) ?: RemoteKeyCode.KEYCODE_UNKNOWN
    }
}
