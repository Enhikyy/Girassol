package com.enhikyy.girassol.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Paleta inspirada em film stocks Kodak (e equivalentes de outros fabricantes).
 *
 * [isDark] decide se o MaterialColorScheme é escuro. O modo OLED força fundo #000000.
 */
data class FilmPalette(
    val id: String,
    val label: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val onBackground: Color,
    val outline: Color,
    /**
     * Granulação do filme, 0..1.3. Alimenta a `intensity` do
     * [com.enhikyy.girassol.ui.KodakFilm]; o modo OLED usa 0.
     */
    val grain: Float = 0.6f,
)

/** As 15 paletas de film stock + o modo OLED. */
object FilmThemes {

    val portra400 = FilmPalette(
        id = "portra400", label = "Portra 400", isDark = false,
        background = Color(0xFFF4EBDD), surface = Color(0xFFFBF5EA),
        primary = Color(0xFFC8704A), secondary = Color(0xFF8FA58B),
        onBackground = Color(0xFF2B2420), outline = Color(0xFFD8CBB8), grain = 0.8f,
    )
    val portra800 = FilmPalette(
        id = "portra800", label = "Portra 800", isDark = true,
        background = Color(0xFF1E1714), surface = Color(0xFF2A211D),
        primary = Color(0xFFD9895A), secondary = Color(0xFF7E9C86),
        onBackground = Color(0xFFF2E6D8), outline = Color(0xFF3F332C), grain = 0.9f,
    )
    val gold200 = FilmPalette(
        id = "gold200", label = "Gold 200", isDark = false,
        background = Color(0xFFFFF4D6), surface = Color(0xFFFFFAEB),
        primary = Color(0xFFE0A526), secondary = Color(0xFFB5652B),
        onBackground = Color(0xFF3A2E12), outline = Color(0xFFE8D7A6), grain = 1.0f,
    )
    val triX400 = FilmPalette(
        id = "trix400", label = "Tri-X 400", isDark = true,
        background = Color(0xFF111111), surface = Color(0xFF1B1B1B),
        primary = Color(0xFFE8E8E8), secondary = Color(0xFF9A9A9A),
        onBackground = Color(0xFFF2F2F2), outline = Color(0xFF333333), grain = 1.2f,
    )
    val ektar100 = FilmPalette(
        id = "ektar100", label = "Ektar 100", isDark = false,
        background = Color(0xFFF7E9E1), surface = Color(0xFFFFF6F0),
        primary = Color(0xFFD7263D), secondary = Color(0xFFF46036),
        onBackground = Color(0xFF2A1A1C), outline = Color(0xFFE6C9BC), grain = 0.3f,
    )
    val vision3 = FilmPalette(
        id = "vision3", label = "Vision3 500T", isDark = true,
        background = Color(0xFF0B1A24), surface = Color(0xFF122633),
        primary = Color(0xFF4FB3BF), secondary = Color(0xFFF2A65A),
        onBackground = Color(0xFFD9ECEF), outline = Color(0xFF1F3A4A), grain = 0.9f,
    )
    val superia400 = FilmPalette(
        id = "superia400", label = "Superia 400", isDark = false,
        background = Color(0xFFEAF2E3), surface = Color(0xFFF6FAF0),
        primary = Color(0xFF3E8E41), secondary = Color(0xFF8BC34A),
        onBackground = Color(0xFF1E2A1C), outline = Color(0xFFCADBB8), grain = 0.9f,
    )
    val hp5 = FilmPalette(
        id = "hp5", label = "Ilford HP5", isDark = true,
        background = Color(0xFF141414), surface = Color(0xFF1F1F1F),
        primary = Color(0xFFBDBDBD), secondary = Color(0xFF7A7A7A),
        onBackground = Color(0xFFEDEDED), outline = Color(0xFF2E2E2E), grain = 1.1f,
    )
    val cinestill800t = FilmPalette(
        id = "cinestill800t", label = "CineStill 800T", isDark = true,
        background = Color(0xFF0A0F1C), surface = Color(0xFF141B2D),
        primary = Color(0xFFFF5A4E), secondary = Color(0xFF4AA3FF),
        onBackground = Color(0xFFE6EEFF), outline = Color(0xFF243049), grain = 1.0f,
    )
    val velvia50 = FilmPalette(
        id = "velvia50", label = "Velvia 50", isDark = false,
        background = Color(0xFFE9F6FF), surface = Color(0xFFF5FBFF),
        primary = Color(0xFF0077B6), secondary = Color(0xFF00B4D8),
        onBackground = Color(0xFF0B2233), outline = Color(0xFFC3E3F5), grain = 0.3f,
    )
    val provia100f = FilmPalette(
        id = "provia100f", label = "Provia 100F", isDark = false,
        background = Color(0xFFEEF1F5), surface = Color(0xFFF9FAFC),
        primary = Color(0xFF4A6FA5), secondary = Color(0xFF9DB4D3),
        onBackground = Color(0xFF1B2433), outline = Color(0xFFD0D8E4), grain = 0.4f,
    )
    val pro400h = FilmPalette(
        id = "pro400h", label = "Pro 400H", isDark = false,
        background = Color(0xFFE8F4EF), surface = Color(0xFFF3FBF7),
        primary = Color(0xFF2A9D8F), secondary = Color(0xFFA8DADC),
        onBackground = Color(0xFF162A26), outline = Color(0xFFC2E0D8), grain = 0.7f,
    )
    val kodachrome64 = FilmPalette(
        id = "kodachrome64", label = "Kodachrome 64", isDark = false,
        background = Color(0xFFFCEFD9), surface = Color(0xFFFFF8EC),
        primary = Color(0xFFD62828), secondary = Color(0xFFF77F00),
        onBackground = Color(0xFF2B1B10), outline = Color(0xFFE9CFA8), grain = 0.5f,
    )
    val ektachrome100 = FilmPalette(
        id = "ektachrome100", label = "Ektachrome E100", isDark = true,
        background = Color(0xFF0E1214), surface = Color(0xFF182024),
        primary = Color(0xFF7FB8C9), secondary = Color(0xFFD4A373),
        onBackground = Color(0xFFE3EEF2), outline = Color(0xFF2A353B), grain = 0.3f,
    )
    val cinestill50d = FilmPalette(
        id = "cinestill50d", label = "CineStill 50D", isDark = false,
        background = Color(0xFFF1F0EA), surface = Color(0xFFFAF9F4),
        primary = Color(0xFFB4442F), secondary = Color(0xFF5D7B93),
        onBackground = Color(0xFF22201C), outline = Color(0xFFDCD8CC), grain = 0.6f,
    )

    /** Modo OLED: preto puro #000000, sem lavagem de fundo e sem grão. */
    val oled = FilmPalette(
        id = "oled", label = "OLED", isDark = true,
        background = Color(0xFF000000), surface = Color(0xFF0A0A0A),
        primary = Color(0xFFBB86FC), secondary = Color(0xFF03DAC6),
        onBackground = Color(0xFFEDEDED), outline = Color(0xFF2C2C2C), grain = 0.0f,
    )

    /** Todas as paletas disponíveis (15 film stocks + OLED). */
    val ALL: List<FilmPalette> = listOf(
        portra400, portra800, gold200, triX400, ektar100, vision3,
        superia400, hp5, cinestill800t, velvia50, provia100f, pro400h,
        kodachrome64, ektachrome100, cinestill50d, oled,
    )
}

/**
 * Gerencia a paleta atual e o modo OLED via StateFlow.
 * A troca atualiza o MaterialTheme na hora, sem recriar a Activity.
 */
object ThemeManager {

    private val _currentPalette = MutableStateFlow(FilmThemes.cinestill800t)
    val currentPalette: StateFlow<FilmPalette> = _currentPalette.asStateFlow()

    private val _oledEnabled = MutableStateFlow(false)
    val oledEnabled: StateFlow<Boolean> = _oledEnabled.asStateFlow()

    /** Paleta efetiva: a selecionada, ou [FilmThemes.oled] quando o modo OLED está ligado. */
    val effectivePalette: FilmPalette
        get() = if (_oledEnabled.value) FilmThemes.oled else _currentPalette.value

    /** Define a paleta pelo id. Ignora ids desconhecidos. */
    fun setPalette(paletteId: String) {
        FilmThemes.ALL.firstOrNull { it.id == paletteId }?.let { _currentPalette.value = it }
    }

    fun setOledEnabled(enabled: Boolean) {
        _oledEnabled.value = enabled
    }

    fun toggleOled() {
        _oledEnabled.value = !_oledEnabled.value
    }
}

/** Paleta efetiva como State para Compose (paleta + flag OLED reagem juntas). */
@Composable
fun currentPalette(): FilmPalette {
    val palette by ThemeManager.currentPalette.collectAsState()
    val oled by ThemeManager.oledEnabled.collectAsState()
    return if (oled) FilmThemes.oled else palette
}

@Composable
fun isOledEnabled(): Boolean = ThemeManager.oledEnabled.collectAsState().value

/** Converte uma [FilmPalette] em ColorScheme do Material 3. */
fun FilmPalette.toColorScheme(): ColorScheme {
    val bg = if (id == "oled") Color.Black else background
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            secondary = secondary,
            onSecondary = Color.Black,
            background = bg,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = surface,
            onSurfaceVariant = onBackground.copy(alpha = 0.8f),
            outline = outline,
            surfaceContainerHighest = surface,
            primaryContainer = primary.copy(alpha = 0.22f),
            onPrimaryContainer = onBackground,
            secondaryContainer = secondary.copy(alpha = 0.18f),
            onSecondaryContainer = onBackground,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            onSecondary = Color.White,
            background = bg,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = surface,
            onSurfaceVariant = onBackground.copy(alpha = 0.8f),
            outline = outline,
            surfaceContainerHighest = surface,
            primaryContainer = primary.copy(alpha = 0.18f),
            onPrimaryContainer = onBackground,
            secondaryContainer = secondary.copy(alpha = 0.14f),
            onSecondaryContainer = onBackground,
        )
    }
}
