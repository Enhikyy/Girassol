package dev.labforge.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab

data class ConsensusAgentVisual(val id: String, val name: String, val active: Boolean)
data class ConsensusTransferVisual(val id: Long, val fromAgentId: String, val toAgentId: String, val label: String)

/**
 * Multi-agent bus renderer. Animation is driven by supplied transfers; an active
 * agent count alone is never presented as evidence of consensus or data exchange.
 */
@Composable
fun ConsensusBus(
    agents: List<ConsensusAgentVisual>,
    transfers: List<ConsensusTransferVisual>,
    modifier: Modifier = Modifier,
) {
    val activeCount = agents.count { it.active }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Lab.Surface.copy(alpha = 0.82f))
            .border(1.dp, Lab.Violet.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Barramento de consenso", color = Lab.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Pill("$activeCount ativos", if (activeCount >= 2) Lab.Green else Lab.Muted)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
            agents.take(5).forEach { agent ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Lab.SurfaceHigh).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (agent.active) PulsingDot(Lab.Cyan, 7.dp) else Box(Modifier.size(7.dp).clip(CircleShape).background(Lab.Outline))
                    Spacer(Modifier.width(5.dp))
                    Text(agent.name, color = if (agent.active) Lab.Text else Lab.Muted, fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (transfers.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(Lab.Outline))
            Spacer(Modifier.height(8.dp))
            Text(
                if (activeCount < 2) "Consenso requer ao menos dois agentes ativos; nenhum fluxo foi inferido."
                else "Agentes ativos, mas nenhum evento real de consenso foi reportado.",
                color = Lab.Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        } else {
            transfers.takeLast(4).forEach { transfer -> ConsensusTransferRow(transfer) }
        }
    }
}

@Composable
private fun ConsensusTransferRow(transfer: ConsensusTransferVisual) {
    val transition = rememberInfiniteTransition(label = "consensus-${transfer.id}")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_250, easing = LinearEasing), RepeatMode.Restart),
        label = "packet",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(transfer.label, color = Lab.Muted, fontSize = 9.sp, modifier = Modifier.width(88.dp), maxLines = 2)
        Canvas(Modifier.weight(1f).height(18.dp)) {
            val y = size.height / 2f
            drawLine(Lab.Violet.copy(alpha = 0.42f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            drawCircle(Lab.Cyan, radius = 4.dp.toPx(), center = Offset(size.width * phase, y))
            drawCircle(Lab.Violet, radius = 3.dp.toPx(), center = Offset(0f, y))
            drawCircle(Lab.Green, radius = 3.dp.toPx(), center = Offset(size.width, y))
        }
        Spacer(Modifier.width(5.dp))
        Text("evento #${transfer.id}", color = Lab.Cyan, fontSize = 8.sp)
    }
}
