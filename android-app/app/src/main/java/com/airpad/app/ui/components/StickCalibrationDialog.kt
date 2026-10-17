package com.airpad.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airpad.app.data.models.StickConfig
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.SuccessGreen
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

@Composable
fun StickCalibrationDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    viewModel: AirPadViewModel
) {
    if (!showDialog) return

    val calibrationState = remember { mutableStateOf(StickCalibrationState.Idle) }
    val calibrationProgress = remember { mutableStateOf(0) }
    val calibrationMessage = remember { mutableStateOf("") }

    StickCalibrationDialogContent(
        showDialog = showDialog,
        onDismiss = onDismiss,
        calibrationState = calibrationState.value,
        onCalibrationStateChanged = { calibrationState.value = it },
        calibrationProgress = calibrationProgress.value,
        onCalibrationProgressChanged = { calibrationProgress.value = it },
        calibrationMessage = calibrationMessage.value,
        onCalibrationMessageChanged = { calibrationMessage.value = it },
        viewModel = viewModel
    )

    Dimmer(onClick = { onDismiss() })
}

@Composable
private fun StickCalibrationDialogContent(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    calibrationState: StickCalibrationState,
    onCalibrationStateChanged: (StickCalibrationState) -> Unit,
    calibrationProgress: Int,
    onCalibrationProgressChanged: (Int) -> Unit,
    calibrationMessage: String,
    onCalibrationMessageChanged: (String) -> Unit,
    viewModel: AirPadViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stick Calibration", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.width(300.dp)) {
                when (calibrationState) {
                    StickCalibrationState.Idle -> IdleView(
                        onStartCalibration = {
                            onCalibrationStateChanged(StickCalibrationState.CalibratingCenter)
                            onCalibrationMessageChanged("Place sticks in center position")
                        }
                    )
                    StickCalibrationState.CalibratingCenter -> CalibratingView(
                        progress = calibrationProgress,
                        message = calibrationMessage,
                        onCalibrationProgressChanged = onCalibrationProgressChanged,
                        onComplete = {
                            onCalibrationStateChanged(StickCalibrationState.CalibratingEdges)
                            onCalibrationMessageChanged("Move sticks to outer edges")
                            onCalibrationProgressChanged(0)
                        }
                    )
                    StickCalibrationState.CalibratingEdges -> CalibratingView(
                        progress = calibrationProgress,
                        message = calibrationMessage,
                        onCalibrationProgressChanged = onCalibrationProgressChanged,
                        onComplete = {
                            onCalibrationStateChanged(StickCalibrationState.Applying)
                            onCalibrationMessageChanged("Applying calibration...")
                            onCalibrationProgressChanged(0)
                        }
                    )
                    StickCalibrationState.Applying -> SuccessView(
                        onFinish = {
                            onDismiss()
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun IdleView(
    onStartCalibration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Calibrate your thumbsticks for optimal precision",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Button(
            onClick = onStartCalibration,
            modifier = Modifier
                .width(120.dp)
                .height(40.dp)
        ) {
            Text("Start Calibration")
        }
    }
}

@Composable
private fun CalibratingView(
    progress: Int,
    message: String,
    onCalibrationProgressChanged: (Int) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier
                .width(200.dp)
                .height(4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        if (progress < 100) {
            LaunchedEffect(Unit) {
                var current = progress
                while (current < 100) {
                    delay(20)
                    current++
                    onCalibrationProgressChanged(current)
                }
                onComplete()
            }
        }
    }
}

@Composable
private fun SuccessView(
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = SuccessGreen,
            modifier = Modifier
                .size(48.dp)
        )
        Text(
            text = "Calibration Complete!",
            style = MaterialTheme.typography.titleMedium,
            color = SuccessGreen
        )
        Text(
            text = "Your stick settings have been updated.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Button(
            onClick = onFinish,
            modifier = Modifier
                .width(120.dp)
                .height(40.dp)
        ) {
            Text("Finish")
        }
    }
}

@Composable
private fun Dimmer(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0x80000000))
            .clickable(onClick = onClick)
    )
}

enum class StickCalibrationState {
    Idle,
    CalibratingCenter,
    CalibratingEdges,
    Applying
}
