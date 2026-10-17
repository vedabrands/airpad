package com.airpad.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    background = BgDark,
    surface = SurfaceDark,
    primary = PrimaryBlue,
    secondary = AccentTeal,
    error = DangerRed,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    primary = PrimaryBlue,
    secondary = AccentTeal,
    error = DangerRed,
    onBackground = Color(0xFF0F0F11),
    onSurface = Color(0xFF0F0F11),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onError = Color.White
)

@Composable
fun AirPadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}