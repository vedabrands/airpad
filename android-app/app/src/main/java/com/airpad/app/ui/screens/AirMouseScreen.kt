package com.airpad.app.ui.screens

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.ConnectionState
import com.airpad.app.data.models.GyroMode
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.AccentTeal
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.BorderSubtle
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.PrimaryBlueHover
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.SurfaceHighlight
import com.airpad.app.ui.theme.TextMuted
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextSecondary
import kotlin.math.hypot

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AirMouseScreen(
    viewModel: AirPadViewModel,
    modifier: Modifier = Modifier
) {
    val gyroConfig by viewModel.gyroConfig.collectAsState()
    val touchConfig by viewModel.touchConfig.collectAsState()
    val gestureFeedback by viewModel.gestureFeedback.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var showSensitivitySlider by remember { mutableStateOf(false) }
    var isRatchetHeld by remember { mutableStateOf(false) }
    val isGyroActive = gyroConfig.mode == GyroMode.GYRO_TO_MOUSE

    // Latency indicator for performance monitoring
    val rttMs = (connectionState as? ConnectionState.Connected)?.rttMs ?: 0L
    val latencyColor = when {
        rttMs == 0L -> TextMuted
        rttMs < 20L -> AccentTeal
        rttMs < 50L -> PrimaryBlue
        rttMs < 100L -> TextMuted
        else -> Color(0xFFE05561) // Red for high latency
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(14.dp)
    ) {
        // --- Top Bar: Mode Title + Sensitivity Quick Toggle + Gyro Aim Toggle ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mouse,
                    contentDescription = "Mouse Mode",
                    tint = AccentTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PRECISION TRACKPAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Latency Indicator
                if (rttMs > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(latencyColor.copy(alpha = 0.15f))
                            .border(1.dp, latencyColor, RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${rttMs}ms",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = latencyColor
                        )
                    }
                }

                // Sensitivity Slider Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (showSensitivitySlider) PrimaryBlue.copy(alpha = 0.2f) else SurfaceDark)
                        .border(
                            1.dp,
                            if (showSensitivitySlider) PrimaryBlue else BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { showSensitivitySlider = !showSensitivitySlider }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sensitivity",
                            tint = if (showSensitivitySlider) PrimaryBlue else TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format("%.1f", touchConfig.sensitivity)}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (showSensitivitySlider) PrimaryBlue else TextMuted
                        )
                    }
                }

                // Gyro Aim Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isGyroActive) AccentTeal.copy(alpha = 0.15f) else SurfaceDark)
                        .border(
                            1.dp,
                            if (isGyroActive) AccentTeal else BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            viewModel.performHapticHeavyClick()
                            viewModel.toggleGyroForMouse()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isGyroActive) AccentTeal else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGyroActive) "Gyro: ON" else "Gyro: OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isGyroActive) AccentTeal else TextMuted
                        )
                    }
                }
            }
        }

        // --- Collapsible Quick Sensitivity Adjustment Slider ---
        AnimatedVisibility(visible = showSensitivitySlider) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Touch Sensitivity",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = String.format("%.2fx", touchConfig.sensitivity),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = touchConfig.sensitivity,
                    onValueChange = { viewModel.updateTouchConfig(touchConfig.copy(sensitivity = it)) },
                    valueRange = 0.2f..3.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = SurfaceHighlight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Main Touchpad Area & Dedicated Vertical Scroll Strip ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // High-Precision Multi-Touch Touchpad Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .graphicsLayer {
                        // GPU-accelerated rendering layer to reduce composition overhead
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SurfaceHighlight.copy(alpha = 0.25f),
                                SurfaceDark
                            )
                        )
                    )
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .pointerInteropFilter { event ->
                        handleTouchpadEvent(event, viewModel)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Subtle Touchpad Guide Text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "1-Finger Drag → Move Cursor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "1-Finger Tap → Left Click",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "2-Finger Tap → Right Click",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "2-Finger Drag → Scroll",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                // Dynamic On-Screen Gesture Feedback Indicator Badge
                androidx.compose.animation.AnimatedVisibility(
                    visible = gestureFeedback != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    val currentFeedback = gestureFeedback
                    if (currentFeedback != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceHighlight.copy(alpha = 0.92f))
                                .border(1.dp, AccentTeal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = currentFeedback,
                                color = AccentTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Dedicated Vertical Precision Scroll Strip
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .pointerInteropFilter { event ->
                        handleScrollStripEvent(event, viewModel)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)
                ) {
                    Text("▲", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "S\nC\nR\nO\nL\nL",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp
                    )
                    Text("▼", fontSize = 10.sp, color = TextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Gyro Ratchet Button (Hold to steer with Phone Gyro when enabled) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isRatchetHeld) PrimaryBlueHover else SurfaceDark)
                .border(
                    1.dp,
                    if (isRatchetHeld) AccentTeal else BorderSubtle,
                    RoundedCornerShape(12.dp)
                )
                .pointerInteropFilter { motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isRatchetHeld = true
                            viewModel.performHapticTick()
                            viewModel.setRatchetActive(true)
                            viewModel.triggerGestureFeedback("🎯 Gyro Ratchet Active")
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isRatchetHeld = false
                            viewModel.setRatchetActive(false)
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRatchetHeld) "GYRO ACTIVE (TRACKING MOTION)" else "HOLD FOR GYRO RATCHET",
                    fontWeight = FontWeight.Bold,
                    color = if (isRatchetHeld) Color.White else TextPrimary,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (isGyroActive) "Tilt phone while holding to steer cursor" else "Enable Gyro mode in header to use tilt",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRatchetHeld) Color.White.copy(alpha = 0.8f) else TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Bottom Tactile Buttons: Left Click & Right Click ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            // Left Click Button
            TactileMouseButton(
                label = "LEFT CLICK",
                accentColor = PrimaryBlue,
                modifier = Modifier.weight(1f)
            ) { pressed ->
                val action = if (pressed) ProtocolConstants.ACTION_PRESS else ProtocolConstants.ACTION_RELEASE
                viewModel.sendMouseButton(ProtocolConstants.MOUSE_BTN_LEFT, action)
                if (pressed) {
                    viewModel.performHapticButtonPress()
                    viewModel.triggerGestureFeedback("🖱️ Left Click")
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Click Button
            TactileMouseButton(
                label = "RIGHT CLICK",
                accentColor = AccentTeal,
                modifier = Modifier.weight(1f)
            ) { pressed ->
                val action = if (pressed) ProtocolConstants.ACTION_PRESS else ProtocolConstants.ACTION_RELEASE
                viewModel.sendMouseButton(ProtocolConstants.MOUSE_BTN_RIGHT, action)
                if (pressed) {
                    viewModel.performHapticButtonPress()
                    viewModel.triggerGestureFeedback("🖱️ Right Click")
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TactileMouseButton(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onPressStateChange: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) accentColor else SurfaceDark)
            .border(1.dp, if (isPressed) accentColor else BorderSubtle, RoundedCornerShape(12.dp))
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressStateChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressStateChange(false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) Color.White else TextPrimary,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp
        )
    }
}

// Multi-touch tracking state objects for touchpad
private var touchStartTime = 0L
private var maxPointerCountSeen = 1
private var startX0 = 0f
private var startY0 = 0f
private var startX1 = 0f
private var startY1 = 0f
private var lastX0 = 0f
private var lastY0 = 0f
private var lastX1 = 0f
private var lastY1 = 0f
private var hasExceededTapDistance = false

private const val TAP_TIMEOUT_MS = 250L
private const val TAP_MAX_DISTANCE = 24.0f

private fun handleTouchpadEvent(event: MotionEvent, viewModel: AirPadViewModel): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchStartTime = System.currentTimeMillis()
            maxPointerCountSeen = 1
            hasExceededTapDistance = false

            startX0 = event.x
            startY0 = event.y
            lastX0 = event.x
            lastY0 = event.y

            viewModel.resetTouchFilter()
            return true
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            val count = event.pointerCount
            if (count > maxPointerCountSeen) {
                maxPointerCountSeen = count
            }
            if (count >= 2) {
                startX0 = event.getX(0)
                startY0 = event.getY(0)
                startX1 = event.getX(1)
                startY1 = event.getY(1)

                lastX0 = startX0
                lastY0 = startY0
                lastX1 = startX1
                lastY1 = startY1
                viewModel.resetTouchFilter()
            }
            return true
        }

        MotionEvent.ACTION_MOVE -> {
            val count = event.pointerCount
            if (count == 1) {
                val currX = event.x
                val currY = event.y
                val dx = currX - lastX0
                val dy = currY - lastY0

                val travelFromStart = hypot(currX - startX0, currY - startY0)
                if (travelFromStart > TAP_MAX_DISTANCE) {
                    hasExceededTapDistance = true
                }

                if (hasExceededTapDistance) {
                    viewModel.sendProcessedTouchDelta(dx, dy)
                }

                lastX0 = currX
                lastY0 = currY
            } else if (count >= 2) {
                val currX0 = event.getX(0)
                val currY0 = event.getY(0)
                val currX1 = event.getX(1)
                val currY1 = event.getY(1)

                val dist0 = hypot(currX0 - startX0, currY0 - startY0)
                val dist1 = hypot(currX1 - startX1, currY1 - startY1)
                if (dist0 > TAP_MAX_DISTANCE || dist1 > TAP_MAX_DISTANCE) {
                    hasExceededTapDistance = true
                }

                val avgDy = ((currY0 - lastY0) + (currY1 - lastY1)) / 2.0f
                val avgDx = ((currX0 - lastX0) + (currX1 - lastX1)) / 2.0f

                if (hasExceededTapDistance) {
                    val scrollY = (-avgDy * 2.2f).toInt().toShort()
                    val scrollX = (avgDx * 2.2f).toInt().toShort()
                    if (scrollY != 0.toShort() || scrollX != 0.toShort()) {
                        viewModel.sendMouseScroll(scrollX, scrollY)
                        viewModel.triggerGestureFeedback(if (scrollY > 0) "↕ Scroll Up" else "↕ Scroll Down")
                    }
                }

                lastX0 = currX0
                lastY0 = currY0
                lastX1 = currX1
                lastY1 = currY1
            }
            return true
        }

        MotionEvent.ACTION_POINTER_UP -> {
            viewModel.resetTouchFilter()
            return true
        }

        MotionEvent.ACTION_UP -> {
            val duration = System.currentTimeMillis() - touchStartTime
            viewModel.resetTouchFilter()

            if (!hasExceededTapDistance && duration <= TAP_TIMEOUT_MS) {
                viewModel.performHapticButtonPress()
                if (maxPointerCountSeen >= 2) {
                    // Two-finger tap -> Right Click
                    viewModel.sendMouseButton(ProtocolConstants.MOUSE_BTN_RIGHT, ProtocolConstants.ACTION_PRESS)
                    viewModel.sendMouseButton(ProtocolConstants.MOUSE_BTN_RIGHT, ProtocolConstants.ACTION_RELEASE)
                    viewModel.triggerGestureFeedback("🖱️ Right Click")
                } else {
                    // One-finger tap -> Left Click
                    viewModel.sendMouseButton(ProtocolConstants.MOUSE_BTN_LEFT, ProtocolConstants.ACTION_PRESS)
                    viewModel.sendMouseButton(ProtocolConstants.MOUSE_BTN_LEFT, ProtocolConstants.ACTION_RELEASE)
                    viewModel.triggerGestureFeedback("🖱️ Left Click")
                }
            }
            return true
        }

        MotionEvent.ACTION_CANCEL -> {
            viewModel.resetTouchFilter()
            return true
        }
    }
    return false
}

private var scrollStripLastY = 0f

private fun handleScrollStripEvent(event: MotionEvent, viewModel: AirPadViewModel): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            scrollStripLastY = event.y
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            val currY = event.y
            val dy = currY - scrollStripLastY
            val scrollDelta = (-dy * 3.0f).toInt().toShort()
            if (scrollDelta != 0.toShort()) {
                viewModel.sendMouseScroll(0, scrollDelta)
                viewModel.triggerGestureFeedback(if (scrollDelta > 0) "↕ Scroll Up" else "↕ Scroll Down")
            }
            scrollStripLastY = currY
            return true
        }
    }
    return true
}
