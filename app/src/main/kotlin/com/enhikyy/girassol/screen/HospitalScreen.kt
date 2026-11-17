package com.enhikyy.girassol.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Health
import com.enhikyy.girassol.data.WorldState
import com.enhikyy.girassol.hospital.AIHospital
import com.enhikyy.girassol.hospital.Therapy
import com.enhikyy.girassol.ui.GirassolColors

/**
 * Tela HOSPITAL.
 *
 * Lista apenas os agentes doentes e oferece as duas terapias. O rollback fica
 * desativado quando não existe checkpoint saudável — nunca se inventa um estado
 * anterior. Toda a ação passa por [WorldState], para a UI reagir.
 */
@Composable
fun HospitalScreen() {
    val agents by WorldState.agents.collectAsState()
    val therapies by AIHospital.therapiesApplied.collectAsState()
    val patients = agents.filter { it.health != Health.HEALTHY }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = "AI HOSPITAL",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${patients.size} em tratamento · $therapies terapias aplicadas",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }

        if (patients.isEmpty()) {
            item { HealthyState() }
        } else {
            items(patients) { agent ->
                PatientRow(agent = agent)
                HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
            }
        }
    }
}

/** Uma linha de paciente com diagnóstico e as duas terapias. */
@Composable
private fun PatientRow(agent: Agent) {
    val severity = when (agent.health) {
        Health.HALLUCINATING -> GirassolColors.Warning
        Health.DEGRADED -> GirassolColors.Warning
        Health.COLLAPSED -> GirassolColors.Error
        Health.HEALTHY -> GirassolColors.Success
    }
    val detail = when (agent.health) {
        Health.HALLUCINATING ->
            "Conhecimento ${pct(agent.drives.knowledge)} com foco ${pct(agent.drives.focus)}: respostas sem verificação."
        Health.DEGRADED ->
            "Drives desequilibrados (total ${pct(agent.drives.total())}): deriva de comportamento."
        Health.COLLAPSED ->
            "Energia a ${pct(agent.drives.energy)}: agente inoperante."
        Health.HEALTHY -> ""
    }
    val canRollback = AIHospital.canRollback(agent.id)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(severity))
            Text(
                text = agent.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = agent.health.name.lowercase(),
                fontSize = 9.sp,
                color = severity,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = detail,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    AIHospital.treat(agent, Therapy.REGROUNDING)?.let { cured ->
                        WorldState.replaceAgent(cured)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = GirassolColors.Success.copy(alpha = 0.16f),
                    contentColor = GirassolColors.Success,
                ),
            ) {
                Text("Re-grounding", fontSize = 10.sp)
            }

            FilledTonalButton(
                onClick = {
                    AIHospital.treat(agent, Therapy.ROLLBACK)?.let { cured ->
                        WorldState.replaceAgent(cured)
                    }
                },
                enabled = canRollback,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = GirassolColors.Info.copy(alpha = 0.16f),
                    contentColor = GirassolColors.Info,
                    disabledContainerColor = Color.White.copy(alpha = 0.04f),
                    disabledContentColor = Color.White.copy(alpha = 0.25f),
                ),
            ) {
                Text(
                    text = if (canRollback) "Rollback de Pesos" else "Rollback (sem checkpoint)",
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** Estado saudável: nenhum agente doente. */
@Composable
private fun HealthyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(GirassolColors.Success))
        Text(
            text = "Nenhum agente doente",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Todos os drives estão em faixa saudável. O hospital permanece em vigilância.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

private fun pct(value: Float): String = "${(value * 100).toInt()}%"
