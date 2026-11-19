package com.enhikyy.girassol

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.screen.*
import com.enhikyy.girassol.theme.GirassolTheme
import com.enhikyy.girassol.ui.AutonomyPill
import com.enhikyy.girassol.ui.GirassolGlass
import com.enhikyy.girassol.ui.SettingsButton
import com.enhikyy.girassol.ui.girassolHazeSource
import com.enhikyy.girassol.ui.rememberGirassolHaze

/**
 * Altura reservada para a faixa do cabeçalho fixo.
 *
 * Tem de acomodar o [AutonomyPill] (7.dp do ponto + 6.dp de padding interno +
 * 12.dp de padding externo). Fica numa constante para que o espaço reservado no
 * conteúdo e a altura real da faixa nunca divergirem.
 */
private val headerHeight = 44.dp

/** Abas do dock flutuante, na ordem exibida. */
enum class Section(val label: String) {
    MUNDO("MUNDO"),
    AGENTES("AGENTES"),
    CHAT("CHAT"),
    DASHBOARD("DASHBOARD"),
    ESCOLA("ESCOLA"),
    HOSPITAL("HOSPITAL"),
    CODER("CODER"),
    PLUGINS("PLUGINS"),
}

/**
 * Raiz do app: conteúdo animado por aba, dock flutuante de vidro e botão de
 * configurações.
 *
 * O filtro Kodak **não** é aplicado aqui: pertence ao viewport do MUNDO
 * (ver [MundoScreen]), para não borrar texto e não custar um efeito por frame
 * sobre toda a interface.
 */
@Composable
fun GirassolApp() {
    GirassolTheme {
        var section by remember { mutableStateOf(Section.MUNDO) }
        val haze = rememberGirassolHaze()

        // Um log por mudança de seção, fora de qualquer lambda de desenho.
        LaunchedEffect(section) {
            AppLogger.log("INFO", AppLogger.TAG, "Navegação: seção = ${section.label}")
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            // Conteúdo da aba. A fonte de blur é a própria tela.
            // O `padding(top = headerHeight)` reserva a faixa do cabeçalho fixo,
            // para o conteúdo nunca passar por baixo do botão PAUSAR.
            Box(Modifier.fillMaxSize().girassolHazeSourceBackdrop(haze)) {
                AnimatedContent(
                    targetState = section,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 12 }) togetherWith
                            (fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 12 })
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerHeight, bottom = 96.dp),
                    label = "section",
                ) { target ->
                    when (target) {
                        Section.MUNDO -> MundoScreen()
                        Section.AGENTES -> AgentesScreen()
                        Section.CHAT -> ChatScreen()
                        Section.DASHBOARD -> DashboardScreen()
                        Section.ESCOLA -> EscolaScreen()
                        Section.HOSPITAL -> HospitalScreen()
                        Section.CODER -> CoderScreen()
                        Section.PLUGINS -> PluginsScreen()
                    }
                }
            }

            // Dock flutuante + configurações.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GirassolGlass(
                    state = haze,
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Section.entries.forEach { entry ->
                            DockTab(
                                label = entry.label,
                                selected = entry == section,
                                onClick = { section = entry },
                            )
                        }
                    }
                }

                GirassolGlass(
                    state = haze,
                    shape = RoundedCornerShape(50.dp),
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                ) {
                    Box(Modifier.wrapContentSize(Alignment.Center)) {
                        SettingsButton()
                    }
                }
            }

            // Faixa de cabeçalho fixa com o interruptor de emergência da
            // autonomia. Fundo OPACO (cor de background do tema) e não
            // transparente: numa tela que rola, o botão deixaria de se
            // distinguir do texto que passasse por baixo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                AutonomyPill()
            }
        }
    }
}

/** Marca o fundo como fonte do blur do dock. */
private fun Modifier.girassolHazeSourceBackdrop(
    state: dev.chrisbanes.haze.HazeState,
): Modifier = this.girassolHazeSource(state)

/**
 * Uma aba do dock, com feedback tátil: encolhe para 0.96 ao pressionar e volta
 * com uma mola sem ressalto.
 */
@Composable
private fun DockTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "dockScale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            )
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
