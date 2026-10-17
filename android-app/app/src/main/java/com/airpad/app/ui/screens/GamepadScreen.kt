package com.airpad.app.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.data.models.GamepadStateEvent
import com.airpad.app.data.models.GyroMode
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GamepadScreen(
    viewModel: AirPadViewModel,
    modifier: Modifier = Modifier
) {
    val gyroConfig by viewModel.gyroConfig.collectAsState()
    val isGyroStickActive = gyroConfig.mode == GyroMode.GYRO_TO_STICK

    var buttonMask by remember { mutableIntStateOf(0) }
    var leftTrigger by remember { mutableFloatStateOf(0f) }
    var rightTrigger by remember { mutableFloatStateOf(0f) }
    var leftStickX by remember { mutableFloatStateOf(0f) }
    var leftStickY by remember { mutableFloatStateOf(0f) }
    var rightStickX by remember { mutableFloatStateOf(0f) }
    var rightStickY by remember { mutableFloatStateOf(0f) }

    fun updateState(
        newMask: Int = buttonMask,
        newLt: Float = leftTrigger,
        newRt: Float = rightTrigger,
        newLx: Float = leftStickX,
        newLy: Float = leftStickY,
        newRx: Float = rightStickX,
        newRy: Float = rightStickY
    ) {
        buttonMask = newMask
        leftTrigger = newLt
        rightTrigger = newRt
        leftStickX = newLx
        leftStickY = newLy
        rightStickX = newRx
        rightStickY = newRy

        viewModel.sendFilteredGamepadState(
            buttonMask = newMask,
            rawLx = newLx,
            rawLy = newLy,
            rawRx = newRx,
            rawRy = newRy,
            rawLt = newLt,
            rawRt = newRt
        )
    }

    fun setButtonBit(bit: Int, pressed: Boolean) {
        if (pressed) {
            viewModel.performHapticButtonPress()
        }
        val nextMask = if (pressed) (buttonMask or bit) else (buttonMask and bit.inv())
        updateState(newMask = nextMask)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Shoulder Bumpers, Gyro Toggle, Triggers Row (LB, LT, Gyro Stick, RT, RB)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Bumpers/Triggers
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GamepadHoldButton("LT", AccentTeal) { pressed ->
                    if (pressed) viewModel.performHapticTick()
                    updateState(newLt = if (pressed) 1.0f else 0.0f)
                }
                GamepadHoldButton("LB", PrimaryBlue) { pressed ->
                    setButtonBit(ProtocolConstants.GAMEPAD_BTN_LB, pressed)
                }
            }

            // Middle Gyro Aim Toggle Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isGyroStickActive) AccentTeal.copy(alpha = 0.15f) else SurfaceDark)
                    .border(
                        1.dp,
                        if (isGyroStickActive) AccentTeal else BorderSubtle,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable {
                        viewModel.performHapticHeavyClick()
                        viewModel.toggleGyroForGamepad()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isGyroStickActive) AccentTeal else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGyroStickActive) "Gyro Stick: ON" else "Gyro Stick: OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isGyroStickActive) AccentTeal else TextMuted
                    )
                }
            }

            // Right Bumpers/Triggers
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GamepadHoldButton("RB", PrimaryBlue) { pressed ->
                    setButtonBit(ProtocolConstants.GAMEPAD_BTN_RB, pressed)
                }
                GamepadHoldButton("RT", AccentTeal) { pressed ->
                    if (pressed) viewModel.performHapticTick()
                    updateState(newRt = if (pressed) 1.0f else 0.0f)
                }
            }
        }

        // Center Area: D-Pad, Left Stick, Center Menu, Right Stick, ABXY Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: D-Pad & Left Thumbstick
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DPadControl(onDirectionChange = { up, down, left, right ->
                    if (up || down || left || right) {
                        viewModel.performHapticButtonPress()
                    }
                    var mask = buttonMask
                    mask = if (up) mask or ProtocolConstants.GAMEPAD_BTN_DPAD_UP else mask and ProtocolConstants.GAMEPAD_BTN_DPAD_UP.inv()
                    mask = if (down) mask or ProtocolConstants.GAMEPAD_BTN_DPAD_DOWN else mask and ProtocolConstants.GAMEPAD_BTN_DPAD_DOWN.inv()
                    mask = if (left) mask or ProtocolConstants.GAMEPAD_BTN_DPAD_LEFT else mask and ProtocolConstants.GAMEPAD_BTN_DPAD_LEFT.inv()
                    mask = if (right) mask or ProtocolConstants.GAMEPAD_BTN_DPAD_RIGHT else mask and ProtocolConstants.GAMEPAD_BTN_DPAD_RIGHT.inv()
                    updateState(newMask = mask)
                })

                VirtualThumbstick(
                    label = "LS",
                    onPositionChange = { x, y -> updateState(newLx = x, newLy = y) }
                )
            }

            // Center: SELECT & START Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GamepadHoldButton("SELECT", SurfaceHighlight, width = 64, height = 36) { pressed ->
                    setButtonBit(ProtocolConstants.GAMEPAD_BTN_BACK, pressed)
                }
                GamepadHoldButton("START", SurfaceHighlight, width = 64, height = 36) { pressed ->
                    setButtonBit(ProtocolConstants.GAMEPAD_BTN_START, pressed)
                }
            }

            // Right Side: ABXY Diamond & Right Thumbstick
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AbxyDiamond(onButtonEvent = { btnBit, pressed ->
                    setButtonBit(btnBit, pressed)
                })

                VirtualThumbstick(
                    label = "RS",
                    onPositionChange = { x, y -> updateState(newRx = x, newRy = y) }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GamepadHoldButton(
    label: String,
    accentColor: Color,
    width: Int = 52,
    height: Int = 40,
    onPressChange: (Boolean) -> Unit
) {
    var isPressed by remember { androidx.compose.runtime.mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) accentColor else SurfaceDark)
            .border(1.dp, if (isPressed) accentColor else BorderSubtle, RoundedCornerShape(8.dp))
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressChange(false)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPressed) Color.White else TextPrimary
        )
    }
}

@Composable
fun DPadControl(
    onDirectionChange: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit
) {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background cross
        Box(
            modifier = Modifier
                .size(36.dp, 110.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
        )
        Box(
            modifier = Modifier
                .size(110.dp, 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
        )

        // Buttons
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            GamepadHoldButton("▲", PrimaryBlue, 36, 36) { onDirectionChange(it, false, false, false) }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            GamepadHoldButton("▼", PrimaryBlue, 36, 36) { onDirectionChange(false, it, false, false) }
        }
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            GamepadHoldButton("◀", PrimaryBlue, 36, 36) { onDirectionChange(false, false, it, false) }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GamepadHoldButton("▶", PrimaryBlue, 36, 36) { onDirectionChange(false, false, false, it) }
        }
    }
}

@Composable
fun AbxyDiamond(
    onButtonEvent: (btnBit: Int, pressed: Boolean) -> Unit
) {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Y (Top)
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            GamepadHoldButton("Y", AccentTeal, 36, 36) { onButtonEvent(ProtocolConstants.GAMEPAD_BTN_Y, it) }
        }
        // A (Bottom)
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            GamepadHoldButton("A", SuccessGreen, 36, 36) { onButtonEvent(ProtocolConstants.GAMEPAD_BTN_A, it) }
        }
        // X (Left)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            GamepadHoldButton("X", PrimaryBlue, 36, 36) { onButtonEvent(ProtocolConstants.GAMEPAD_BTN_X, it) }
        }
        // B (Right)
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GamepadHoldButton("B", DangerRed, 36, 36) { onButtonEvent(ProtocolConstants.GAMEPAD_BTN_B, it) }
        }
    }
}

@Composable
fun VirtualThumbstick(
    label: String,
    onPositionChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val outerRadius = 55f
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(110.dp)
            .clip(CircleShape)
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        offsetX = 0f
                        offsetY = 0f
                        onPositionChange(0f, 0f)
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                        onPositionChange(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val newX = offsetX + dragAmount.x
                    val newY = offsetY + dragAmount.y
                    val dist = sqrt(newX * newX + newY * newY)

                    if (dist <= outerRadius) {
                        offsetX = newX
                        offsetY = newY
                    } else {
                        offsetX = (newX / dist) * outerRadius
                        offsetY = (newY / dist) * outerRadius
                    }

                    onPositionChange(offsetX / outerRadius, offsetY / outerRadius)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Inner thumb knob
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceHighlight)
                .border(1.dp, PrimaryBlue.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        }
    }
}
