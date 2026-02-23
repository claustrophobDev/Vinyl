package com.claustrophobDev.vinyl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object VinylColors {
    val Background = Color(0xFF07060B)
    val Surface = Color(0xFF110F18)
    val SurfaceHigh = Color(0xFF1A1724)
    val Stroke = Color(0x14FFFFFF)
    val StrokeStrong = Color(0x24FFFFFF)

    val Accent = Color(0xFF8B6CFF)
    val AccentBright = Color(0xFFB9A6FF)
    val AccentDeep = Color(0xFF5B3DF5)
    val AccentSoft = Color(0x248B6CFF)

    val Text = Color(0xFFF4F2FA)
    val TextSecondary = Color(0xFFA6A2B8)
    val TextMuted = Color(0xFF6D6982)

    val Success = Color(0xFF3DDC97)
    val Warning = Color(0xFFFFB547)
    val Danger = Color(0xFFFF5C7A)
}

private val colorScheme = darkColorScheme(
    primary = VinylColors.Accent,
    onPrimary = Color.White,
    primaryContainer = VinylColors.AccentSoft,
    onPrimaryContainer = VinylColors.AccentBright,
    secondary = VinylColors.AccentBright,
    background = VinylColors.Background,
    onBackground = VinylColors.Text,
    surface = VinylColors.Surface,
    onSurface = VinylColors.Text,
    surfaceVariant = VinylColors.SurfaceHigh,
    onSurfaceVariant = VinylColors.TextSecondary,
    surfaceContainerLow = VinylColors.Surface,
    surfaceContainer = VinylColors.Surface,
    surfaceContainerHigh = VinylColors.SurfaceHigh,
    surfaceContainerHighest = VinylColors.SurfaceHigh,
    outline = VinylColors.StrokeStrong,
    outlineVariant = VinylColors.Stroke,
    error = VinylColors.Danger,
    inverseSurface = VinylColors.Text,
    inverseOnSurface = VinylColors.Background,
)

private val typography = Typography(
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

@Composable
fun VinylTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
