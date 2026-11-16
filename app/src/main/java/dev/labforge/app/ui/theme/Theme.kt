package dev.labforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Lab {
    private val active = mutableStateOf(FilmTheme.PORTRA_400.palette)
    internal fun apply(palette: FilmPalette) { active.value = palette }
    val Bg get() = Color(active.value.background)
    val BgElev get() = Color(active.value.backgroundElevated)
    val Surface get() = Color(active.value.surface)
    val SurfaceHigh get() = Color(active.value.surfaceHigh)
    val Outline get() = Color(active.value.outline)
    val Text get() = Color(active.value.text)
    val Muted get() = Color(active.value.muted)
    val OnPrimary get() = Color(active.value.onPrimary)
    val Field get() = Color(active.value.field)
    val Violet get() = Color(active.value.primary)
    val VioletDeep get() = Color(active.value.primaryDeep)
    val Indigo get() = Color(active.value.indigo)
    val Cyan get() = Color(active.value.cyan)
    val Pink get() = Color(active.value.pink)
    val Green get() = Color(active.value.green)
    val Amber get() = Color(active.value.amber)
    val Red get() = Color(active.value.red)
}

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
    val context = LocalContext.current
    LaunchedEffect(context) { ThemeManager.initialize(context) }
    val selected by ThemeManager.selected.collectAsState()
    val palette = selected.palette
    SideEffect { Lab.apply(palette) }
    val scheme = darkColorScheme(
        primary = Color(palette.primary),
        onPrimary = Color(palette.onPrimary),
        primaryContainer = Color(palette.primaryDeep),
        onPrimaryContainer = Color(palette.text),
        secondary = Color(palette.cyan),
        onSecondary = Color(palette.background),
        tertiary = Color(palette.pink),
        background = Color(palette.background),
        onBackground = Color(palette.text),
        surface = Color(palette.surface),
        onSurface = Color(palette.text),
        surfaceVariant = Color(palette.surfaceHigh),
        onSurfaceVariant = Color(palette.muted),
        outline = Color(palette.outline),
        error = Color(palette.red),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
