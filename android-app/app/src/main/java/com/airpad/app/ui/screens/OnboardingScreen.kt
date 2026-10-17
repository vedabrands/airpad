package com.airpad.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airpad.app.ui.AirPadViewModel
import com.airpad.app.ui.theme.*

@Composable
fun OnboardingScreen(
    viewModel: AirPadViewModel,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        "Welcome to AirPad" to "Transform your Android device into a precise wireless Air Mouse, Gamepad, and Keyboard controller for your PC.",
        "Connect Effortlessly" to "Ensure both your PC and Phone are on the same Wi-Fi network. AirPad will discover your desktop agent automatically.",
        "Choose Your Mode" to "Seamlessly switch between precision mouse tracking, full-featured gamepad layout, and keyboard typing.",
        "Ready to Roll" to "Explore settings to customize sensitivity, deadzones, and motion controls for your unique setup."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Step Indicator
        Row(
            modifier = Modifier.padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(steps.size) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (step == index) PrimaryBlue else TextMuted)
                )
            }
        }

        // Animated Content
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                        slideOutHorizontally { width -> -width } + fadeOut()
            },
            label = "OnboardingStep"
        ) { targetStep ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = steps[targetStep].first,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = steps[targetStep].second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text("Back", color = TextSecondary)
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }

            Button(
                onClick = {
                    if (step < steps.size - 1) {
                        step++
                    } else {
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(if (step < steps.size - 1) "Next" else "Get Started")
            }
        }
    }
}