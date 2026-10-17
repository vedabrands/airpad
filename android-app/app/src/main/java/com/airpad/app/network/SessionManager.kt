package com.airpad.app.network

import android.content.Context
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val scope: CoroutineScope,
    private val context: Context? = null
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Bluetooth client for plug-and-play connection
    private val bluetoothClient = context?.let { BluetoothClient(scope, it) }

    // UDP client as fallback for LAN mode
    private var udpClient: UdpClient? = null
    private val clientUid = UUID.randomUUID()
    private val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
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
        // Listen for Bluetooth packets if available
        bluetoothClient?.let { bt ->
            scope.launch {
                bt.packetsReceived.collect { (opcode, payload) ->
                    handleServerPacket(opcode, 0L, payload)
                }
            }
            scope.launch {
                bt.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }
        }
    }

    /**
     * Get Bluetooth discovery status.
     */
    val isBluetoothSupported get() = bluetoothClient?.isBluetoothSupported() == true
    val isBluetoothEnabled get() = bluetoothClient?.isBluetoothEnabled() == true

    /**
     * Get paired Bluetooth devices that are AirPad agents.
     */
    fun getPairedBluetoothDevices(): List<DiscoveredServer> {
        return bluetoothClient?.getPairedDevices() ?: emptyList()
    }

    /**
     * Connect via Bluetooth (plug-and-play - no PIN required).
     */
    fun connectViaBluetooth(server: DiscoveredServer) {
        bluetoothClient?.connectToDevice(server)
    }

    /**
     * Request Bluetooth enablement.
     */
    suspend fun requestBluetoothEnable() {
        bluetoothClient?.requestEnableAndWait()
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

                udpClient?.disconnect()
                udpClient = UdpClient(scope, onPacketReceived = { opcode, sessionToken, payload ->
                    handleServerPacket(opcode, sessionToken, payload)
                })
                udpClient?.connect(server.address, server.port)
                delay(80)

                val pairPayload = PacketEncoder.encodePairRequest(lastPinCode, clientUid, deviceName)
                udpClient?.sendPacket(ProtocolConstants.OP_PAIR_REQUEST, pairPayload)

                val waitStart = System.currentTimeMillis()
                while (System.currentTimeMillis() - waitStart < 1200L) {
                    if (_connectionState.value is ConnectionState.Connected) {
                        return@launch
                    }
                    delay(100)
                }

                attempt++
                backoffDelay *= 2L
            }

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

    /**
     * Send mode switch configuration via Bluetooth or UDP.
     */
    private fun isBluetoothActive(): Boolean =
        bluetoothClient?.connectionState?.value is ConnectionState.Connected

    fun sendModeSwitch(config: GyroConfig, resetBias: Boolean = false) {
        val payload = PacketEncoder.encodeModeSwitch(config, resetBias)
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_MODE_SWITCH, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_MODE_SWITCH, payload)
        }
    }

    fun sendMouseDelta(dx: Float, dy: Float) {
        val payload = PacketEncoder.encodeMouseDelta(MouseDeltaEvent(dx, dy))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_MOUSE_DELTA, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_DELTA, payload)
        }
    }

    fun sendBatchedMouseDeltas(deltas: List<Pair<Float, Float>>) {
        if (deltas.isEmpty()) return
        val payload = PacketEncoder.encodeMouseComboBatch(deltas)
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
        }
    }

    fun sendImmediate(opcode: Byte, payload: ByteArray = ByteArray(0)) {
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(opcode, payload)
        } else {
            udpClient?.sendImmediate(opcode, payload)
        }
    }

    fun sendMouseButton(buttonMask: Byte, action: Byte) {
        val payload = PacketEncoder.encodeMouseButton(MouseButtonEvent(buttonMask, action))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_MOUSE_BUTTON, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_BUTTON, payload)
        }
    }

    fun sendMouseScroll(scrollX: Short, scrollY: Short) {
        val payload = PacketEncoder.encodeMouseScroll(MouseScrollEvent(scrollX, scrollY))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_MOUSE_SCROLL, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_SCROLL, payload)
        }
    }

    fun sendMouseCombo(dx: Short, dy: Short, buttonState: Byte, scrollY: Byte, scrollX: Byte) {
        val payload = PacketEncoder.encodeMouseCombo(MouseComboEvent(dx, dy, buttonState, scrollY, scrollX))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
        }
    }

    fun sendKeyboardKey(keyCode: Short, action: Byte, modifiers: Byte = 0) {
        val payload = PacketEncoder.encodeKeyboardKey(KeyboardKeyEvent(keyCode, action, modifiers))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_KEYBOARD_KEY, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_KEYBOARD_KEY, payload)
        }
    }

    fun sendKeyboardText(text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = PacketEncoder.encodeKeyboardText(bytes)
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_KEYBOARD_KEY, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_KEYBOARD_KEY, payload)
        }
    }

    fun sendGamepadState(event: GamepadStateEvent) {
        val payload = PacketEncoder.encodeGamepadState(event)
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_STATE, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_STATE, payload)
        }
    }

    fun sendGamepadStick(stickId: Byte, posX: Short, posY: Short) {
        val payload = PacketEncoder.encodeGamepadStick(GamepadStickEvent(stickId, posX, posY))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_STICK, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_STICK, payload)
        }
    }

    fun sendGamepadButton(buttonBit: Int, action: Byte) {
        val payload = PacketEncoder.encodeGamepadButton(GamepadButtonEvent(buttonBit, action))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_BUTTON, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_BUTTON, payload)
        }
    }

    fun sendGamepadTrigger(triggerId: Byte, value: UByte) {
        val payload = PacketEncoder.encodeGamepadTrigger(GamepadTriggerEvent(triggerId, value))
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_TRIGGER, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_GAMEPAD_TRIGGER, payload)
        }
    }

    fun sendRawImu(event: RawImuEvent) {
        val payload = PacketEncoder.encodeRawImu(event)
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_RAW_IMU_DATA, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_RAW_IMU_DATA, payload)
        }
    }

    fun sendGyroCalibrate(command: Byte = 0) {
        val payload = PacketEncoder.encodeGyroCalibrate(command)
        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_GYRO_CALIBRATE, payload)
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_GYRO_CALIBRATE, payload)
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        if (isBluetoothActive()) {
            bluetoothClient?.sendPacket(ProtocolConstants.OP_DISCONNECT)
            bluetoothClient?.disconnect()
        } else {
            udpClient?.sendPacket(ProtocolConstants.OP_DISCONNECT)
            udpClient?.disconnect()
            udpClient = null
        }

        _connectionState.value = ConnectionState.Disconnected
    }
}
