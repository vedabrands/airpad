package com.airpad.app.data.models

data class MouseDeltaEvent(
    val dx: Float,
    val dy: Float
)

data class MouseButtonEvent(
    val buttonMask: Byte,
    val action: Byte
)

data class MouseScrollEvent(
    val scrollX: Short,
    val scrollY: Short
)

data class MouseComboEvent(
    val dx: Short,
    val dy: Short,
    val buttonState: Byte,
    val scrollY: Byte,
    val scrollX: Byte
)

data class KeyboardKeyEvent(
    val keyCode: Short,
    val action: Byte,
    val modifiers: Byte = 0
)

data class GamepadStateEvent(
    val buttonMask: Int,
    val leftStickX: Short,
    val leftStickY: Short,
    val rightStickX: Short,
    val rightStickY: Short,
    val leftTrigger: UByte,
    val rightTrigger: UByte
)

data class GamepadStickEvent(
    val stickId: Byte,
    val posX: Short,
    val posY: Short
)

data class GamepadButtonEvent(
    val buttonBit: Int,
    val action: Byte
)

data class GamepadTriggerEvent(
    val triggerId: Byte,
    val value: UByte
)

data class RawImuEvent(
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float
)

data class TouchConfig(
    val sensitivity: Float = 1.5f,
    val deadzone: Float = 0.5f,
    val smoothingFactor: Float = 0.15f,
    val invertScroll: Boolean = false,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    /** Velocity-based acceleration curve */
    val accelerationEnabled: Boolean = true,
    val accelThreshold: Float = 30f,
    val accelGain: Float = 1.8f,
    val accelExponent: Float = 1.4f,
    /** Sub-pixel interpolation for ultra-smooth movement */
    val subpixelPrecision: Boolean = true,
    /** Predictive smoothing (reduces perceived latency) */
    val predictionEnabled: Boolean = true,
    val predictionFrames: Int = 2
)

