package com.airpad.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    viewModel: AirPadViewModel,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "KEYBOARD MODE",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        // Text preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = if (text.isEmpty()) "Start typing..." else text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (text.isEmpty()) TextMuted else TextPrimary
            )
        }

        // Keyboard rows
        KeyboardRow(
            keys = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            viewModel = viewModel,
            text = text,
            onTextChange = { text = it }
        )
        KeyboardRow(
            keys = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", ";"),
            viewModel = viewModel,
            text = text,
            onTextChange = { text = it }
        )
        KeyboardRow(
            keys = listOf("Z", "X", "C", "V", "B", "N", "M", ",", ".", "/"),
            viewModel = viewModel,
            text = text,
            onTextChange = { text = it }
        )

        // Bottom row: Space + Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Space bar
            Box(
                modifier = Modifier
                    .weight(3f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceHighlight)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable {
                        viewModel.sendKeyboardKey(ProtocolConstants.KEY_SPACE, ProtocolConstants.ACTION_PRESS)
                        viewModel.sendKeyboardKey(ProtocolConstants.KEY_SPACE, ProtocolConstants.ACTION_RELEASE)
                        text = text + " "
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Space", color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            // Backspace
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceHighlight)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .clickable {
                        viewModel.sendKeyboardKey(ProtocolConstants.KEY_BACKSPACE, ProtocolConstants.ACTION_PRESS)
                        viewModel.sendKeyboardKey(ProtocolConstants.KEY_BACKSPACE, ProtocolConstants.ACTION_RELEASE)
                        if (text.isNotEmpty()) text = text.dropLast(1)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⌫", color = TextPrimary, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    viewModel: AirPadViewModel,
    text: String,
    onTextChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { key ->
            KeyButton(
                label = key,
                onClick = {
                    val keyCode = keyToKeyCode(key)
                    if (keyCode != null) {
                        viewModel.sendKeyboardKey(keyCode, ProtocolConstants.ACTION_PRESS)
                        viewModel.sendKeyboardKey(keyCode, ProtocolConstants.ACTION_RELEASE)
                        onTextChange(text + key)
                    }
                }
            )
        }
    }
}

@Composable
fun KeyButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceHighlight)
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

private fun keyToKeyCode(key: String): Short? {
    return when (key) {
        "Q" -> ProtocolConstants.KEY_Q
        "W" -> ProtocolConstants.KEY_W
        "E" -> ProtocolConstants.KEY_E
        "R" -> ProtocolConstants.KEY_R
        "T" -> ProtocolConstants.KEY_T
        "Y" -> ProtocolConstants.KEY_Y
        "U" -> ProtocolConstants.KEY_U
        "I" -> ProtocolConstants.KEY_I
        "O" -> ProtocolConstants.KEY_O
        "P" -> ProtocolConstants.KEY_P
        "A" -> ProtocolConstants.KEY_A
        "S" -> ProtocolConstants.KEY_S
        "D" -> ProtocolConstants.KEY_D
        "F" -> ProtocolConstants.KEY_F
        "G" -> ProtocolConstants.KEY_G
        "H" -> ProtocolConstants.KEY_H
        "J" -> ProtocolConstants.KEY_J
        "K" -> ProtocolConstants.KEY_K
        "L" -> ProtocolConstants.KEY_L
        "Z" -> ProtocolConstants.KEY_Z
        "X" -> ProtocolConstants.KEY_X
        "C" -> ProtocolConstants.KEY_C
        "V" -> ProtocolConstants.KEY_V
        "B" -> ProtocolConstants.KEY_B
        "N" -> ProtocolConstants.KEY_N
        "M" -> ProtocolConstants.KEY_M
        ";" -> ProtocolConstants.KEY_SEMICOLON
        "," -> ProtocolConstants.KEY_COMMA
        "." -> ProtocolConstants.KEY_DOT
        "/" -> ProtocolConstants.KEY_SLASH
        else -> null
    }
}