package com.enhikyy.girassol.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import com.enhikyy.girassol.ui.currentFontFamily
import com.enhikyy.girassol.AppLogger

/**
 * Aplica a paleta atual e a fonte escolhida ao MaterialTheme.
 *
 * Paleta e fonte vêm de StateFlow; trocar qualquer uma recompõe apenas o que
 * lê o [MaterialTheme], sem recriar a Activity.
 */
@Composable
fun GirassolTheme(content: @Composable () -> Unit) {
    val palette = currentPalette()
    val fontFamily = currentFontFamily()

    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.withFamily(fontFamily),
        displayMedium = base.displayMedium.withFamily(fontFamily),
        displaySmall = base.displaySmall.withFamily(fontFamily),
        headlineLarge = base.headlineLarge.withFamily(fontFamily),
        headlineMedium = base.headlineMedium.withFamily(fontFamily),
        headlineSmall = base.headlineSmall.withFamily(fontFamily),
        titleLarge = base.titleLarge.withFamily(fontFamily),
        titleMedium = base.titleMedium.withFamily(fontFamily),
        titleSmall = base.titleSmall.withFamily(fontFamily),
        bodyLarge = base.bodyLarge.withFamily(fontFamily),
        bodyMedium = base.bodyMedium.withFamily(fontFamily),
        bodySmall = base.bodySmall.withFamily(fontFamily),
        labelLarge = base.labelLarge.withFamily(fontFamily),
        labelMedium = base.labelMedium.withFamily(fontFamily),
        labelSmall = base.labelSmall.withFamily(fontFamily),
    )

    val scheme = palette.toColorScheme()
    AppLogger.log(
        "DEBUG",
        "GIRASSOL_DEBUG",
        "Cor de fundo: ${scheme.background}, Cor do texto: ${scheme.onBackground}",
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content,
    )
}

private fun TextStyle.withFamily(
    family: androidx.compose.ui.text.font.FontFamily,
): TextStyle {
    return this.copy(fontFamily = family)
}
