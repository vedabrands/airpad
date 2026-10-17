package com.airpad.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.data.models.ControlProfile
import com.airpad.app.data.models.GyroHorizontalAxis
import com.airpad.app.data.models.GyroMode
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.AccentTeal
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.BorderSubtle
import com.airpad.app.ui.theme.DangerRed
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.SurfaceHighlight
import com.airpad.app.ui.theme.TextMuted
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: AirPadViewModel,
    modifier: Modifier = Modifier
) {
    val allProfiles by viewModel.allProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val touchConfig by viewModel.touchConfig.collectAsState()
    val gyroConfig by viewModel.gyroConfig.collectAsState()
    val stickConfig by viewModel.stickConfig.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Save As Custom Profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a unique name for your new control profile:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        placeholder = { Text("e.g. Competitive Valorant", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newProfileName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createCustomProfile(trimmed)
                            newProfileName = ""
                            showCreateDialog = false
                            viewModel.performHapticHeavyClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save Profile", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ==========================================
        // SECTION 1: PROFILE MANAGEMENT
        // ==========================================
        SectionHeader(title = "CONTROL PROFILES & PRESETS")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Active Profile Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activeProfile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (activeProfile.isCustom) "User Custom Profile" else "Built-in Preset",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeProfile.isCustom) PrimaryBlue else AccentTeal
                        )
                    }

                    // Profile Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeProfile.isCustom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DangerRed.copy(alpha = 0.15f))
                                    .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.performHapticHeavyClick()
                                        viewModel.deleteCustomProfile(activeProfile.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Delete",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DangerRed
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlue.copy(alpha = 0.15f))
                                .border(1.dp, PrimaryBlue, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.performHapticButtonPress()
                                    showCreateDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "+ Save As New",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                        }
                    }
                }

                // Profile Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allProfiles.forEach { profile ->
                        val isSelected = profile.id == activeProfile.id
                        ProfileChip(
                            profile = profile,
                            isSelected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    viewModel.performHapticHeavyClick()
                                    viewModel.selectProfile(profile.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 2: TOUCHPAD & TRACKPAD CALIBRATION
        // ==========================================
        SectionHeader(title = "TRACKPAD & TOUCH CALIBRATION")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Touch Sensitivity
                SettingSliderRow(
                    label = "Touch Tracking Sensitivity",
                    value = touchConfig.sensitivity,
                    valueFormat = "%.2fx",
                    valueRange = 0.2f..3.5f,
                    onValueChange = { viewModel.updateTouchConfig(touchConfig.copy(sensitivity = it)) }
                )

                // Touch Deadzone
                SettingSliderRow(
                    label = "Hand Tremor Deadzone",
                    value = touchConfig.deadzone,
                    valueFormat = "%.2f px",
                    valueRange = 0.0f..3.0f,
                    onValueChange = { viewModel.updateTouchConfig(touchConfig.copy(deadzone = it)) }
                )

                // Touch EMA Smoothing
                SettingSliderRow(
                    label = "Cursor Movement Smoothing (EMA)",
                    value = touchConfig.smoothingFactor,
                    valueFormat = "%.2f",
                    valueRange = 0.0f..0.8f,
                    onValueChange = { viewModel.updateTouchConfig(touchConfig.copy(smoothingFactor = it)) }
                )

                // Invert Scroll Direction
                SettingSwitchRow(
                    title = "Invert Scroll Direction",
                    description = "Natural two-finger wheel scrolling",
                    checked = touchConfig.invertScroll,
                    onCheckedChange = { viewModel.updateTouchConfig(touchConfig.copy(invertScroll = it)) }
                )

                // Invert Touch X
                SettingSwitchRow(
                    title = "Invert Touch X-Axis",
                    description = "Reverse horizontal touchpad motion",
                    checked = touchConfig.invertX,
                    onCheckedChange = { viewModel.updateTouchConfig(touchConfig.copy(invertX = it)) }
                )

                // Invert Touch Y
                SettingSwitchRow(
                    title = "Invert Touch Y-Axis",
                    description = "Reverse vertical touchpad motion",
                    checked = touchConfig.invertY,
                    onCheckedChange = { viewModel.updateTouchConfig(touchConfig.copy(invertY = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 3: GYRO & MOTION CONFIGURATION
        // ==========================================
        SectionHeader(title = "GYRO & MOTION CONFIGURATION")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Gyro Operating Mode
                Text(
                    text = "Gyro Operating Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentedTab("OFF", gyroConfig.mode == GyroMode.OFF, Modifier.weight(1f)) {
                        viewModel.performHapticHeavyClick()
                        viewModel.setGyroMode(GyroMode.OFF)
                    }
                    SegmentedTab("TO MOUSE", gyroConfig.mode == GyroMode.GYRO_TO_MOUSE, Modifier.weight(1f)) {
                        viewModel.performHapticHeavyClick()
                        viewModel.setGyroMode(GyroMode.GYRO_TO_MOUSE)
                    }
                    SegmentedTab("TO STICK", gyroConfig.mode == GyroMode.GYRO_TO_STICK, Modifier.weight(1f)) {
                        viewModel.performHapticHeavyClick()
                        viewModel.setGyroMode(GyroMode.GYRO_TO_STICK)
                    }
                }

                // Horizontal Steering Axis
                Text(
                    text = "Horizontal Motion Axis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentedTab(
                        label = "Yaw (Laser Pointer / Z)",
                        isSelected = gyroConfig.horizontalAxis == GyroHorizontalAxis.YAW,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.performHapticButtonPress()
                        viewModel.updateGyroConfig(gyroConfig.copy(horizontalAxis = GyroHorizontalAxis.YAW))
                    }
                    SegmentedTab(
                        label = "Roll (Steering Wheel / Y)",
                        isSelected = gyroConfig.horizontalAxis == GyroHorizontalAxis.ROLL,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.performHapticButtonPress()
                        viewModel.updateGyroConfig(gyroConfig.copy(horizontalAxis = GyroHorizontalAxis.ROLL))
                    }
                }

                // X Sensitivity
                SettingSliderRow(
                    label = "X-Axis Sensitivity (Yaw/Roll)",
                    value = gyroConfig.sensitivityX,
                    valueFormat = "%.1fx",
                    valueRange = 0.1f..5.0f,
                    onValueChange = { viewModel.updateGyroConfig(gyroConfig.copy(sensitivityX = it)) }
                )

                // Y Sensitivity
                SettingSliderRow(
                    label = "Y-Axis Sensitivity (Pitch)",
                    value = gyroConfig.sensitivityY,
                    valueFormat = "%.1fx",
                    valueRange = 0.1f..5.0f,
                    onValueChange = { viewModel.updateGyroConfig(gyroConfig.copy(sensitivityY = it)) }
                )

                // Deadzone Threshold
                SettingSliderRow(
                    label = "Deadzone Threshold",
                    value = gyroConfig.deadzone,
                    valueFormat = "%.3f rad/s",
                    valueRange = 0.00f..0.10f,
                    onValueChange = { viewModel.updateGyroConfig(gyroConfig.copy(deadzone = it)) }
                )

                // Smoothing EMA Factor
                SettingSliderRow(
                    label = "Smoothing Factor (EMA)",
                    value = gyroConfig.smoothingFactor,
                    valueFormat = "%.2f",
                    valueRange = 0.05f..0.80f,
                    onValueChange = { viewModel.updateGyroConfig(gyroConfig.copy(smoothingFactor = it)) }
                )

                // Invert Pitch
                SettingSwitchRow(
                    title = "Invert Pitch (Y-Axis)",
                    description = "Invert vertical tilt gyro deflection",
                    checked = gyroConfig.invertY,
                    onCheckedChange = { viewModel.updateGyroConfig(gyroConfig.copy(invertY = it)) }
                )

                // Invert Yaw/Roll
                SettingSwitchRow(
                    title = "Invert Horizontal Axis",
                    description = "Invert horizontal rotation deflection",
                    checked = gyroConfig.invertX,
                    onCheckedChange = { viewModel.updateGyroConfig(gyroConfig.copy(invertX = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 4: VIRTUAL THUMBSTICKS CALIBRATION
        // ==========================================
        SectionHeader(title = "VIRTUAL THUMBSTICKS CALIBRATION")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Stick Sensitivity X
                SettingSliderRow(
                    label = "Stick Horizontal Sensitivity",
                    value = stickConfig.sensitivityX,
                    valueFormat = "%.2fx",
                    valueRange = 0.2f..3.0f,
                    onValueChange = { viewModel.updateStickConfig(stickConfig.copy(sensitivityX = it)) }
                )

                // Stick Sensitivity Y
                SettingSliderRow(
                    label = "Stick Vertical Sensitivity",
                    value = stickConfig.sensitivityY,
                    valueFormat = "%.2fx",
                    valueRange = 0.2f..3.0f,
                    onValueChange = { viewModel.updateStickConfig(stickConfig.copy(sensitivityY = it)) }
                )

                // Radial Deadzone
                SettingSliderRow(
                    label = "Radial Deadzone Threshold",
                    value = stickConfig.deadzone,
                    valueFormat = "%.2f",
                    valueRange = 0.00f..0.35f,
                    onValueChange = { viewModel.updateStickConfig(stickConfig.copy(deadzone = it)) }
                )

                // Stick Smoothing Factor
                SettingSliderRow(
                    label = "Stick EMA Smoothing Factor",
                    value = stickConfig.smoothingFactor,
                    valueFormat = "%.2f",
                    valueRange = 0.00f..0.50f,
                    onValueChange = { viewModel.updateStickConfig(stickConfig.copy(smoothingFactor = it)) }
                )

                // Invert Stick Y
                SettingSwitchRow(
                    title = "Invert Stick Y-Axis",
                    description = "Invert vertical thumbstick deflection (Flight Sim / FPS Look)",
                    checked = stickConfig.invertY,
                    onCheckedChange = { viewModel.updateStickConfig(stickConfig.copy(invertY = it)) }
                )

                // Invert Stick X
                SettingSwitchRow(
                    title = "Invert Stick X-Axis",
                    description = "Invert horizontal thumbstick deflection",
                    checked = stickConfig.invertX,
                    onCheckedChange = { viewModel.updateStickConfig(stickConfig.copy(invertX = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 5: PERFORMANCE & LATENCY TUNING
        // ==========================================
        SectionHeader(title = "PERFORMANCE & LATENCY TUNING")
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Touch Acceleration
                SettingSwitchRow(
                    title = "Touch Acceleration Curve",
                    description = "Non-linear sensitivity boost for fast swipes",
                    checked = touchConfig.accelerationEnabled,
                    onCheckedChange = { viewModel.updateTouchConfig(touchConfig.copy(accelerationEnabled = it)) }
                )

                // Sub-pixel precision
                SettingSwitchRow(
                    title = "Sub-pixel Precision",
                    description = "Accumulate fractional movement for ultra-smooth cursor",
                    checked = touchConfig.subpixelPrecision,
                    onCheckedChange = { viewModel.updateTouchConfig(touchConfig.copy(subpixelPrecision = it)) }
                )

                // Prediction
                SettingSwitchRow(
                    title = "Movement Prediction",
                    description = "Reduce perceived latency by extrapolating velocity (2 frames)",
                    checked = touchConfig.predictionEnabled,
                    onCheckedChange = { viewModel.updateTouchConfig(touchConfig.copy(predictionEnabled = it)) }
                )

                // Gyro Auto-Drift Calibration
                SettingSwitchRow(
                    title = "Gyro Auto-Drift Calibration",
                    description = "Continuously learn and remove gyroscope bias at rest",
                    checked = true, // Always enabled in new implementation
                    onCheckedChange = { /* Always on */ }
                )

                // Reset Gyro Bias
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Reset Gyro Bias", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(text = "Hold phone still for 1s to recalibrate zero point", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            viewModel.performHapticHeavyClick()
                            // Trigger recalibration via sensor fusion manager
                            // This is exposed through ViewModel if needed
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        androidx.compose.material3.Text("Recalibrate", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
fun ProfileChip(
    profile: ControlProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) PrimaryBlue else SurfaceHighlight)
            .border(
                1.dp,
                if (isSelected) PrimaryBlue else BorderSubtle,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White
                        else if (profile.isCustom) PrimaryBlue
                        else AccentTeal
                    )
            )
            Text(
                text = profile.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) TextPrimary else TextSecondary
            )
        }
    }
}

@Composable
fun SegmentedTab(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) PrimaryBlue else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) TextPrimary else TextSecondary
        )
    }
}

@Composable
fun SettingSliderRow(
    label: String,
    value: Float,
    valueFormat: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = String.format(valueFormat, value),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = AccentTeal
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryBlue,
                activeTrackColor = PrimaryBlue,
                inactiveTrackColor = SurfaceHighlight
            )
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(text = description, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceHighlight
            )
        )
    }
}
