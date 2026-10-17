package com.airpad.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.R
import com.airpad.app.data.models.ControlProfile
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.BgDark
import com.airpad.app.ui.theme.PrimaryBlue
import com.airpad.app.ui.theme.SuccessGreen
import com.airpad.app.ui.theme.SurfaceDark
import com.airpad.app.ui.theme.TextPrimary
import com.airpad.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameProfilesScreen(
    viewModel: AirPadViewModel,
    onClose: () -> Unit
) {
    val profiles by viewModel.allProfiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Text(
            text = "Game Profiles",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Active: ${activeProfile.name} (${activeProfile.genre})",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Built-in Presets",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Built-in profiles first, then custom ones
            val builtin = profiles.filter { !it.isCustom }
            val custom = profiles.filter { it.isCustom }

            items(builtin.size) { index ->
                val profile = builtin[index]
                ProfileCard(
                    profile = profile,
                    isSelected = profile.id == activeProfile.id,
                    isCustom = false,
                    onClick = {
                        viewModel.selectProfile(profile.id)
                        onClose()
                    }
                )
            }

            item {
                if (custom.isNotEmpty()) {
                    Text(
                        text = "Custom Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(custom.size) { index ->
                val profile = custom[index]
                ProfileCard(
                    profile = profile,
                    isSelected = profile.id == activeProfile.id,
                    isCustom = true,
                    onDelete = {
                        viewModel.deleteCustomProfile(profile.id)
                    },
                    onClick = {
                        viewModel.selectProfile(profile.id)
                        onClose()
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.create_custom_profile)) },
            text = {
                Column {
                    Text("Based on the currently active profile.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.profile_name)) },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedTextColor = TextPrimary,
                            cursorColor = PrimaryBlue,
                            focusedBorderColor = PrimaryBlue
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.createCustomProfile(newName)
                            newName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ProfileCard(
    profile: ControlProfile,
    isSelected: Boolean,
    isCustom: Boolean,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animDuration = 500

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isSelected) 84.dp else 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.15f) else SurfaceDark
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSelected) 40.dp else 36.dp)
                    .clip(RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.iconName,
                    fontSize = if (isSelected) 32.sp else 28.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = profile.genre,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SuccessGreen)
                )
            }

            if (isCustom) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { onDelete?.invoke() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_profile),
                        tint = TextMuted
                    )
                }
            }
        }
    }
}
