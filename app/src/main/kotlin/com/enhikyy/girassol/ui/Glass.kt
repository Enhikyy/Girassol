package com.enhikyy.girassol.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.DebugFlags

/**
 * Interruptor global do blur.
 *
 * Com `false` — ou em dispositivos abaixo de API 31, onde o `RenderEffect` do
 * blur não existe — todo o vidro cai para uma superfície sólida com o mesmo
 * tint e a mesma borda. Serve para isolar o Haze como causa de um fecho da app.
 */
var girassolHazeEnabled: Boolean = true

/** O blur só é aplicado quando está ligado *e* o dispositivo o suporta. */
private fun hazeSupported(): Boolean =
    girassolHazeEnabled && DebugFlags.enableHaze && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Glassmorphism do Girassol, sobre a API Haze 2.
 *
 * O fundo de uma tela marca-se com [girassolHazeSource]; os cards e o dock
 * desfocam-no com [GirassolGlass]. Ambos partilham o mesmo [HazeState].
 *
 * O estado é sempre criado — `rememberHazeState()` nunca é chamado dentro de um
 * ramo condicional — e é a aplicação do modificador que depende do suporte.
 * Assim a árvore de composição é idêntica em todos os aparelhos e alternar as
 * flags em tempo de execução não desalinha a slot table.
 */
@Composable
fun rememberGirassolHaze(): HazeState {
    val state = rememberHazeState()
    LaunchedEffect(Unit) {
        AppLogger.log(
            "INFO",
            AppLogger.TAG,
            "Haze: estado criado | flag=${DebugFlags.enableHaze} | " +
                "SDK=${Build.VERSION.SDK_INT} | blurAtivo=${hazeSupported()}" +
                if (hazeSupported()) "" else " (fallback: superfície sólida)",
        )
    }
    return state
}

/** Marca o conteúdo que servirá de fonte de blur (normalmente o fundo da tela). */
fun Modifier.girassolHazeSource(state: HazeState): Modifier =
    if (hazeSupported()) hazeSource(state) else this

/**
 * Superfície de vidro: blur do que está atrás + tint escuro + borda fina.
 *
 * Com o blur indisponível o resultado é uma superfície sólida semitransparente
 * com a mesma borda — visível em qualquer versão do Android.
 *
 * **A ordem dos modificadores importa.** [hazeBlur] desenha o ruído/blur num
 * retângulo de layout próprio e ignora o `shape` do `.background()`. Sem um
 * [clip] antes, o vidro de um dock arredondado ou de um botão circular ficava
 * com um retângulo de grão atrás — visível como uma caixa suja em volta do
 * dock. O `clip(shape)` recorta o efeito exatamente no formato da superfície.
 */
@Composable
fun GirassolGlass(
    state: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    @Suppress("UNUSED_PARAMETER") blurRadius: Dp = 20.dp,
    tint: Color = Color(0xFF18181B).copy(alpha = 0.75f),
    borderColor: Color = Color(0xFF27272A),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            // Recorta o efeito de vidro no formato da superfície (ver KDoc).
            .clip(shape)
            .then(if (hazeSupported()) Modifier.hazeBlur(input = HazeInput.Sources(state)) else Modifier)
            .background(tint, shape)
            .border(1.dp, borderColor, shape),
    ) {
        content()
    }
}

/**
 * Variante sem blur, para ecrãs onde não existe fonte marcada (ex.: listas
 * simples). Mantém exatamente o mesmo aspeto de superfície.
 */
@Composable
fun GirassolSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    tint: Color = Color(0xFF18181B).copy(alpha = 0.75f),
    borderColor: Color = Color(0xFF27272A),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(tint, shape)
            .border(1.dp, borderColor, shape),
    ) {
        content()
    }
}

/** Cores fixas do design brutalista, usadas em cartões e divisórias. */
object GirassolColors {
    val CardBackground = Color(0xFF18181B)
    val CardBorder = Color(0xFF27272A)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Success = Color(0xFF10B981)
    val Info = Color(0xFF3B82F6)
    val Innie = Color(0xFFFF5A4E)
    val Outie = Color(0xFF4AA3FF)
}
