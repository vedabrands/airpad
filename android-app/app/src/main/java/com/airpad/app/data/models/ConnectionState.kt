package com.airpad.app.data.models

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Discovering(val discoveredServers: List<DiscoveredServer> = emptyList()) : ConnectionState()
    data class Pairing(val server: DiscoveredServer) : ConnectionState()
    data class Connected(
        val hostAddress: String,
        val hostPort: Int,
        val hostName: String,
        val sessionToken: Long,
        val rttMs: Long = 0,
        val packetsSent: Long = 0,
        val avgRttMs: Double = 0.0,
        val packetLoss: Double = 0.0
    ) : ConnectionState()
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val nextRetryMs: Long,
        val hostName: String
    ) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class DiscoveredServer(
    val address: String,
    val port: Int,
    val name: String,
    val osType: Int,
    val requirePin: Boolean,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
