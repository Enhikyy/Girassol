package app.girassol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.girassol.core.data.ThemeMode

val Ink = Color(0xFF172321)
val Cobalt = Color(0xFF536BD8)
val Petrol = Color(0xFF177C72)
val Mint = Color(0xFF8DDAC6)
val Laboratory = Color(0xFFF2F5F1)
val Graphite = Color(0xFF101817)

private val LightColors = lightColorScheme(
    primary = Cobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E4FF),
    onPrimaryContainer = Color(0xFF16235E),
    secondary = Petrol,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1F1E8),
    onSecondaryContainer = Color(0xFF103D37),
    tertiary = Color(0xFF9A597C),
    background = Laboratory,
    onBackground = Ink,
    surface = Color(0xFFFAFCF9),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6ECE7),
    onSurfaceVariant = Color(0xFF52615D),
    outline = Color(0xFFC4D0C9),
    error = Color(0xFFB64343),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAB8FF),
    onPrimary = Color(0xFF17265E),
    primaryContainer = Color(0xFF303F80),
    onPrimaryContainer = Color(0xFFE0E4FF),
    secondary = Mint,
    onSecondary = Color(0xFF103D37),
    secondaryContainer = Color(0xFF1D5149),
    onSecondaryContainer = Color(0xFFD1F1E8),
    tertiary = Color(0xFFE5A9C7),
    background = Graphite,
    onBackground = Color(0xFFE4ECE7),
    surface = Color(0xFF16201E),
    onSurface = Color(0xFFE4ECE7),
    surfaceVariant = Color(0xFF24312E),
    onSurfaceVariant = Color(0xFFB0BDB7),
    outline = Color(0xFF3C4C47),
    error = Color(0xFFFFB4AB),
)

@Composable
fun GirassolTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val typography = Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    )
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = typography,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)),
        content = content,
    )
}
