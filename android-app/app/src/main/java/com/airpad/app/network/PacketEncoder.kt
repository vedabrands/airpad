package com.airpad.app.network

import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.GamepadButtonEvent
import com.airpad.app.data.models.GamepadStateEvent
import com.airpad.app.data.models.GamepadStickEvent
import com.airpad.app.data.models.GamepadTriggerEvent
import com.airpad.app.data.models.GyroConfig
import com.airpad.app.data.models.KeyboardKeyEvent
import com.airpad.app.data.models.MouseButtonEvent
import com.airpad.app.data.models.MouseComboEvent
import com.airpad.app.data.models.MouseDeltaEvent
import com.airpad.app.data.models.MouseScrollEvent
import com.airpad.app.data.models.RawImuEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

object PacketEncoder {

    /**
     * Builds standard 16-byte Big-Endian header + payload.
     */
    fun createFrame(
        opcode: Byte,
        sessionToken: Long,
        sequence: Long,
        flags: Byte = 0,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val buffer = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE + payload.size)
        buffer.order(ByteOrder.BIG_ENDIAN)

        buffer.putShort(ProtocolConstants.MAGIC_BYTES)
        buffer.put(ProtocolConstants.PROTOCOL_VERSION)
        buffer.put(flags)
        buffer.putInt((sequence and 0xFFFFFFFFL).toInt())
        buffer.putInt((sessionToken and 0xFFFFFFFFL).toInt())
        buffer.put(opcode)
        buffer.put(0x00.toByte()) // Reserved
        buffer.putShort(payload.size.toShort())

        if (payload.isNotEmpty()) {
            buffer.put(payload)
        }

        return buffer.array()
    }

    fun encodeHeartbeat(clientTimestamp: Long): ByteArray {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(clientTimestamp)
        return buf.array()
    }

    fun encodePairRequest(
        pinCode: Int,
        clientUid: UUID,
        deviceName: String
    ): ByteArray {
        val nameBytes = deviceName.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(4 + 16 + 1 + nameBytes.size).order(ByteOrder.BIG_ENDIAN)

        buf.putInt(pinCode)
        buf.putLong(clientUid.mostSignificantBits)
        buf.putLong(clientUid.leastSignificantBits)
        buf.put(nameBytes.size.toByte())
        buf.put(nameBytes)

        return buf.array()
    }

    fun encodeDisconnect(reasonCode: Byte = 0): ByteArray {
        val buf = ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN)
        buf.put(reasonCode)
        return buf.array()
    }

    fun encodeModeSwitch(config: GyroConfig, resetBias: Boolean = false): ByteArray {
        val buf = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)

        var flags: Byte = 0
        if (config.invertX) flags = (flags.toInt() or 0x01).toByte()
        if (config.invertY) flags = (flags.toInt() or 0x02).toByte()
        if (config.ratchetActive) flags = (flags.toInt() or 0x04).toByte()
        if (resetBias) flags = (flags.toInt() or 0x08).toByte()
        if (config.holdToAimEnabled) flags = (flags.toInt() or 0x10).toByte()
        if (config.autoDriftCalibration) flags = (flags.toInt() or 0x20).toByte()

        buf.put(config.mode.value)
        buf.put(flags)
        buf.putShort(0x0000.toShort()) // Reserved alignment
        buf.putFloat(config.sensitivityX)
        buf.putFloat(config.sensitivityY)
        buf.putFloat(config.aimingSensitivityX)
        buf.putFloat(config.aimingSensitivityY)
        buf.putFloat(config.deadzone)
        buf.putFloat(config.smoothingFactor)
        buf.putFloat(config.nonLinearCurveExponent)
        buf.putInt(config.holdToAimButton)

        return buf.array()
    }

    fun encodeMouseDelta(event: MouseDeltaEvent): ByteArray {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        val intDx = event.dx.toInt().toShort()
        val intDy = event.dy.toInt().toShort()
        val subX = (kotlin.math.abs(event.dx - intDx) * 255.0f).toInt().coerceIn(0, 255).toByte()
        val subY = (kotlin.math.abs(event.dy - intDy) * 255.0f).toInt().coerceIn(0, 255).toByte()

        buf.putShort(intDx)
        buf.putShort(intDy)
        buf.put(subX)
        buf.put(subY)
        buf.putShort(0x0000.toShort()) // Padding

        return buf.array()
    }

    fun encodeMouseButton(event: MouseButtonEvent): ByteArray {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        buf.put(event.buttonMask)
        buf.put(event.action)
        return buf.array()
    }

    fun encodeMouseScroll(event: MouseScrollEvent): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(event.scrollY)
        buf.putShort(event.scrollX)
        return buf.array()
    }

    fun encodeMouseCombo(event: MouseComboEvent): ByteArray {
        val buf = ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(event.dx)
        buf.putShort(event.dy)
        buf.put(event.buttonState)
        buf.put(event.scrollY)
        buf.put(event.scrollX)
        return buf.array()
    }

    /**
     * Encodes multiple mouse deltas into a single batched packet for reduced overhead.
     * Format: [count:uint8][dx1:int16][dy1:int16][dx2:int16][dy2:int16]...
     * Sub-pixel precision is maintained for the first delta only.
     */
    fun encodeMouseComboBatch(
        deltas: List<Pair<Float, Float>>,
        buttonState: Byte = 0,
        scrollY: Byte = 0,
        scrollX: Byte = 0
    ): ByteArray {
        val count = deltas.size.coerceIn(1, 31) // Max 31 deltas per packet (6 + 31*4 = 130 bytes)
        val payloadSize = 6 + count * 4 // header(2+2+1+1) + count*4
        val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.BIG_ENDIAN)

        // First delta with sub-pixel precision
        val (firstDx, firstDy) = deltas.first()
        val intDx1 = firstDx.toInt().toShort()
        val intDy1 = firstDy.toInt().toShort()
        val subX = (kotlin.math.abs(firstDx - intDx1) * 255.0f).toInt().coerceIn(0, 255).toByte()
        val subY = (kotlin.math.abs(firstDy - intDy1) * 255.0f).toInt().coerceIn(0, 255).toByte()

        buf.putShort(intDx1)
        buf.putShort(intDy1)
        buf.put(subX)
        buf.put(subY)
        buf.put(buttonState)
        buf.put(scrollY)
        buf.put(scrollX)
        buf.put(count.toByte())

        // Remaining deltas (int16 only for efficiency)
        for (i in 1 until count) {
            val (dx, dy) = deltas[i]
            buf.putShort(dx.toInt().toShort())
            buf.putShort(dy.toInt().toShort())
        }

        return buf.array()
    }

    /**
     * Encodes a batch of mouse deltas with timestamps for jitter compensation.
     */
    fun encodeTimestampedMouseBatch(
        deltas: List<TimestampedDelta>
    ): ByteArray {
        val count = deltas.size.coerceIn(1, 15) // Max 15 for timestamped
        val payloadSize = 1 + count * 8 // count + (dx+dy+dt)*2
        val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.BIG_ENDIAN)

        buf.put(count.toByte())
        for (delta in deltas.take(count)) {
            buf.putShort(delta.dx.toInt().toShort())
            buf.putShort(delta.dy.toInt().toShort())
            buf.putInt((delta.timestampUs and 0xFFFFFFFFL).toInt())
        }
        return buf.array()
    }

    data class TimestampedDelta(
        val dx: Float,
        val dy: Float,
        val timestampUs: Long
    )

    fun encodeKeyboardKey(event: KeyboardKeyEvent): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(event.keyCode)
        buf.put(event.action)
        buf.put(event.modifiers)
        return buf.array()
    }

    fun encodeKeyboardText(textBytes: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(2 + textBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(textBytes.size.toShort())
        buf.put(textBytes)
        return buf.array()
    }

    fun encodeGamepadState(event: GamepadStateEvent): ByteArray {
        val buf = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(event.buttonMask)
        buf.putShort(event.leftStickX)
        buf.putShort(event.leftStickY)
        buf.putShort(event.rightStickX)
        buf.putShort(event.rightStickY)
        buf.put(event.leftTrigger.toByte())
        buf.put(event.rightTrigger.toByte())
        return buf.array()
    }

    fun encodeGamepadStick(event: GamepadStickEvent): ByteArray {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(event.stickId)
        buf.put(0x00.toByte()) // Reserved
        buf.putShort(event.posX)
        buf.putShort(event.posY)
        return buf.array()
    }

    fun encodeGamepadButton(event: GamepadButtonEvent): ByteArray {
        val buf = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(event.buttonBit)
        buf.put(event.action)
        return buf.array()
    }

    fun encodeGamepadTrigger(event: GamepadTriggerEvent): ByteArray {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        buf.put(event.triggerId)
        buf.put(event.value.toByte())
        return buf.array()
    }

    fun encodeRawImu(event: RawImuEvent): ByteArray {
        val buf = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
        buf.putFloat(event.gyroX)
        buf.putFloat(event.gyroY)
        buf.putFloat(event.gyroZ)
        buf.putFloat(event.accelX)
        buf.putFloat(event.accelY)
        buf.putFloat(event.accelZ)
        return buf.array()
    }

    fun encodeGyroCalibrate(command: Byte = 0): ByteArray {
        val buf = ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN)
        buf.put(command)
        return buf.array()
    }

    // =========================================================================
    // NEW: ACTIVE WINDOW INFO ENCODING (Server -> Client)
    // Payload layout (Big-Endian):
    //   [0..3]    int32 processNameLen
    //   [4..N]    UTF-8 processName
    //   [N+1]     int32 windowTitleLen
    //   [N+2..]   UTF-8 windowTitle
    //   [end+1]   byte profileIdLen
    //   [end+2]   UTF-8 profileId (if auto-detected)
    // =========================================================================
    fun encodeActiveWindowInfo(
        processName: String = "",
        windowTitle: String = "",
        matchedProfileId: String? = null
    ): ByteArray {
        val procBytes = processName.toByteArray(StandardCharsets.UTF_8)
        val winBytes = windowTitle.toByteArray(StandardCharsets.UTF_8)
        val profileIdBytes = matchedProfileId?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)

        val buf = ByteBuffer.allocate(4 + procBytes.size + 4 + winBytes.size + 1 + profileIdBytes.size).order(ByteOrder.BIG_ENDIAN)

        buf.putInt(procBytes.size)
        buf.put(procBytes)
        buf.putInt(winBytes.size)
        buf.put(winBytes)
        buf.put(profileIdBytes.size.toByte())
        if (profileIdBytes.isNotEmpty()) {
            buf.put(profileIdBytes)
        }

        return buf.array()
    }

    // =========================================================================
    // NEW: GAME PROFILE SYNC ENCODING (Client <-> Server)
    // Payload layout:
    //   [0..3]   int32 profileJsonLen
    //   [4..N]   UTF-8 JSON profile string
    // =========================================================================
    fun encodeGameProfileSync(profileJson: String): ByteArray {
        val jsonBytes = profileJson.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(4 + jsonBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(jsonBytes.size)
        buf.put(jsonBytes)
        return buf.array()
    }

    // =========================================================================
    // NEW: MACRO TRIGGER ENCODING
    // Payload layout:
    //   [0..3]   int32 macroId (hash of macro id string)
    //   [4]      byte action (1=execute, 0=cancel)
    // =========================================================================
    fun encodeMacroTrigger(macroId: Int, action: Byte = 1): ByteArray {
        val buf = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(macroId)
        buf.put(action)
        return buf.array()
    }

    /**
     * Decodes incoming active window info payload (Server -> Client).
     */
    fun decodeActiveWindowInfo(payload: ByteArray): Triple<String, String, String?> {
        if (payload.isEmpty()) return Triple("", "", null)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val procLen = buf.int
        val procName = if (procLen > 0 && buf.hasRemaining()) {
            val bytes = ByteArray(procLen)
            buf.get(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } else ""
        val winLen = buf.int
        val winTitle = if (winLen > 0 && buf.hasRemaining()) {
            val bytes = ByteArray(winLen)
            buf.get(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } else ""
        val profileIdLen = buf.get().toInt()
        val matchedProfileId = if (profileIdLen > 0 && buf.hasRemaining()) {
            val bytes = ByteArray(profileIdLen)
            buf.get(bytes)
            String(bytes, StandardCharsets.UTF_8)
        } else null
        return Triple(procName, winTitle, matchedProfileId)
    }

    /**
     * Decodes incoming game profile sync payload.
     */
    fun decodeGameProfileSync(payload: ByteArray): String? {
        if (payload.isEmpty() || payload.size < 4) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val jsonLen = buf.int
        if (jsonLen <= 0 || jsonLen > payload.size - 4) return null
        val bytes = ByteArray(jsonLen)
        buf.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }
}
