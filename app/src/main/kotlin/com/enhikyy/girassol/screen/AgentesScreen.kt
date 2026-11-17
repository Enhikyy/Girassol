package com.enhikyy.girassol.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Health
import com.enhikyy.girassol.data.WorldState
import com.enhikyy.girassol.school.AgentSchoolManager
import com.enhikyy.girassol.ui.GirassolColors

/**
 * Tela AGENTES: lista vertical com divisórias finas, sem cards flutuantes.
 *
 * Cada linha mostra identidade, reino, saúde, a intenção atual e o progresso
 * na universidade. Tocar seleciona o agente para a tela MUNDO.
 */
@Composable
fun AgentesScreen() {
    val agents by WorldState.agents.collectAsState()
    val selectedId by WorldState.selectedId.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = "AGENTES",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${agents.size} no ecossistema · toque para focar no mundo",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }

        items(agents) { agent ->
            AgentRow(
                agent = agent,
                selected = agent.id == selectedId,
                onClick = { WorldState.select(agent.id) },
            )
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }
    }
}

@Composable
private fun AgentRow(agent: Agent, selected: Boolean, onClick: () -> Unit) {
    val realmColor = if (agent.isInnie) GirassolColors.Innie else GirassolColors.Outie
    val healthColor = when (agent.health) {
        Health.HEALTHY -> GirassolColors.Success
        Health.HALLUCINATING, Health.DEGRADED -> GirassolColors.Warning
        Health.COLLAPSED -> GirassolColors.Error
    }
    val school = AgentSchoolManager.progressOf(agent.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.background,
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(realmColor),
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = agent.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "g${agent.generation}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                )
            }
            Text(
                text = "${agent.role} · ${agent.lastIntention}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "energia ${(agent.drives.energy * 100).toInt()}%",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = "escola ${(school * 100).toInt()}%",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }

        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(healthColor),
        )
    }
}
