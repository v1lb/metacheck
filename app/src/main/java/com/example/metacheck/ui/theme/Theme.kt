package com.example.metacheck.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object CyberPalette {
    val Black = Color(0xFF05070A)
    val Base = Color(0xFF0A0C10)
    val Glass = Color(0xD9141B28)
    val GlassLight = Color(0xB71C2838)
    val Cyan = Color(0xFF00F3FF)
    val Purple = Color(0xFF9D4EDD)
    val Green = Color(0xFF00FFB2)
    val Amber = Color(0xFFFFC875)
    val Danger = Color(0xFFFF6B91)
    val White = Color(0xFFF0F7FF)
    val Muted = Color(0xFFA8B9CE)
    val Line = Color(0xFF293E52)
}

private val Colors = darkColorScheme(
    primary = CyberPalette.Cyan,
    onPrimary = Color(0xFF001A23),
    primaryContainer = Color(0xFF123741),
    onPrimaryContainer = Color(0xFFCAFBFF),
    secondary = CyberPalette.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF33204C),
    onSecondaryContainer = Color(0xFFF2DDFF),
    tertiary = CyberPalette.Green,
    onTertiary = Color(0xFF00251C),
    tertiaryContainer = Color(0xFF123A31),
    onTertiaryContainer = Color(0xFFCCFFF0),
    background = CyberPalette.Base,
    onBackground = CyberPalette.White,
    surface = Color(0xFF121A26),
    onSurface = CyberPalette.White,
    surfaceVariant = Color(0xFF1C2838),
    onSurfaceVariant = CyberPalette.Muted,
    outline = CyberPalette.Line,
    error = CyberPalette.Danger,
    errorContainer = Color(0xFF4D2433),
    onErrorContainer = Color(0xFFFFDCE6),
)

private val Type = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun MetaCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
        ),
        content = content,
    )
}
