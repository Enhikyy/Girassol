package com.enhikyy.girassol.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.core.AutonomyController
import com.enhikyy.girassol.core.RunState

/**
 * Botão PAUSAR/RETOMAR, sempre visível no topo da app.
 *
 * É o botão de emergência da autonomia: se um loop descontrolado aquecer o
 * aparelho ou consumir a bateria, este controlo (e a ação equivalente na
 * notificação) pará-lo sem ser preciso matar a app. Fica fora do conteúdo das
 * abas de propósito, para nunca ser desmontado por uma troca de secção.
 */
@Composable
fun AutonomyPill(modifier: Modifier = Modifier) {
    val state by AutonomyController.state.collectAsState()
    val paused = state == RunState.PAUSED

    val dot by animateColorAsState(
        targetValue = if (paused) Color(0xFFF59E0B) else Color(0xFF22C55E),
        animationSpec = tween(220),
        label = "autonomyDot",
    )
    val label = if (paused) "RETOMAR" else "PAUSAR"

    val pillShape = RoundedCornerShape(50.dp)

    Row(
        modifier = modifier
            .clip(pillShape)
            // Fundo OPACO: o botão fica sobre uma faixa fixa, mas nunca deve
            // deixar o texto de uma lista rolar "por dentro" dele.
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = pillShape,
            )
            .clickable(
                interactionSource = null,
                indication = null,
            ) { AutonomyController.toggle("app") }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}

/** Animação de mola reutilizada pelos controlos da barra. */
internal val autonomySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)
