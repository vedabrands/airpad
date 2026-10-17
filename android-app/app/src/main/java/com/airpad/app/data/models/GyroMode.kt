package com.airpad.app.data.models

enum class GyroMode(val value: Byte) {
    OFF(0x00),
    GYRO_TO_STICK(0x01),
    GYRO_TO_MOUSE(0x02);

    companion object {
        fun fromByte(b: Byte): GyroMode = entries.firstOrNull { it.value == b } ?: OFF
    }
}

enum class GyroHorizontalAxis(val value: Byte) {
    YAW(0x00),
    ROLL(0x01);

    companion object {
        fun fromByte(b: Byte): GyroHorizontalAxis = entries.firstOrNull { it.value == b } ?: YAW
    }
}
