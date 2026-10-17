package com.airpad.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.airpad.app.data.models.ConnectionState
import com.airpad.app.data.models.DiscoveredServer
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.AccentTeal
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.BorderSubtle
import com.airpad.app.ui.theme.DangerRed
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.SuccessGreen
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.SurfaceHighlight
import com.airpad.app.ui.theme.TextMuted
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextSecondary

@Composable
fun ConnectionScreen(
    viewModel: AirPadViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()
    val bluetoothDevices by viewModel.bluetoothDevices.collectAsState()

    var selectedServerForPin by remember { mutableStateOf<DiscoveredServer?>(null) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("54321") }
    var manualPin by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        // Status Header Card
        ConnectionStatusCard(
            connectionState = connectionState,
            onDisconnect = { viewModel.disconnect() },
            onCancelReconnect = { viewModel.cancelReconnection() }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Bluetooth (Plug-and-Play) Section ──
        BluetoothSection(
            isSupported = viewModel.isBluetoothSupported,
            isEnabled = viewModel.isBluetoothEnabled,
            devices = bluetoothDevices,
            onEnable = { viewModel.refreshBluetoothDevices() },
            onConnect = { viewModel.connectViaBluetooth(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── LAN Fallback Section ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DISCOVERED AGENTS (LAN UDP 54320)",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (discoveredServers.isEmpty()) "Scanning..." else "${discoveredServers.size} found",
                style = MaterialTheme.typography.labelSmall,
                color = AccentTeal
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Server List / Scanner Box
        if (discoveredServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No LAN agents found — use Bluetooth above",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveredServers) { server ->
                    ServerListItem(
                        server = server,
                        isConnected = (connectionState as? ConnectionState.Connected)?.hostAddress == server.address,
                        onClick = { selectedServerForPin = server }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manual Connection Button
        Button(
            onClick = { showManualDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceHighlight,
                contentColor = TextPrimary
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Text(text = "Connect via Manual IP & 6-Digit PIN", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }

    // PIN Input Dialog for Discovered Server
    selectedServerForPin?.let { server ->
        PinInputDialog(
            title = "Pair with ${server.name}",
            subtitle = "${server.address}:${server.port}",
            onDismiss = { selectedServerForPin = null },
            onConfirm = { pinCode ->
                viewModel.connectToServer(server, pinCode)
                selectedServerForPin = null
            }
        )
    }

    // Manual Connection Dialog
    if (showManualDialog) {
        Dialog(onDismissRequest = { showManualDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Manual Connection",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("Desktop IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        colors = outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = outlinedTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = manualPin,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) manualPin = it },
                            label = { Text("6-Digit PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = outlinedTextFieldColors(),
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showManualDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val port = manualPort.toIntOrNull() ?: 54321
                                val pin = manualPin.toIntOrNull() ?: 0
                                if (manualIp.isNotBlank()) {
                                    viewModel.connectManual(manualIp.trim(), port, pin)
                                    showManualDialog = false
                                }
                            },
                            enabled = manualIp.isNotBlank() && manualPin.length == 6,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onDisconnect: () -> Unit,
    onCancelReconnect: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusDotColor = when (connectionState) {
                        is ConnectionState.Connected -> SuccessGreen
                        is ConnectionState.Pairing -> PrimaryBlue
                        is ConnectionState.Discovering -> AccentTeal
                        is ConnectionState.Reconnecting -> Color(0xFFFFB300) // Amber
                        is ConnectionState.Error -> DangerRed
                        ConnectionState.Disconnected -> TextMuted
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (connectionState) {
                            is ConnectionState.Connected -> "CONNECTED"
                            is ConnectionState.Pairing -> "PAIRING..."
                            is ConnectionState.Discovering -> "DISCOVERING..."
                            is ConnectionState.Reconnecting -> "RECONNECTING (${(connectionState as ConnectionState.Reconnecting).attempt}/${(connectionState as ConnectionState.Reconnecting).maxAttempts})"
                            is ConnectionState.Error -> "CONNECTION ERROR"
                            ConnectionState.Disconnected -> "NOT CONNECTED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusDotColor
                    )
                }

                if (connectionState is ConnectionState.Connected) {
                    Text(
                        text = "RTT: ${connectionState.rttMs}ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = AccentTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (connectionState) {
                is ConnectionState.Connected -> {
                    Text(
                        text = connectionState.hostName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${connectionState.hostAddress}:${connectionState.hostPort}  •  Token: 0x${connectionState.sessionToken.toString(16).uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed.copy(alpha = 0.15f),
                            contentColor = DangerRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                is ConnectionState.Reconnecting -> {
                    Text(
                        text = "Reconnecting to ${connectionState.hostName}...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wi-Fi link dropped. Attempt ${connectionState.attempt} of ${connectionState.maxAttempts} (next in ${connectionState.nextRetryMs}ms)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB300)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onCancelReconnect,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceHighlight,
                            contentColor = TextSecondary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Cancel Reconnection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                is ConnectionState.Pairing -> {
                    Text(
                        text = "Authenticating with ${connectionState.server.name}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                is ConnectionState.Error -> {
                    Text(
                        text = connectionState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DangerRed
                    )
                }
                is ConnectionState.Discovering, ConnectionState.Disconnected -> {
                    Text(
                        text = "Select a detected agent from LAN broadcast or enter IP manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun BluetoothSection(
    isSupported: Boolean,
    isEnabled: Boolean,
    devices: List<DiscoveredServer>,
    onEnable: () -> Unit,
    onConnect: (DiscoveredServer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                val statusColor = when {
                    !isSupported -> TextMuted
                    !isEnabled -> DangerRed
                    devices.isNotEmpty() -> SuccessGreen
                    else -> AccentTeal
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        !isSupported -> "Not supported"
                        !isEnabled -> "Bluetooth off"
                        devices.isNotEmpty() -> "${devices.size} paired"
                        else -> "Scanning..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            if (!isSupported) {
                Text("No BT hardware", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            } else if (!isEnabled) {
                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Enable Bluetooth", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isSupported) {
            Text(
                text = "Bluetooth not available on this device. Use LAN or manual connection.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        } else if (!isEnabled) {
            Text(
                text = "Turn on Bluetooth to connect wirelessly to your desktop.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        } else if (devices.isEmpty()) {
            Text(
                text = "No paired AirPad agents found. Pair your PC in Windows Bluetooth settings first.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(devices) { server ->
                    BluetoothDeviceItem(server = server, onConnect = { onConnect(server) })
                }
            }
        }
    }
}

@Composable
fun BluetoothDeviceItem(
    server: DiscoveredServer,
    onConnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHighlight)
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .clickable { onConnect() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📡",
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Bluetooth SPP • No PIN required",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen
                    )
                }
            }
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = TextPrimary
                ),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Connect", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ServerListItem(
    server: DiscoveredServer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isConnected) SurfaceHighlight else SurfaceDark)
            .border(1.dp, if (isConnected) PrimaryBlue else BorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${server.address}:${server.port} • Port ${server.port}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) SuccessGreen else PrimaryBlue
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text(if (isConnected) "Connected" else "Pair PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PinInputDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var pinText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) pinText = it },
                    placeholder = { Text("6-Digit PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = outlinedTextFieldColors(),
                    modifier = Modifier.width(180.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val pin = pinText.toIntOrNull()
                            if (pin != null) onConfirm(pin)
                        },
                        enabled = pinText.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Pair & Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryBlue,
    unfocusedBorderColor = BorderSubtle,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = PrimaryBlue,
    unfocusedLabelColor = TextSecondary,
    cursorColor = PrimaryBlue
)
