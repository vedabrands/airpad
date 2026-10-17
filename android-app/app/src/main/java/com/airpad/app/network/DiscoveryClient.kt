package com.airpad.app.network

import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.DiscoveredServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class DiscoveryClient(
    private val scope: CoroutineScope
) {
    private val _discoveredServers = MutableStateFlow<Map<String, DiscoveredServer>>(emptyMap())
    val discoveredServers: StateFlow<Map<String, DiscoveredServer>> = _discoveredServers.asStateFlow()

    private var listenJob: Job? = null
    private var socket: DatagramSocket? = null

    fun startListening() {
        if (listenJob?.isActive == true) return

        listenJob = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(ProtocolConstants.DISCOVERY_PORT))
                }

                val buffer = ByteArray(512)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket?.receive(packet)
                        parseBeacon(packet.data, packet.length, packet.address.hostAddress ?: "")
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                // socket bind failed or network unavailable
            } finally {
                socket?.close()
                socket = null
            }
        }
    }

    private fun parseBeacon(data: ByteArray, length: Int, hostIp: String) {
        if (length < ProtocolConstants.HEADER_SIZE + 4) return

        val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
        val magic = buf.short
        if (magic != ProtocolConstants.MAGIC_BYTES) return

        val version = buf.get()
        if (version != ProtocolConstants.PROTOCOL_VERSION) return

        buf.get() // flags
        buf.int // sequence
        buf.int // sessionToken
        val opcode = buf.get()
        buf.get() // reserved
        val payloadLen = buf.short.toInt()

        if (opcode != ProtocolConstants.OP_DISCOVERY_BEACON) return
        if (buf.remaining() < payloadLen || payloadLen < 4) return

        val port = buf.short.toInt() and 0xFFFF
        val osType = buf.get().toInt()
        val requirePin = buf.get().toInt() != 0
        val nameLen = if (buf.hasRemaining()) buf.get().toInt() and 0xFF else 0

        val serverName = if (nameLen > 0 && buf.remaining() >= nameLen) {
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            String(nameBytes, StandardCharsets.UTF_8)
        } else {
            "AirPad Desktop ($hostIp)"
        }

        val server = DiscoveredServer(
            address = hostIp,
            port = port,
            name = serverName,
            osType = osType,
            requirePin = requirePin,
            lastSeenTimestamp = System.currentTimeMillis()
        )

        _discoveredServers.value = _discoveredServers.value + (hostIp to server)
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        socket?.close()
        socket = null
    }
}
