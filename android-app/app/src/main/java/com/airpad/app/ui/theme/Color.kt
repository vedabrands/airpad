package com.airpad.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- Background / Surface ---
val BgDark = Color(0xFF0F0F11)
val SurfaceDark = Color(0xFF18181B)
val SurfaceElevated = Color(0xFF1F1F23)
val SurfaceHighlight = Color(0xFF27272A)
val BorderSubtle = Color(0x1AFFFFFF) // 10% white border
val BorderMedium = Color(0x2DFFFFFF) // 18% white border

// --- Primary / Accent ---
val PrimaryBlue = Color(0xFF3B82F6)
val PrimaryBlueHover = Color(0xFF2563EB)
val PrimaryBlueDim = Color(0xFF1E40AF)
val AccentTeal = Color(0xFF14B8A6)
val AccentTealHover = Color(0xFF0D9488)
val AccentCyan = Color(0xFF22D3EE)

// --- Semantic ---
val DangerRed = Color(0xFFEF4444)
val DangerRedHover = Color(0xFFDC2626)
val SuccessGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF3B82F6)

// --- Text ---
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF71717A)
val TextDisabled = Color(0xFF52525B)

// --- Gradients ---
val GradientPrimary = Brush.linearGradient(
    colors = listOf(PrimaryBlue, AccentTeal),
    start = Offset(0f, 0f),
    end = Offset(1f, 1f)
)
val GradientSurface = Brush.linearGradient(
    colors = listOf(SurfaceElevated, SurfaceDark),
    start = Offset(0f, 0f),
    end = Offset(0f, 1f)
)
val GradientAccent = Brush.radialGradient(
    colors = listOf(PrimaryBlue.copy(alpha = 0.25f), Color(0x003B82F6)),
    radius = 300f
)

// --- Gamepad / Mode Colors ---
val GamepadA = Color(0xFF10B981)     // Green
val GamepadB = Color(0xFFEF4444)     // Red
val GamepadX = Color(0xFF3B82F6)     // Blue
val GamepadY = Color(0xFFF59E0B)     // Yellow
val GamepadDpad = Color(0xFF6B7280)  // Slate
val GamepadShoulder = Color(0xFF8B5CF6) // Violet

// --- Cursor / Aim ---
val CursorColor = Color(0xFF14B8A6)
val AimCrosshair = Color(0xFFEF4444)