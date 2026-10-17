package com.airpad.app.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.R
import com.airpad.app.data.models.ButtonMappingConfig
import com.airpad.app.data.ProtocolConstants
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.AccentTeal
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.SuccessGreen
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextMuted

@Composable
fun ButtonRemapperDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    viewModel: AirPadViewModel
) {
    if (!showDialog) return

    val buttonMapping by viewModel.buttonMapping.collectAsState()
    val remappingsState = remember { mutableStateOf(buttonMapping.remappings) }
    val turboButtonsState = remember { mutableStateOf(buttonMapping.turboButtons) }
    val turboRateHz = remember { mutableStateOf(buttonMapping.turboRateHz) }
    val chordingRulesState = remember { mutableStateOf(buttonMapping.chordingRules) }

    val transition: Transition<Int, ButtonRemapperTab> = updateTransition(
        init = { ButtonRemapperTab.Mapping },
        target = { selectedTab.ordinal }
    )

    val selectedTab by remember { mutableStateOf(ButtonRemapperTab.Mapping) }

    ButtonRemapperDialogContent(
        showDialog = showDialog,
        onDismiss = onDismiss,
        selectedTab = selectedTab,
        remappings = remappingsState.value,
        onRemappingChanged = { remappingsState.value = it },
        turboButtons = turboButtonsState.value,
        onTurboButtonsChanged = { turboButtonsState.value = it },
        turboRateHz = turboRateHz.value,
        onTurboRateChanged = { turboRateHz.value = it },
        chordingRules = chordingRulesState.value,
        onChordingRulesChanged = { chordingRulesState.value = it },
        viewModel = viewModel
    )

    Dimmer(onClick = { onDismiss() })
}

@Composable
private fun ButtonRemapperDialogContent(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    selectedTab: ButtonRemapperTab,
    remappings: Map<Int, Int>,
    onRemappingChanged: (Map<Int, Int>) -> Unit,
    turboButtons: Set<Int>,
    onTurboButtonsChanged: (Set<Int>) -> Unit,
    turboRateHz: Int,
    onTurboRateChanged: (Int) -> Unit,
    chordingRules: List<ButtonMappingConfig.ChordingRule>,
    onChordingRulesChanged: (List<ButtonMappingConfig.ChordingRule>) -> Unit,
    viewModel: AirPadViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Button Mapping & Macros", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.width(300.dp)) {
                when (selectedTab) {
                    ButtonRemapperTab.Mapping -> ButtonMappingSection(
                        remappings = remappings,
                        onChanged = onRemappingChanged,
                        viewModel = viewModel
                    )
                    ButtonRemapperTab.Turbo -> TurboSettingsSection(
                        turboButtons = turboButtons,
                        onTurboButtonsChanged = onTurboButtonsChanged,
                        turboRateHz = turboRateHz,
                        onTurboRateChanged = onTurboRateChanged,
                        viewModel = viewModel
                    )
                    ButtonRemapperTab.Macros -> MacroEditorSection(
                        chordingRules = chordingRules,
                        onChordingRulesChanged = onChordingRulesChanged,
                        viewModel = viewModel
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateButtonMapping(
                    ButtonMappingConfig(
                        remappings = remappings,
                        turboButtons = turboButtons,
                        turboRateHz = turboRateHz,
                        chordingRules = chordingRules
                    )
                )
                onDismiss()
            }) {
                Text("Apply")
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
private fun ButtonMappingSection(
    remappings: Map<Int, Int>,
    onChanged: (Map<Int, Int>) -> Unit,
    viewModel: AirPadViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Button Remapping",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(ButtonMapper.ALL_BUTTONS.size) { index ->
                val buttonBit = ButtonMapper.ALL_BUTTONS[index]
                ButtonRemapRow(
                    buttonBit = buttonBit,
                    currentMapping = remappings[buttonBit] ?: buttonBit,
                    onMappingChanged = { newMapping ->
                        val updated = remappings + (buttonBit to newMapping)
                        onChanged(updated)
                    }
                )
            }
        }
    }
}

@Composable
private fun ButtonRemapRow(
    buttonBit: Int,
    currentMapping: Int,
    onMappingChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ButtonMapper.getButtonName(buttonBit),
            modifier = Modifier
                .width(80.dp)
                .padding(end = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "→",
            modifier = Modifier
                .width(20.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val available = ButtonMapper.ALL_BUTTONS.filter { it != buttonBit }
                val nextIndex = (available.indexOfFirst { it == currentMapping } + 1) % available.size
                onMappingChanged(available[nextIndex])
            },
            modifier = Modifier
                .width(100.dp)
        ) {
            Text(ButtonMapper.getButtonName(currentMapping))
        }
    }
}

@Composable
private fun TurboSettingsSection(
    turboButtons: Set<Int>,
    onTurboButtonsChanged: (Set<Int>) -> Unit,
    turboRateHz: Int,
    onTurboRateChanged: (Int) -> Unit,
    viewModel: AirPadViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Turbo / Rapid-Fire",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Buttons that will auto-repeat when held",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(ButtonMapper.TURBO_BUTTONS.size) { index ->
                val buttonBit = ButtonMapper.TURBO_BUTTONS[index]
                TurboToggleRow(
                    buttonBit = buttonBit,
                    isEnabled = turboButtons.contains(buttonBit),
                    onToggle = { enabled ->
                        if (enabled) {
                            onTurboButtonsChanged(turboButtons + buttonBit)
                        } else {
                            onTurboButtonsChanged(turboButtons - buttonBit)
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Turbo Rate (Hz): ",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$turboRateHz",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(40.dp)
            )
            Slider(
                value = turboRateHz.toFloat(),
                onValueChange = { onTurboRateChanged(it.roundToInt()) },
                valueRange = 5f..30f,
                steps = 5
            )
        }
    }
}

@Composable
private fun TurboToggleRow(
    buttonBit: Int,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ButtonMapper.getButtonName(buttonBit),
            modifier = Modifier
                .width(80.dp)
                .padding(end = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            thumbContent = { /* Custom thumb if needed */ }
        )
    }
}

@Composable
private fun MacroEditorSection(
    chordingRules: List<ButtonMappingConfig.ChordingRule>,
    onChordingRulesChanged: (List<ButtonMappingConfig.ChordingRule>) -> Unit,
    viewModel: AirPadViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Button Chording (Combos)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "When these button combinations are pressed, output a different button",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(chordingRules.size) { index ->
                val rule = chordingRules[index]
                ChordingRuleRow(
                    rule = rule,
                    onDelete = {
                        val updated = chordingRules.toMutableList()
                        updated.removeAt(index)
                        onChordingRulesChanged(updated)
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        // Add a new empty rule
                        val newRule = ButtonMappingConfig.ChordingRule(
                            primaryBit = ProtocolConstants.GAMEPAD_BTN_LB,
                            secondaryBit = ProtocolConstants.GAMEPAD_BTN_A,
                            outputBit = ProtocolConstants.GAMEPAD_BTN_X
                        )
                        onChordingRulesChanged(chordingRules + newRule)
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                ) {
                    Text("Add Combo")
                }
            }
        }
    }
}

@Composable
private fun ChordingRuleRow(
    rule: ButtonMappingConfig.ChordingRule,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+",
            modifier = Modifier
                .width(20.dp)
        )
        Text(
            text = ButtonMapper.getButtonName(rule.primaryBit),
            modifier = Modifier
                .width(60.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = ButtonMapper.getButtonName(rule.secondaryBit),
            modifier = Modifier
                .width(60.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "→",
            modifier = Modifier
                .width(20.dp)
        )
        Text(
            text = ButtonMapper.getButtonName(rule.outputBit),
            modifier = Modifier
                .width(60.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun Dimmer(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x000000.copy(alpha = 0.5f)))
            .clickable(onClick = onClick)
    )
}

enum class ButtonRemapperTab {
    Mapping,
    Turbo,
    Macros
}

private object ButtonMapper {
    val ALL_BUTTONS = listOf(
        ProtocolConstants.GAMEPAD_BTN_A,
        ProtocolConstants.GAMEPAD_BTN_B,
        ProtocolConstants.GAMEPAD_BTN_X,
        ProtocolConstants.GAMEPAD_BTN_Y,
        ProtocolConstants.GAMEPAD_BTN_LB,
        ProtocolConstants.GAMEPAD_BTN_RB,
        ProtocolConstants.GAMEPAD_BTN_BACK,
        ProtocolConstants.GAMEPAD_BTN_START,
        ProtocolConstants.GAMEPAD_BTN_LS,
        ProtocolConstants.GAMEPAD_BTN_RS,
        ProtocolConstants.GAMEPAD_BTN_DPAD_UP,
        ProtocolConstants.GAMEPAD_BTN_DPAD_DOWN,
        ProtocolConstants.GAMEPAD_BTN_DPAD_LEFT,
        ProtocolConstants.GAMEPAD_BTN_DPAD_RIGHT
    )

    val TURBO_BUTTONS = listOf(
        ProtocolConstants.GAMEPAD_BTN_A,
        ProtocolConstants.GAMEPAD_BTN_B,
        ProtocolConstants.GAMEPAD_BTN_X,
        ProtocolConstants.GAMEPAD_BTN_Y,
        ProtocolConstants.GAMEPAD_BTN_LB,
        ProtocolConstants.GAMEPAD_BTN_RB
    )

    fun getButtonName(bit: Int): String {
        return ProtocolConstants.BUTTON_NAMES[bit] ?: "BTN_$bit"
    }
}
