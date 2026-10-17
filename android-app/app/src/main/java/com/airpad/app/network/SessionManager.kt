package com.airpad.app.network

import android.os.Build
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.ConnectionState
import com.airpad.app.data.models.DiscoveredServer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class SessionManager(
    private val scope: CoroutineScope
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val clientUid = UUID.randomUUID()
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var udpClient: UdpClient? = null

    private var activeHost: String = ""
    private var activePort: Int = ProtocolConstants.CONTROL_PORT
    private var activeServerName: String = ""

    private var lastPairedServer: DiscoveredServer? = null
    private var lastPinCode: Int = 0
    private var lastHeartbeatAckTimestamp: Long = 0L

    val discoveryClient = DiscoveryClient(scope)

    companion object {
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val HEARTBEAT_LOSS_THRESHOLD_MS = 2500L // 5 missed heartbeats
    }

    init {
        discoveryClient.startListening()
    }

    fun initiatePairing(server: DiscoveredServer, pinCode: Int) {
        reconnectJob?.cancel()
        reconnectJob = null

        _connectionState.value = ConnectionState.Pairing(server)
        activeHost = server.address
        activePort = server.port
        activeServerName = server.name
        lastPairedServer = server
        lastPinCode = pinCode

        udpClient?.disconnect()
        udpClient = UdpClient(scope, onPacketReceived = { opcode, sessionToken, payload ->
            handleServerPacket(opcode, sessionToken, payload)
        })

        udpClient?.connect(server.address, server.port)

        scope.launch(Dispatchers.IO) {
            delay(100) // Allow socket binding
            val pairPayload = PacketEncoder.encodePairRequest(pinCode, clientUid, deviceName)
            udpClient?.sendPacket(ProtocolConstants.OP_PAIR_REQUEST, pairPayload)
        }
    }

    private fun handleServerPacket(opcode: Byte, sessionToken: Long, payload: ByteArray) {
        when (opcode) {
            ProtocolConstants.OP_PAIR_RESPONSE -> {
                if (payload.isNotEmpty()) {
                    val status = payload[0]
                    if (status == ProtocolConstants.PAIR_STATUS_SUCCESS) {
                        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                        buf.get() // status
                        buf.get() // reserved
                        val token = buf.int.toLong() and 0xFFFFFFFFL

                        udpClient?.activeSessionToken = token
                        lastHeartbeatAckTimestamp = System.currentTimeMillis()

                        reconnectJob?.cancel()
                        reconnectJob = null

                        _connectionState.value = ConnectionState.Connected(
                            hostAddress = activeHost,
                            hostPort = activePort,
                            hostName = activeServerName,
                            sessionToken = token
                        )
                        startHeartbeat()
                    } else {
                        val errMsg = when (status) {
                            ProtocolConstants.PAIR_STATUS_INVALID_PIN -> "Invalid 6-digit PIN entered"
                            ProtocolConstants.PAIR_STATUS_SERVER_BUSY -> "Server is busy with another device"
                            ProtocolConstants.PAIR_STATUS_VERSION_MISMATCH -> "Protocol version mismatch"
                            ProtocolConstants.PAIR_STATUS_REJECTED -> "Pairing rejected by host"
                            else -> "Pairing rejected (code: $status)"
                        }
                        _connectionState.value = ConnectionState.Error(errMsg)
                        udpClient?.disconnect()
                    }
                }
            }

            ProtocolConstants.OP_HEARTBEAT_ACK -> {
                if (payload.size >= 16) {
                    val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                    val clientTs = buf.long
                    buf.long // serverTs
                    val rtt = System.currentTimeMillis() - clientTs
                    lastHeartbeatAckTimestamp = System.currentTimeMillis()

                    udpClient?.updateRtt(rtt)

                    val current = _connectionState.value
                    if (current is ConnectionState.Connected) {
                        _connectionState.value = current.copy(rttMs = rtt)
                    }
                }
            }

            ProtocolConstants.OP_DISCONNECT -> {
                handleHostForcedDisconnect()
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            lastHeartbeatAckTimestamp = System.currentTimeMillis()
            while (isActive) {
                delay(ProtocolConstants.HEARTBEAT_INTERVAL_MS)

                // Check for heartbeat loss
                val timeSinceLastAck = System.currentTimeMillis() - lastHeartbeatAckTimestamp
                if (timeSinceLastAck > HEARTBEAT_LOSS_THRESHOLD_MS) {
                    triggerAutoReconnect()
                    break
                }

                val payload = PacketEncoder.encodeHeartbeat(System.currentTimeMillis())
                udpClient?.sendPacket(ProtocolConstants.OP_HEARTBEAT, payload)
            }
        }
    }

    /**
     * Executes exponential backoff reconnection if the connection drops.
     * Backoff schedule: 500ms -> 1000ms -> 2000ms -> 4000ms -> 8000ms
     */
    private fun triggerAutoReconnect() {
        val server = lastPairedServer ?: run {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        heartbeatJob?.cancel()
        heartbeatJob = null

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            var attempt = 1
            var backoffDelay = 500L

            while (isActive && attempt <= MAX_RECONNECT_ATTEMPTS) {
                _connectionState.value = ConnectionState.Reconnecting(
                    attempt = attempt,
                    maxAttempts = MAX_RECONNECT_ATTEMPTS,
                    nextRetryMs = backoffDelay,
                    hostName = server.name
                )

                delay(backoffDelay)

                // Re-bind UDP socket and re-send pair request
                udpClient?.disconnect()
                udpClient = UdpClient(scope, onPacketReceived = { opcode, sessionToken, payload ->
                    handleServerPacket(opcode, sessionToken, payload)
                })
                udpClient?.connect(server.address, server.port)
                delay(80)

                val pairPayload = PacketEncoder.encodePairRequest(lastPinCode, clientUid, deviceName)
                udpClient?.sendPacket(ProtocolConstants.OP_PAIR_REQUEST, pairPayload)

                // Wait up to 1200ms to see if connection is restored
                val waitStart = System.currentTimeMillis()
                while (System.currentTimeMillis() - waitStart < 1200L) {
                    if (_connectionState.value is ConnectionState.Connected) {
                        // Successfully restored
                        return@launch
                    }
                    delay(100)
                }

                attempt++
                backoffDelay *= 2L
            }

            // If all retry attempts exhausted
            _connectionState.value = ConnectionState.Error("Connection lost to ${server.name}. Reconnection failed after $MAX_RECONNECT_ATTEMPTS attempts.")
            udpClient?.disconnect()
            udpClient = null
        }
    }

    private fun handleHostForcedDisconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        udpClient?.disconnect()
        udpClient = null
        _connectionState.value = ConnectionState.Error("Disconnected by remote desktop host.")
    }

    fun cancelReconnection() {
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        udpClient?.disconnect()
        udpClient = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun sendModeSwitch(config: GyroConfig, resetBias: Boolean = false) {
        val payload = PacketEncoder.encodeModeSwitch(config, resetBias)
        udpClient?.sendPacket(ProtocolConstants.OP_MODE_SWITCH, payload)
    }

    fun sendMouseDelta(dx: Float, dy: Float) {
        val payload = PacketEncoder.encodeMouseDelta(MouseDeltaEvent(dx, dy))
        udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_DELTA, payload)
    }

    /**
     * Batched mouse delta sends multiple deltas in a single packet
     * to reduce per-packet overhead and achieve higher effective update rate.
     */
    fun sendBatchedMouseDeltas(deltas: List<Pair<Float, Float>>) {
        if (deltas.isEmpty()) return
        val payload = PacketEncoder.encodeMouseComboBatch(deltas)
        udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
    }

    /**
     * Immediate high-priority send that bypasses batching.
     * Used for button presses and critical events.
     */
    fun sendImmediate(opcode: Byte, payload: ByteArray = ByteArray(0)) {
        udpClient?.sendImmediate(opcode, payload)
    }

    fun sendMouseButton(buttonMask: Byte, action: Byte) {
        val payload = PacketEncoder.encodeMouseButton(MouseButtonEvent(buttonMask, action))
        udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_BUTTON, payload)
    }

    fun sendMouseScroll(scrollX: Short, scrollY: Short) {
        val payload = PacketEncoder.encodeMouseScroll(MouseScrollEvent(scrollX, scrollY))
        udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_SCROLL, payload)
    }

    fun sendMouseCombo(dx: Short, dy: Short, buttonState: Byte, scrollY: Byte, scrollX: Byte) {
        val payload = PacketEncoder.encodeMouseCombo(MouseComboEvent(dx, dy, buttonState, scrollY, scrollX))
        udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
    }

    fun sendKeyboardKey(keyCode: Short, action: Byte, modifiers: Byte = 0) {
        val payload = PacketEncoder.encodeKeyboardKey(KeyboardKeyEvent(keyCode, action, modifiers))
        udpClient?.sendPacket(ProtocolConstants.OP_KEYBOARD_KEY, payload)
    }

    fun sendKeyboardText(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = PacketEncoder.encodeKeyboardText(bytes)
        udpClient?.sendPacket(ProtocolConstants.OP_KEYBOARD_KEY, payload)
    }

    fun sendGamepadState(event: GamepadStateEvent) {
        val payload = PacketEncoder.encodeGamepadState(event)
        udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_STATE, payload)
    }

    fun sendGamepadStick(stickId: Byte, posX: Short, posY: Short) {
        val payload = PacketEncoder.encodeGamepadStick(GamepadStickEvent(stickId, posX, posY))
        udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_STICK, payload)
    }

    fun sendGamepadButton(buttonBit: Int, action: Byte) {
        val payload = PacketEncoder.encodeGamepadButton(GamepadButtonEvent(buttonBit, action))
        udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_BUTTON, payload)
    }

    fun sendGamepadTrigger(triggerId: Byte, value: UByte) {
        val payload = PacketEncoder.encodeGamepadTrigger(GamepadTriggerEvent(triggerId, value))
        udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_TRIGGER, payload)
    }

    fun sendRawImu(event: RawImuEvent) {
        val payload = PacketEncoder.encodeRawImu(event)
        udpClient?.sendPacket(ProtocolConstants.OP_RAW_IMU_DATA, payload)
    }

    fun sendGyroCalibrate(command: Byte = 0) {
        val payload = PacketEncoder.encodeGyroCalibrate(command)
        udpClient?.sendPacket(ProtocolConstants.OP_GYRO_CALIBRATE, payload)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        udpClient?.sendPacket(ProtocolConstants.OP_DISCONNECT)
        udpClient?.disconnect()
        udpClient = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
