package com.example.metacheck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metacheck.R

/**
 * Стиль «протокол осмотра»: бумага и чернила, один сигнальный цвет (как у штампа), данные из файла —
 * моноширинным шрифтом. Тёмная тема — та же бумага наоборот: тёплый почти чёрный фон и светлые чернила.
 */
object Paper {
    val Light = Color(0xFFF4F1EA)
    val LightRaised = Color(0xFFFBF9F4)
    val LightRule = Color(0xFFD8D2C4)
    val Ink = Color(0xFF16150F)
    val InkMuted = Color(0xFF5F5B50)
    val Signal = Color(0xFFB93D0A)
    val Clean = Color(0xFF2F6B3A)
    val Caution = Color(0xFF7A4E00)

    val Dark = Color(0xFF17160F)
    val DarkRaised = Color(0xFF201E16)
    val DarkRule = Color(0xFF34312A)
    val DarkInk = Color(0xFFEDE8DC)
    val DarkInkMuted = Color(0xFFA8A291)
    val DarkSignal = Color(0xFFF07A3E)
    val DarkClean = Color(0xFF86C08D)
    val DarkCaution = Color(0xFFE0A93F)
}

private val LightColors = lightColorScheme(
    primary = Paper.Ink,
    onPrimary = Paper.Light,
    primaryContainer = Color(0xFFE6E1D5),
    onPrimaryContainer = Paper.Ink,
    inversePrimary = Paper.DarkInk,
    secondary = Paper.Caution,
    onSecondary = Paper.Light,
    secondaryContainer = Color(0xFFF1E3C6),
    onSecondaryContainer = Color(0xFF3A2500),
    tertiary = Paper.Clean,
    onTertiary = Paper.Light,
    tertiaryContainer = Color(0xFFDCEBDD),
    onTertiaryContainer = Color(0xFF0F2E15),
    background = Paper.Light,
    onBackground = Paper.Ink,
    surface = Paper.Light,
    onSurface = Paper.Ink,
    surfaceVariant = Paper.LightRaised,
    onSurfaceVariant = Paper.InkMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = Paper.LightRaised,
    surfaceDim = Color(0xFFE4DED2),
    surfaceContainerLowest = Paper.LightRaised,
    surfaceContainerLow = Color(0xFFF7F4EE),
    surfaceContainer = Color(0xFFEFEBE2),
    surfaceContainerHigh = Color(0xFFEAE5DA),
    surfaceContainerHighest = Color(0xFFE4DED2),
    inverseSurface = Paper.Ink,
    inverseOnSurface = Paper.Light,
    outline = Paper.Ink,
    outlineVariant = Paper.LightRule,
    error = Paper.Signal,
    onError = Paper.Light,
    errorContainer = Color(0xFFF6DDD0),
    onErrorContainer = Color(0xFF5C1C02),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Paper.DarkInk,
    onPrimary = Paper.Dark,
    primaryContainer = Color(0xFF2E2B22),
    onPrimaryContainer = Paper.DarkInk,
    inversePrimary = Paper.Ink,
    secondary = Paper.DarkCaution,
    onSecondary = Paper.Dark,
    secondaryContainer = Color(0xFF3A2C10),
    onSecondaryContainer = Color(0xFFF6DFB0),
    tertiary = Paper.DarkClean,
    onTertiary = Paper.Dark,
    tertiaryContainer = Color(0xFF1D3321),
    onTertiaryContainer = Color(0xFFCDE8D0),
    background = Paper.Dark,
    onBackground = Paper.DarkInk,
    surface = Paper.Dark,
    onSurface = Paper.DarkInk,
    surfaceVariant = Paper.DarkRaised,
    onSurfaceVariant = Paper.DarkInkMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF2A2820),
    surfaceDim = Paper.Dark,
    surfaceContainerLowest = Color(0xFF12110C),
    surfaceContainerLow = Color(0xFF1C1A14),
    surfaceContainer = Paper.DarkRaised,
    surfaceContainerHigh = Color(0xFF25231B),
    surfaceContainerHighest = Color(0xFF2A2820),
    inverseSurface = Paper.DarkInk,
    inverseOnSurface = Paper.Dark,
    outline = Paper.DarkInk,
    outlineVariant = Paper.DarkRule,
    error = Paper.DarkSignal,
    onError = Paper.Dark,
    errorContainer = Color(0xFF3D1F10),
    onErrorContainer = Color(0xFFFFD9C8),
    scrim = Color.Black,
)

/** IBM Plex Sans: интерфейс. Шрифты лежат в res/font (SIL Open Font License 1.1). */
val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plex_sans_bold, FontWeight.Bold),
)

/** IBM Plex Mono: всё, что прочитано из файла (координаты, серийные номера, имена файлов, значения тегов). */
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

private fun TextStyle.sans() = copy(fontFamily = PlexSans)

private val Base = Typography()

private val Type = Typography(
    displayLarge = Base.displayLarge.sans(),
    displayMedium = Base.displayMedium.sans(),
    displaySmall = Base.displaySmall.sans(),
    headlineLarge = TextStyle(fontFamily = PlexSans, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = PlexSans, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = PlexSans, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = PlexSans, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = PlexSans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Моноширинные стили для значений из файла. */
object MonoText {
    val value = TextStyle(fontFamily = PlexMono, fontSize = 14.sp, lineHeight = 20.sp)
    val file = TextStyle(fontFamily = PlexMono, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val tag = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}

@Composable
fun MetaCheckTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Type,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(3.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp),
            extraLarge = RoundedCornerShape(10.dp),
        ),
        content = { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } },
    )
}
