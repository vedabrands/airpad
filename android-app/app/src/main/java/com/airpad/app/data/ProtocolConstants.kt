package com.airpad.app.data

object ProtocolConstants {
    const val MAGIC_BYTES: Short = 0x4150.toShort() // "AP"
    const val PROTOCOL_VERSION: Byte = 0x01
    const val HEADER_SIZE = 16

    // Default Ports
    const val DISCOVERY_PORT = 54320
    const val CONTROL_PORT = 54321

    // Opcodes
    const val OP_HEARTBEAT: Byte = 0x01
    const val OP_HEARTBEAT_ACK: Byte = 0x02
    const val OP_DISCOVERY_BEACON: Byte = 0x05
    const val OP_PAIR_REQUEST: Byte = 0x06
    const val OP_PAIR_RESPONSE: Byte = 0x07
    const val OP_DISCONNECT: Byte = 0x08
    const val OP_MODE_SWITCH: Byte = 0x10
    const val OP_MOUSE_DELTA: Byte = 0x20
    const val OP_MOUSE_BUTTON: Byte = 0x21
    const val OP_MOUSE_SCROLL: Byte = 0x22
    const val OP_MOUSE_COMBO: Byte = 0x23
    const val OP_KEYBOARD_KEY: Byte = 0x24
    const val OP_GAMEPAD_STATE: Byte = 0x30
    const val OP_GAMEPAD_STICK: Byte = 0x31
    const val OP_GAMEPAD_BUTTON: Byte = 0x32
    const val OP_GAMEPAD_TRIGGER: Byte = 0x33
    const val OP_RAW_IMU_DATA: Byte = 0x40
    const val OP_GYRO_CALIBRATE: Byte = 0x41

    // New Opcodes for Game Profile System (0x50+)
    const val OP_ACTIVE_WINDOW_INFO: Byte = 0x50      // Server -> Client: Active game window info
    const val OP_GAME_PROFILE_SYNC: Byte = 0x51        // Bi-directional: Profile config sync
    const val OP_PROFILE_CHANGE: Byte = 0x52           // Client -> Server: Profile change event
    const val OP_MACRO_TRIGGER: Byte = 0x53            // Client -> Server: Macro trigger request

    // Pairing Status
    const val PAIR_STATUS_SUCCESS: Byte = 0x00
    const val PAIR_STATUS_INVALID_PIN: Byte = 0x01
    const val PAIR_STATUS_VERSION_MISMATCH: Byte = 0x02
    const val PAIR_STATUS_SERVER_BUSY: Byte = 0x03
    const val PAIR_STATUS_REJECTED: Byte = 0x04

    // Mouse Button Bits
    const val MOUSE_BTN_LEFT: Byte = 0x01
    const val MOUSE_BTN_RIGHT: Byte = 0x02
    const val MOUSE_BTN_MIDDLE: Byte = 0x04
    const val MOUSE_BTN_BACK: Byte = 0x08
    const val MOUSE_BTN_FORWARD: Byte = 0x10

    // Actions
    const val ACTION_RELEASE: Byte = 0x00
    const val ACTION_PRESS: Byte = 0x01
    const val ACTION_CLICK: Byte = 0x02
    const val BTN_ACTION_RELEASE: Byte = 0x00
    const val BTN_ACTION_PRESS: Byte = 0x01
    const val BTN_ACTION_CLICK: Byte = 0x02
    const val MOUSE_ACTION_UP: Byte = 0x00
    const val MOUSE_ACTION_DOWN: Byte = 0x01
    const val MOUSE_ACTION_CLICK: Byte = 0x02

    // Gamepad Button Bits
    const val GP_BTN_A = 0x00000001
    const val GP_BTN_B = 0x00000002
    const val GP_BTN_X = 0x00000004
    const val GP_BTN_Y = 0x00000008
    const val GP_BTN_DPAD_UP = 0x00000010
    const val GP_BTN_DPAD_DOWN = 0x00000020
    const val GP_BTN_DPAD_LEFT = 0x00000040
    const val GP_BTN_DPAD_RIGHT = 0x00000080
    const val GP_BTN_LB = 0x00000100
    const val GP_BTN_RB = 0x00000200
    const val GP_BTN_LS = 0x00000400
    const val GP_BTN_RS = 0x00000800
    const val GP_BTN_RIGHT_SHOULDER: Int = GP_BTN_RB
    const val GP_BTN_START = 0x00001000
    const val GP_BTN_BACK = 0x00002000
    const val GP_BTN_GUIDE = 0x00004000
    const val GP_BTN_TOUCHPAD = 0x00008000

    const val GAMEPAD_BTN_A = GP_BTN_A
    const val GAMEPAD_BTN_B = GP_BTN_B
    const val GAMEPAD_BTN_X = GP_BTN_X
    const val GAMEPAD_BTN_Y = GP_BTN_Y
    const val GAMEPAD_BTN_DPAD_UP = GP_BTN_DPAD_UP
    const val GAMEPAD_BTN_DPAD_DOWN = GP_BTN_DPAD_DOWN
    const val GAMEPAD_BTN_DPAD_LEFT = GP_BTN_DPAD_LEFT
    const val GAMEPAD_BTN_DPAD_RIGHT = GP_BTN_DPAD_RIGHT
    const val GAMEPAD_BTN_LB = GP_BTN_LB
    const val GAMEPAD_BTN_RB = GP_BTN_RB
    const val GAMEPAD_BTN_LS = GP_BTN_LS
    const val GAMEPAD_BTN_RS = GP_BTN_RS
    const val GAMEPAD_BTN_START = GP_BTN_START
    const val GAMEPAD_BTN_BACK = GP_BTN_BACK
    const val GAMEPAD_BTN_GUIDE = GP_BTN_GUIDE
    const val GAMEPAD_BTN_TOUCHPAD = GP_BTN_TOUCHPAD

    // Button names for UI mapping display
    val BUTTON_NAMES: Map<Int, String> = mapOf(
        GP_BTN_A to "A",
        GP_BTN_B to "B",
        GP_BTN_X to "X",
        GP_BTN_Y to "Y",
        GP_BTN_DPAD_UP to "D-Pad ↑",
        GP_BTN_DPAD_DOWN to "D-Pad ↓",
        GP_BTN_DPAD_LEFT to "D-Pad ←",
        GP_BTN_DPAD_RIGHT to "D-Pad →",
        GP_BTN_LB to "LB",
        GP_BTN_RB to "RB",
        GP_BTN_LS to "L-Stick Click",
        GP_BTN_RS to "R-Stick Click",
        GP_BTN_START to "Start",
        GP_BTN_BACK to "Select",
        GP_BTN_GUIDE to "Guide",
        GP_BTN_TOUCHPAD to "Touchpad"
    )

    // Timeout
    const val HEARTBEAT_INTERVAL_MS = 500L
    const val DISCONNECT_TIMEOUT_MS = 1500L

    // Keyboard Key Codes (USB HID usage page subset)
    const val KEY_NONE: Short = 0
    const val KEY_ERROR_ROLLOVER: Short = 1
    const val KEY_A: Short = 4
    const val KEY_B: Short = 5
    const val KEY_C: Short = 6
    const val KEY_D: Short = 7
    const val KEY_E: Short = 8
    const val KEY_F: Short = 9
    const val KEY_G: Short = 10
    const val KEY_H: Short = 11
    const val KEY_I: Short = 12
    const val KEY_J: Short = 13
    const val KEY_K: Short = 14
    const val KEY_L: Short = 15
    const val KEY_M: Short = 16
    const val KEY_N: Short = 17
    const val KEY_O: Short = 18
    const val KEY_P: Short = 19
    const val KEY_Q: Short = 20
    const val KEY_R: Short = 21
    const val KEY_S: Short = 22
    const val KEY_T: Short = 23
    const val KEY_U: Short = 24
    const val KEY_V: Short = 25
    const val KEY_W: Short = 26
    const val KEY_X: Short = 27
    const val KEY_Y: Short = 28
    const val KEY_Z: Short = 29
    const val KEY_1: Short = 30
    const val KEY_2: Short = 31
    const val KEY_3: Short = 32
    const val KEY_4: Short = 33
    const val KEY_5: Short = 34
    const val KEY_6: Short = 35
    const val KEY_7: Short = 36
    const val KEY_8: Short = 37
    const val KEY_9: Short = 38
    const val KEY_0: Short = 39
    const val KEY_ENTER: Short = 40
    const val KEY_ESC: Short = 41
    const val KEY_BACKSPACE: Short = 42
    const val KEY_TAB: Short = 43
    const val KEY_SPACE: Short = 44
    const val KEY_MINUS: Short = 45
    const val KEY_EQUALS: Short = 46
    const val KEY_LEFT_BRACE: Short = 47
    const val KEY_RIGHT_BRACE: Short = 48
    const val KEY_BACKSLASH: Short = 49
    const val KEY_SEMICOLON: Short = 51
    const val KEY_APOSTROPHE: Short = 52
    const val KEY_GRAVE: Short = 53
    const val KEY_COMMA: Short = 54
    const val KEY_DOT: Short = 55
    const val KEY_SLASH: Short = 56
    const val KEY_CAPS_LOCK: Short = 57

    // Modifier bits (USB HID modifier bitmap)
    const val MOD_LEFT_CTRL: Byte = 0x01
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_ALT: Byte = 0x04
    const val MOD_LEFT_GUI: Byte = 0x08
    const val MOD_RIGHT_CTRL: Byte = 0x10
    const val MOD_RIGHT_SHIFT: Byte = 0x20
    const val MOD_RIGHT_ALT: Byte = 0x40
    const val MOD_RIGHT_GUI: Byte = 0x80.toByte()
}
