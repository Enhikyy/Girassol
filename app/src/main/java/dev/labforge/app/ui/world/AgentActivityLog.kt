package dev.labforge.app.ui.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.world.ActionType
import dev.labforge.app.core.world.WorldState
import dev.labforge.app.core.world.building
import dev.labforge.app.core.world.displayName
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.PulsingDot
import dev.labforge.app.ui.theme.Lab

private data class ActivityLogEntry(
    val agentId: String,
    val agentName: String,
    val action: ActionType,
    val buildingId: String?,
    val simMinute: Long,
    val durationMinutes: Int,
    val outcome: String,
    val active: Boolean,
    val progress: Float,
)

/** Minimal vertical timeline fed directly by the immutable WorldState snapshots. */
@Composable
fun AgentActivityLog(
    state: WorldState,
    modifier: Modifier = Modifier,
    onAgent: (String) -> Unit = {},
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val entries = state.activityLogEntries()
    val filtered = entries.filter { entry ->
        when (filter) {
            1 -> entry.action == ActionType.LEARN || entry.action == ActionType.RESEARCH
            2 -> entry.action == ActionType.WORK
            3 -> entry.action == ActionType.SLEEP
            else -> true
        }
    }
    val active = state.agents.mapNotNull { it.action }

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${active.count { it.type == ActionType.LEARN || it.type == ActionType.RESEARCH }} estudando", Lab.Cyan)
            Pill("${active.count { it.type == ActionType.WORK }} trabalhando", Lab.Green)
            Pill("${active.count { it.type == ActionType.SLEEP }} dormindo", Lab.Violet)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Tudo", "Estudo", "Trabalho", "Sono").forEachIndexed { index, label ->
                val selected = filter == index
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Lab.Violet.copy(alpha = 0.18f) else Lab.Surface)
                        .border(1.dp, if (selected) Lab.Violet.copy(alpha = 0.45f) else Lab.Outline, RoundedCornerShape(10.dp))
                        .clickable { filter = index }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text(label, color = if (selected) Lab.Text else Lab.Muted, fontSize = 10.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Text(
                "Nenhuma transição registrada neste filtro. A linha do tempo é formada pelas ações reais dos habitantes.",
                color = Lab.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered) { entry -> ActivityTimelineRow(entry, state, onAgent) }
            }
        }
    }
}

@Composable
private fun ActivityTimelineRow(entry: ActivityLogEntry, state: WorldState, onAgent: (String) -> Unit) {
    val color = activityColor(entry.action)
    val location = entry.buildingId?.let { state.building(it)?.name } ?: "Em circulação"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable { onAgent(entry.agentId) }
            .padding(vertical = 5.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            if (entry.active) PulsingDot(color, 10.dp) else Box(Modifier.size(9.dp).clip(CircleShape).background(color.copy(alpha = 0.7f)))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(1.dp).height(49.dp).background(if (entry.active) color.copy(alpha = 0.35f) else Lab.Outline))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (entry.active) color.copy(alpha = 0.09f) else Lab.Surface.copy(alpha = 0.75f))
                .border(1.dp, if (entry.active) color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.agentName, color = Lab.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (entry.active) "agora" else formatActivityTime(entry.simMinute),
                    color = if (entry.active) color else Lab.Muted,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (entry.active) operationalLabel(entry.action) else entry.action.displayName(),
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (entry.active) "$location · ${entry.durationMinutes} min restantes"
                else "$location · ${entry.durationMinutes} min${if (entry.outcome.isBlank()) "" else " · ${entry.outcome}"}",
                color = Lab.Muted,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.active) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)),
                    color = color,
                    trackColor = Lab.SurfaceHigh,
                )
            }
        }
    }
}

private fun WorldState.activityLogEntries(): List<ActivityLogEntry> {
    val current = agents.mapNotNull { agent ->
        val action = agent.action ?: return@mapNotNull null
        ActivityLogEntry(
            agentId = agent.id,
            agentName = agent.name,
            action = action.type,
            buildingId = action.targetBuildingId,
            simMinute = elapsedMinutes,
            durationMinutes = action.remainingMinutes,
            outcome = "",
            active = true,
            progress = if (action.totalMinutes > 0) {
                ((action.totalMinutes - action.remainingMinutes).toFloat() / action.totalMinutes).coerceIn(0f, 1f)
            } else 0f,
        )
    }
    val completed = agents.flatMap { agent ->
        agent.activityHistory.map { record ->
            ActivityLogEntry(
                agentId = agent.id,
                agentName = agent.name,
                action = record.action,
                buildingId = record.buildingId,
                simMinute = record.simMinute,
                durationMinutes = record.durationMinutes,
                outcome = record.outcome,
                active = false,
                progress = 1f,
            )
        }
    }
    return (current + completed)
        .sortedWith(compareByDescending<ActivityLogEntry> { it.simMinute }.thenByDescending { it.active })
        .take(100)
}

private fun activityColor(action: ActionType): Color = when (action) {
    ActionType.SLEEP -> Lab.Violet
    ActionType.LEARN, ActionType.RESEARCH -> Lab.Cyan
    ActionType.WORK -> Lab.Green
    ActionType.EAT -> Lab.Amber
    ActionType.SOCIALIZE -> Lab.Pink
    ActionType.RELAX, ActionType.IDLE -> Lab.Muted
}

private fun operationalLabel(action: ActionType): String = when (action) {
    ActionType.SLEEP -> "dormindo"
    ActionType.LEARN -> "estudando"
    ActionType.RESEARCH -> "pesquisando"
    ActionType.WORK -> "trabalhando"
    ActionType.EAT -> "alimentando-se"
    ActionType.SOCIALIZE -> "socializando"
    ActionType.RELAX -> "descansando"
    ActionType.IDLE -> "sem tarefa"
}

private fun formatActivityTime(simMinute: Long): String {
    val minute = (simMinute % 1_440L).toInt()
    return "D${simMinute / 1_440L + 1} · %02d:%02d".format((minute / 60) % 24, minute % 60)
}
