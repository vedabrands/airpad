package com.airpad.app.network

import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.GamepadStateEvent
import com.airpad.app.data.models.GyroConfig
import com.airpad.app.data.models.GyroMode
import com.airpad.app.data.models.MouseButtonEvent
import com.airpad.app.data.models.MouseDeltaEvent
import com.airpad.app.data.models.MouseScrollEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class PacketEncoderTest {

    @Test
    fun testCreateFrameHeader() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val frame = PacketEncoder.createFrame(
            opcode = ProtocolConstants.OP_PAIR_REQUEST,
            sessionToken = 0x12345678L,
            sequence = 42L,
            flags = 0x05,
            payload = payload
        )

        assertEquals(ProtocolConstants.HEADER_SIZE + payload.size, frame.size)
        val buf = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN)

        assertEquals(ProtocolConstants.MAGIC_BYTES, buf.short)
        assertEquals(ProtocolConstants.PROTOCOL_VERSION, buf.get())
        assertEquals(0x05.toByte(), buf.get())
        assertEquals(42, buf.int)
        assertEquals(0x12345678, buf.int)
        assertEquals(ProtocolConstants.OP_PAIR_REQUEST, buf.get())
        assertEquals(0x00.toByte(), buf.get())
        assertEquals(4.toShort(), buf.short)

        val readPayload = ByteArray(4)
        buf.get(readPayload)
        assertArrayEquals(payload, readPayload)
    }

    @Test
    fun testEncodeHeartbeat() {
        val ts = 1718000000000L
        val payload = PacketEncoder.encodeHeartbeat(ts)

        assertEquals(8, payload.size)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        assertEquals(ts, buf.long)
    }

    @Test
    fun testEncodePairRequest() {
        val pin = 842915
        val uuid = UUID.fromString("12345678-1234-5678-1234-567812345678")
        val deviceName = "Pixel 8 Pro"

        val payload = PacketEncoder.encodePairRequest(pin, uuid, deviceName)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        assertEquals(pin, buf.int)
        assertEquals(uuid.mostSignificantBits, buf.long)
        assertEquals(uuid.leastSignificantBits, buf.long)
        val nameLen = buf.get().toInt()
        assertEquals(deviceName.length, nameLen)

        val nameBytes = ByteArray(nameLen)
        buf.get(nameBytes)
        assertEquals(deviceName, String(nameBytes, Charsets.UTF_8))
    }

    @Test
    fun testEncodeMouseDelta() {
        val event = MouseDeltaEvent(dx = 15.25f, dy = -10.5f)
        val payload = PacketEncoder.encodeMouseDelta(event)

        assertEquals(8, payload.size)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        assertEquals(15.toShort(), buf.short)
        assertEquals((-10).toShort(), buf.short)
        assertEquals((0.25f * 255.0f).toInt().toByte(), buf.get())
        assertEquals((0.5f * 255.0f).toInt().toByte(), buf.get())
    }

    @Test
    fun testEncodeMouseButton() {
        val event = MouseButtonEvent(
            buttonMask = ProtocolConstants.MOUSE_BTN_LEFT,
            action = ProtocolConstants.BTN_ACTION_PRESS
        )
        val payload = PacketEncoder.encodeMouseButton(event)

        assertEquals(2, payload.size)
        assertEquals(ProtocolConstants.MOUSE_BTN_LEFT, payload[0])
        assertEquals(ProtocolConstants.BTN_ACTION_PRESS, payload[1])
    }

    @Test
    fun testEncodeMouseScroll() {
        val event = MouseScrollEvent(scrollX = -120, scrollY = 240)
        val payload = PacketEncoder.encodeMouseScroll(event)

        assertEquals(4, payload.size)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        assertEquals(240.toShort(), buf.short) // scrollY
        assertEquals((-120).toShort(), buf.short) // scrollX
    }

    @Test
    fun testEncodeGamepadState() {
        val event = GamepadStateEvent(
            buttonMask = ProtocolConstants.GAMEPAD_BTN_A or ProtocolConstants.GAMEPAD_BTN_X,
            leftStickX = 16000,
            leftStickY = -16000,
            rightStickX = 32000,
            rightStickY = 0,
            leftTrigger = 200u,
            rightTrigger = 50u
        )
        val payload = PacketEncoder.encodeGamepadState(event)

        assertEquals(14, payload.size)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        assertEquals(ProtocolConstants.GAMEPAD_BTN_A or ProtocolConstants.GAMEPAD_BTN_X, buf.int)
        assertEquals(16000.toShort(), buf.short)
        assertEquals((-16000).toShort(), buf.short)
        assertEquals(32000.toShort(), buf.short)
        assertEquals(0.toShort(), buf.short)
        assertEquals(200.toByte(), buf.get())
        assertEquals(50.toByte(), buf.get())
    }

    @Test
    fun testEncodeModeSwitch() {
        val config = GyroConfig(
            mode = GyroMode.GYRO_TO_MOUSE,
            sensitivityX = 2.5f,
            sensitivityY = 1.8f,
            deadzone = 0.03f,
            smoothingFactor = 0.35f,
            invertX = false,
            invertY = true,
            ratchetActive = true
        )
        val payload = PacketEncoder.encodeModeSwitch(config, resetBias = false)

        assertEquals(20, payload.size)
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        assertEquals(GyroMode.GYRO_TO_MOUSE.value, buf.get())
        val flags = buf.get().toInt()
        assertEquals(0, flags and 0x01) // invertX = false
        assertEquals(0x02, flags and 0x02) // invertY = true
        assertEquals(0x04, flags and 0x04) // ratchetActive = true
        assertEquals(0, flags and 0x08) // resetBias = false

        buf.short // reserved
        assertEquals(2.5f, buf.float, 0.0001f)
        assertEquals(1.8f, buf.float, 0.0001f)
        assertEquals(0.03f, buf.float, 0.0001f)
        assertEquals(0.35f, buf.float, 0.0001f)
    }
}
