package com.example.metacheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColors = darkColorScheme(
    primary = Color(0xFF56E8DA),
    onPrimary = Color(0xFF062C32),
    primaryContainer = Color(0xFF123B45),
    onPrimaryContainer = Color(0xFFB9FFF5),
    secondary = Color(0xFF91B8FF),
    onSecondary = Color(0xFF09284A),
    secondaryContainer = Color(0xFF1B314B),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = Color(0xFFFFC782),
    onTertiary = Color(0xFF402600),
    tertiaryContainer = Color(0xFF493522),
    onTertiaryContainer = Color(0xFFFFDEB3),
    background = Color(0xFF080F1B),
    onBackground = Color(0xFFF0F7FF),
    surface = Color(0xFF101C2B),
    onSurface = Color(0xFFF0F7FF),
    surfaceVariant = Color(0xFF1B2A3B),
    onSurfaceVariant = Color(0xFFB6C6D8),
    outline = Color(0xFF385065),
    error = Color(0xFFFF8596),
    errorContainer = Color(0xFF512837),
    onErrorContainer = Color(0xFFFFDDE3),
)

@Composable
fun MetaCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CyberColors, content = content)
}
