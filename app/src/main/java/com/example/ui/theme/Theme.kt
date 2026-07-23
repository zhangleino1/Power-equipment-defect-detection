package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Slate800,
    onPrimaryContainer = ElectricBlue,
    secondary = SkyCyan,
    onSecondary = Color.White,
    tertiary = EmeraldGreen,
    background = Slate900,
    onBackground = TextLight,
    surface = Slate800,
    onSurface = TextLight,
    surfaceVariant = Slate700,
    onSurfaceVariant = TextMuted,
    error = EmergencyRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
