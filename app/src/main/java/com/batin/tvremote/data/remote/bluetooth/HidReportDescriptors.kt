package com.batin.tvremote.data.remote.bluetooth

/**
 * Combined USB-HID report descriptor this app registers as a Bluetooth HID *device*
 * (i.e. the phone impersonates a keyboard + consumer-control remote + mouse, and the TV
 * acts as the HID *host* - the same role a Bluetooth keyboard plays). Three report IDs
 * share one descriptor so a single BluetoothHidDevice registration covers buttons,
 * media/volume keys and the touchpad:
 *   Report ID 1 - standard 8-byte boot-style keyboard (modifier byte + 6 key array)
 *   Report ID 2 - 5-bit consumer control bitmap (play/pause, vol+/-, mute, power)
 *   Report ID 3 - 2-button relative mouse (touchpad mode)
 * These are standard, extensively documented USB-HID Usage Tables constructs, not part
 * of the reverse-engineered Android TV Remote Protocol - unlike that protocol, this one
 * is a stable, decades-old, officially published spec.
 */
object HidReportDescriptors {

    const val KEYBOARD_REPORT_ID: Byte = 1
    const val CONSUMER_REPORT_ID: Byte = 2
    const val MOUSE_REPORT_ID: Byte = 3

    val DESCRIPTOR: ByteArray = byteArrayOf(
        // --- Keyboard (Report ID 1) ---
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), KEYBOARD_REPORT_ID,
        0x05, 0x07,             //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0.toByte(),    //   Usage Minimum (224)
        0x29, 0xE7.toByte(),    //   Usage Maximum (231)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x08,    //   Report Count (8)  -> modifier byte
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x75, 0x08,             //   Report Size (8)   -> reserved byte
        0x81.toByte(), 0x01,    //   Input (Const)
        0x95.toByte(), 0x06,    //   Report Count (6)  -> up to 6 simultaneous keys
        0x75, 0x08,             //   Report Size (8)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x65,             //   Logical Maximum (101)
        0x05, 0x07,             //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x65,             //   Usage Maximum (101)
        0x81.toByte(), 0x00,    //   Input (Data,Array)
        0xC0.toByte(),          // End Collection

        // --- Consumer control (Report ID 2): Play/Pause, Vol+, Vol-, Mute, Power ---
        0x05, 0x0C,             // Usage Page (Consumer)
        0x09, 0x01,             // Usage (Consumer Control)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), CONSUMER_REPORT_ID,
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x05,    //   Report Count (5)
        0x09, 0xCD.toByte(),    //   Usage (Play/Pause)       bit0
        0x09, 0xE9.toByte(),    //   Usage (Volume Increment) bit1
        0x09, 0xEA.toByte(),    //   Usage (Volume Decrement) bit2
        0x09, 0xE2.toByte(),    //   Usage (Mute)             bit3
        0x09, 0x30,             //   Usage (Power)            bit4
        0x81.toByte(), 0x02,    //   Input (Data,Var,Abs)
        0x95.toByte(), 0x03,    //   Report Count (3) padding
        0x81.toByte(), 0x03,    //   Input (Const,Var,Abs)
        0xC0.toByte(),          // End Collection

        // --- Relative mouse (Report ID 3): touchpad mode ---
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x02,             // Usage (Mouse)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x09, 0x01,             //   Usage (Pointer)
        0xA1.toByte(), 0x00,    //   Collection (Physical)
        0x85.toByte(), MOUSE_REPORT_ID,
        0x05, 0x09,             //     Usage Page (Button)
        0x19, 0x01,             //     Usage Minimum (Button 1)
        0x29, 0x02,             //     Usage Maximum (Button 2)
        0x15, 0x00,             //     Logical Minimum (0)
        0x25, 0x01,             //     Logical Maximum (1)
        0x95.toByte(), 0x02,    //     Report Count (2)
        0x75, 0x01,             //     Report Size (1)
        0x81.toByte(), 0x02,    //     Input (Data,Var,Abs)
        0x95.toByte(), 0x01,    //     Report Count (1)
        0x75, 0x06,             //     Report Size (6) padding
        0x81.toByte(), 0x03,    //     Input (Const,Var,Abs)
        0x05, 0x01,             //     Usage Page (Generic Desktop)
        0x09, 0x30,             //     Usage (X)
        0x09, 0x31,             //     Usage (Y)
        0x15, 0x81.toByte(),    //     Logical Minimum (-127)
        0x25, 0x7F,             //     Logical Maximum (127)
        0x75, 0x08,             //     Report Size (8)
        0x95.toByte(), 0x02,    //     Report Count (2)
        0x81.toByte(), 0x06,    //     Input (Data,Var,Rel)
        0xC0.toByte(),          //   End Collection
        0xC0.toByte()           // End Collection
    )

    const val MODIFIER_LEFT_SHIFT: Int = 0x02

    fun emptyKeyboardReport(): ByteArray = ByteArray(8)

    fun keyboardReport(modifier: Int, keyUsage: Int): ByteArray {
        val report = ByteArray(8)
        report[0] = modifier.toByte()
        report[2] = keyUsage.toByte()
        return report
    }

    fun emptyConsumerReport(): ByteArray = ByteArray(1)

    fun consumerReport(bit: Int): ByteArray = byteArrayOf((1 shl bit).toByte())

    fun mouseReport(buttons: Int, dx: Int, dy: Int): ByteArray =
        byteArrayOf(buttons.toByte(), dx.coerceIn(-127, 127).toByte(), dy.coerceIn(-127, 127).toByte())
}
