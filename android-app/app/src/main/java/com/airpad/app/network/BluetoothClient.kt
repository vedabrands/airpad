package com.airpad.app.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.ConnectionState
import com.airpad.app.data.models.DiscoveredServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// Well-known SPP UUID for serial port emulation
private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * Bluetooth Classic SPP client for AirPad wireless controller.
 * Provides plug-and-play connection to paired desktop agents.
 */
class BluetoothClient(
    private val scope: CoroutineScope,
    private val context: Context
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _packetsReceived = MutableSharedFlow<Pair<Byte, ByteArray>>(replay = 0)
    val packetsReceived: SharedFlow<Pair<Byte, ByteArray>> = _packetsReceived.asSharedFlow()

    private var adapter: BluetoothAdapter? = null
    private var connectedDevice: BluetoothDevice? = null
    private var btSocket: BluetoothSocket? = null
    private var connectJob: kotlinx.coroutines.Job? = null
    private var readJob: kotlinx.coroutines.Job? = null

    // Packet sequencing counter
    private var sequenceCounter: Long = 0

    init {
        adapter = BluetoothAdapter.getDefaultAdapter()
    }

    /**
     * Check if Bluetooth is supported on this device.
     */
    fun isBluetoothSupported(): Boolean {
        return adapter != null
    }

    /**
     * Check if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return adapter?.isEnabled == true
    }

    /**
     * Request Bluetooth enablement if not already on.
     * Returns a coroutine that waits for the state to change.
     */
    suspend fun requestEnableAndWait(timeoutMs: Long = 10000L) {
        if (isBluetoothEnabled()) return
        // Note: In a real app, you'd startActivityForResult to request enable
        // For now, we just poll
        val start = System.currentTimeMillis()
        while (!isBluetoothEnabled() && System.currentTimeMillis() - start < timeoutMs) {
            delay(500)
        }
    }

    /**
     * Check required Bluetooth permissions.
     */
    fun hasBluetoothPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        return fineLocation == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Discover bonded/paired Bluetooth devices and filter for AirPad agents.
     */
    fun getPairedDevices(): List<DiscoveredServer> {
        val devices = mutableListOf<DiscoveredServer>()
        adapter?.let { bt ->
            bt.bondedDevices.forEach { device ->
                if (isAirPadDevice(device)) {
                    devices.add(DiscoveredServer(
                        address = device.address,
                        port = ProtocolConstants.CONTROL_PORT,
                        name = "🔵 ${device.name ?: device.address}",
                        osType = 0,
                        requirePin = false,
                        lastSeenTimestamp = System.currentTimeMillis()
                    ))
                }
            }
        }
        return devices
    }

    /**
     * Connect to a discovered Bluetooth device using SPP.
     * This replaces the old PIN-based pairing with true plug-and-play.
     */
    fun connectToDevice(server: DiscoveredServer) {
        connectJob?.cancel()
        connectJob = scope.launch {
            _connectionState.value = ConnectionState.Pairing(server)
            try {
                val btDevice = adapter?.getRemoteDevice(server.address)
                    ?: run {
                        _connectionState.value = ConnectionState.Error("Device not found: ${server.address}")
                        return@launch
                    }

                btSocket = btDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                connectedDevice = btDevice

                // Connect (blocks until connected or timeout)
                btSocket?.connect()

                if (btSocket?.isConnected == true) {
                    _connectionState.value = ConnectionState.Connected(
                        hostAddress = server.address,
                        hostPort = ProtocolConstants.CONTROL_PORT,
                        hostName = server.name,
                        sessionToken = 0L
                    )
                    startReading()
                } else {
                    throw IOException("SPP socket not connected")
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error("Bluetooth connection failed: ${e.message ?: "unknown"}")
                disconnect()
            }
        }
    }

    /**
     * Send a packet over the Bluetooth SPP stream.
     */
    fun sendPacket(opcode: Byte, payload: ByteArray = ByteArray(0)) {
        sequenceCounter++
        val frame = PacketEncoder.createFrame(
            opcode = opcode,
            sessionToken = 0L,
            sequence = sequenceCounter,
            payload = payload
        )
        scope.launch {
            try {
                output?.write(frame)
                output?.flush()
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.Error("Send failed: ${e.message}")
                disconnect()
            }
        }
    }

    /**
     * Start background reading of incoming SPP data.
     */
    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(512)
            while (isActive && btSocket?.isConnected == true) {
                try {
                    val bytesRead = input?.read(buffer) ?: 0
                    if (bytesRead > 0) {
                        parseIncomingData(buffer, bytesRead)
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        _connectionState.value = ConnectionState.Error("Read error: ${e.message}")
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    private fun parseIncomingData(data: ByteArray, length: Int) {
        if (length < ProtocolConstants.HEADER_SIZE) return
        val buf = java.nio.ByteBuffer.wrap(data, 0, length).order(java.nio.ByteOrder.BIG_ENDIAN)
        val magic = buf.short
        if (magic != ProtocolConstants.MAGIC_BYTES) return

        val version = buf.get()
        if (version != ProtocolConstants.PROTOCOL_VERSION) return

        buf.get() // flags
        buf.int // sequence
        buf.int // sessionToken
        val opcode = buf.get()
        buf.get() // reserved
        val payloadLen = buf.short.toInt() and 0xFFFF

        if (payloadLen > 0 && buf.remaining() >= payloadLen) {
            val payload = ByteArray(payloadLen)
            buf.get(payload)
            scope.launch { _packetsReceived.emit(Pair(opcode, payload)) }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        readJob?.cancel()
        readJob = null
        try {
            btSocket?.close()
        } catch (_: IOException) {}
        btSocket = null
        connectedDevice = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun isAirPadDevice(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase() ?: ""
        return name.contains("airpad") || name.contains("desktop")
    }

    private val input: InputStream? get() = btSocket?.inputStream
    private val output: OutputStream? get() = btSocket?.outputStream
}
