package dev.labforge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Lab {
    val Bg = Color(0xFF070805)
    val BgElev = Color(0xFF0C0E09)
    val Surface = Color(0xFF13160F)
    val SurfaceHigh = Color(0xFF1C2015)
    val Outline = Color(0xFF3A4028)
    val Text = Color(0xFFFBF6E7)
    val Muted = Color(0xFFB4B89F)
    val OnPrimary = Color(0xFF1A1405)
    val Field = Color(0xFF0F110B)
    val Violet = Color(0xFFF2C14E)
    val VioletDeep = Color(0xFFC6922A)
    val Indigo = Color(0xFFE6A93C)
    val Cyan = Color(0xFFA9D96B)
    val Pink = Color(0xFFFF9D5C)
    val Green = Color(0xFF84D98A)
    val Amber = Color(0xFFFFD166)
    val Red = Color(0xFFFF6B6B)
}

private val scheme = darkColorScheme(
    primary = Lab.Violet,
    onPrimary = Lab.OnPrimary,
    primaryContainer = Lab.VioletDeep,
    onPrimaryContainer = Lab.OnPrimary,
    secondary = Lab.Cyan,
    onSecondary = Color(0xFF16200A),
    tertiary = Lab.Pink,
    background = Lab.Bg,
    onBackground = Lab.Text,
    surface = Lab.Surface,
    onSurface = Lab.Text,
    surfaceVariant = Lab.SurfaceHigh,
    onSurfaceVariant = Lab.Muted,
    outline = Lab.Outline,
    error = Lab.Red,
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun LabForgeTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
