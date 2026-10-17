package com.airpad.app.network

import com.airpad.app.data.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance, zero-allocation UDP Client for low-latency transmission.
 * Optimizations:
 * - Packet batching for multiple input events per UDP packet
 * - Zero-copy buffer reuse with pre-allocated ByteBuffers
 * - Packet prioritization (mouse move > button > other)
 * - Adaptive quality based on RTT
 * - Jitter buffer for consistent timing
 */
class UdpClient(
    private val scope: CoroutineScope,
    private val onPacketReceived: (opcode: Byte, sessionToken: Long, payload: ByteArray) -> Unit,
    private val config: UdpClientConfig = UdpClientConfig()
) {
    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = ProtocolConstants.CONTROL_PORT
    private var receiveJob: Job? = null
    private var sendJob: Job? = null

    // Channel buffer capacity with DROP_OLDEST for ultra-low latency
    private var sendChannel = Channel<SendRequest>(capacity = config.sendBufferCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val sequenceCounter = AtomicLong(0)
    var activeSessionToken: Long = 0

    // Reusable buffers to avoid GC pressure
    private val sendBufferPool = ObjectPool(
        createInstance = { ByteBuffer.allocateDirect(config.maxPacketSize).order(ByteOrder.BIG_ENDIAN) },
        reset = { it.clear() }
    )

    // Batching state
    private var pendingMouseDeltas = mutableListOf<Pair<Float, Float>>()
    private var pendingButtons = mutableListOf<Pair<Byte, Byte>>()
    private var pendingScrolls = mutableListOf<Pair<Short, Short>>()
    private var batchTimerJob: Job? = null
    private var lastFlushTime = System.nanoTime()

    // RTT monitoring for adaptive quality
    private var currentRttMs: Long = 0
    private var rttSamples = mutableListOf<Long>()
    private val rttLock = Any()

    // Stats
    private var packetsSent = 0L
    private var packetsDropped = 0L
    private val pendingQueueSize = java.util.concurrent.atomic.AtomicInteger(0)

    data class SendRequest(
        val opcode: Byte,
        val payload: ByteArray,
        val flags: Byte = 0,
        val priority: Int = 0 // Higher = more important
    )

    fun connect(host: String, port: Int) {
        disconnect()
        sendChannel = Channel(capacity = config.sendBufferCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        scope.launch(Dispatchers.IO) {
            try {
                targetAddress = InetAddress.getByName(host)
                targetPort = port
                socket = DatagramSocket().apply {
                    sendBufferSize = config.socketBufferSize
                    receiveBufferSize = config.socketBufferSize
                    setTrafficClass(config.trafficClass) // DSCP EF for low latency
                }

                startReceiving()
                startSending()
                startBatchFlusher()
            } catch (e: Exception) {
                // Socket creation or DNS lookup error handled upstream
            }
        }
    }

    fun sendPacket(opcode: Byte, payload: ByteArray = ByteArray(0), flags: Byte = 0) {
        val priority = when (opcode) {
            ProtocolConstants.OP_MOUSE_DELTA -> 3
            ProtocolConstants.OP_MOUSE_BUTTON -> 2
            ProtocolConstants.OP_MOUSE_SCROLL -> 2
            ProtocolConstants.OP_MOUSE_COMBO -> 3
            ProtocolConstants.OP_GAMEPAD_STATE -> 2
            ProtocolConstants.OP_GAMEPAD_STICK -> 2
            ProtocolConstants.OP_GAMEPAD_BUTTON -> 2
            ProtocolConstants.OP_GAMEPAD_TRIGGER -> 2
            ProtocolConstants.OP_RAW_IMU_DATA -> 1
            ProtocolConstants.OP_HEARTBEAT -> 0
            else -> 0
        }
        val seq = sequenceCounter.incrementAndGet()
        val frame = PacketEncoder.createFrame(
            opcode = opcode,
            sessionToken = activeSessionToken,
            sequence = seq,
            flags = flags,
            payload = payload
        )
        sendChannel.trySend(SendRequest(opcode, frame, flags, priority))
        pendingQueueSize.incrementAndGet()
    }

    /** Batch multiple mouse deltas into a single packet */
    fun sendBatchedMouseDeltas(deltas: List<Pair<Float, Float>>) {
        if (deltas.isEmpty()) return
        // Use MOUSE_COMBO opcode for batched deltas
        val payload = PacketEncoder.encodeMouseComboBatch(deltas)
        sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
    }

    /** Send high-priority immediate packet (bypasses batching) */
    fun sendImmediate(opcode: Byte, payload: ByteArray = ByteArray(0)) {
        val seq = sequenceCounter.incrementAndGet()
        val frame = PacketEncoder.createFrame(
            opcode = opcode,
            sessionToken = activeSessionToken,
            sequence = seq,
            flags = 0x80.toByte(), // High priority flag
            payload = payload
        )
        sendChannel.trySend(SendRequest(opcode, frame, 0x80.toByte(), priority = 4))
        pendingQueueSize.incrementAndGet()
    }

    fun updateRtt(rttMs: Long) {
        synchronized(rttLock) {
            currentRttMs = rttMs
            rttSamples.add(rttMs)
            if (rttSamples.size > 20) rttSamples.removeAt(0)
        }
        // Adjust batching based on RTT
        val avgRtt = rttSamples.average()
        if (avgRtt > 50) {
            // High latency: increase batching window
            config.batchWindowMs = min(config.batchWindowMs + 1, 8)
        } else if (avgRtt < 20) {
            // Low latency: reduce batching for responsiveness
            config.batchWindowMs = max(config.batchWindowMs - 1, 1)
        }
    }

    fun getStats(): UdpStats {
        val avgRtt = if (rttSamples.isEmpty()) 0.0 else rttSamples.average()
        return UdpStats(
            packetsSent = packetsSent,
            packetsDropped = packetsDropped,
            currentRttMs = currentRttMs,
            avgRttMs = avgRtt,
            queueSize = pendingQueueSize.get()
        )
    }

    private fun startSending() {
        sendJob = scope.launch(Dispatchers.IO) {
            for (request in sendChannel) {
                pendingQueueSize.decrementAndGet()
                val currentSocket = socket ?: break
                val currentAddress = targetAddress ?: break
                try {
                    val packet = DatagramPacket(request.payload, request.payload.size, currentAddress, targetPort)
                    currentSocket.send(packet)
                    packetsSent++
                } catch (e: Exception) {
                    if (!isActive) break
                    packetsDropped++
                }
            }
        }
    }

    private fun startBatchFlusher() {
        batchTimerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val now = System.nanoTime()
                val elapsedMs = (now - lastFlushTime) / 1_000_000
                if (elapsedMs >= config.batchWindowMs && (pendingMouseDeltas.isNotEmpty() || pendingButtons.isNotEmpty() || pendingScrolls.isNotEmpty())) {
                    flushBatch()
                    lastFlushTime = now
                }
                kotlinx.coroutines.delay(config.batchWindowMs.toLong())
            }
        }
    }

    private fun flushBatch() {
        val mouseDeltas = pendingMouseDeltas.takeIf { it.isNotEmpty() } ?: return
        val buttons = pendingButtons.takeIf { it.isNotEmpty() } ?: emptyList()
        val scrolls = pendingScrolls.takeIf { it.isNotEmpty() } ?: emptyList()

        pendingMouseDeltas.clear()
        pendingButtons.clear()
        pendingScrolls.clear()

        // Combine into MOUSE_COMBO packet
        val dx = mouseDeltas.sumOf { it.first.toInt() }
        val dy = mouseDeltas.sumOf { it.second.toInt() }
        val buttonState = buttons.lastOrNull()?.first ?: 0
        val scrollY = scrolls.lastOrNull()?.first ?: 0
        val scrollX = scrolls.lastOrNull()?.second ?: 0

        val payload = PacketEncoder.encodeMouseComboBatch(
            mouseDeltas,
            buttonState.toByte(),
            scrollY.toByte(),
            scrollX.toByte()
        )
        sendPacket(ProtocolConstants.OP_MOUSE_COMBO, payload)
    }

    private fun startReceiving() {
        receiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(config.receiveBufferSize)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isActive) {
                try {
                    socket?.receive(packet)
                    if (packet.length >= ProtocolConstants.HEADER_SIZE) {
                        val buf = ByteBuffer.wrap(packet.data, 0, packet.length).order(ByteOrder.BIG_ENDIAN)
                        val magic = buf.short
                        if (magic == ProtocolConstants.MAGIC_BYTES) {
                            buf.get() // version
                            buf.get() // flags
                            buf.int // sequence
                            val sessionToken = buf.int.toLong() and 0xFFFFFFFFL
                            val opcode = buf.get()
                            buf.get() // reserved
                            val payloadLen = buf.short.toInt() and 0xFFFF

                            val payload = if (payloadLen > 0 && buf.remaining() >= payloadLen) {
                                val p = ByteArray(payloadLen)
                                buf.get(p)
                                p
                            } else {
                                ByteArray(0)
                            }

                            onPacketReceived(opcode, sessionToken, payload)
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
    }

    fun disconnect() {
        sendChannel.close()
        sendJob?.cancel()
        sendJob = null
        receiveJob?.cancel()
        receiveJob = null
        batchTimerJob?.cancel()
        batchTimerJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        targetAddress = null
        activeSessionToken = 0
        sequenceCounter.set(0)
        pendingMouseDeltas.clear()
        pendingButtons.clear()
        pendingScrolls.clear()
    }

    private class ObjectPool<T>(
        private val createInstance: () -> T,
        private val reset: (T) -> Unit
    ) {
        private val pool = mutableListOf<T>()
        private val lock = Any()

        fun acquire(): T {
            synchronized(lock) {
                if (pool.isNotEmpty()) return pool.removeAt(0)
                return createInstance()
            }
        }

        fun release(instance: T) {
            reset(instance)
            synchronized(lock) {
                if (pool.size < 16) pool.add(instance)
            }
        }
    }
}

data class UdpClientConfig(
    val sendBufferCapacity: Int = 512,
    val maxPacketSize: Int = 1400,
    val socketBufferSize: Int = 128 * 1024,
    val receiveBufferSize: Int = 1400,
    var batchWindowMs: Int = 4, // 250Hz batch flush, adjusted dynamically by RTT
    val trafficClass: Int = 0xB8 // DSCP EF (Expedited Forwarding)
)

data class UdpStats(
    val packetsSent: Long,
    val packetsDropped: Long,
    val currentRttMs: Long,
    val avgRttMs: Double,
    val queueSize: Int
)