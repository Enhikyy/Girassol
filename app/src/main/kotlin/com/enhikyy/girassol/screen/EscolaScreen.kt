package com.enhikyy.girassol.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.enhikyy.girassol.data.WorldState
import com.enhikyy.girassol.school.AgentSchoolManager
import com.enhikyy.girassol.school.Concept
import com.enhikyy.girassol.school.Professor
import com.enhikyy.girassol.ui.GirassolColors

/**
 * Tela ESCOLA: a Universidade Girassol.
 *
 * Cada professor aparece com o seu grafo de conhecimento e o conceito que
 * está na Zona de Desenvolvimento Proximal do primeiro agente — o próximo
 * passo possível, nem trivial nem impossível.
 */
@Composable
fun EscolaScreen() {
    val agents by WorldState.agents.collectAsState()
    val student = agents.firstOrNull()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = "UNIVERSIDADE GIRASSOL",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Grafos de conhecimento · Zona de Desenvolvimento Proximal",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }

        items(AgentSchoolManager.professors) { professor ->
            val nextLesson = student?.let { AgentSchoolManager.nextLesson(it.id, professor.subject) }
            ProfessorBlock(
                professor = professor,
                nextConcept = nextLesson?.takeIf { it.first.id == professor.id }?.second,
                progress = student?.let { AgentSchoolManager.progressOf(it.id, professor.subject) } ?: 0f,
            )
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }
    }
}

@Composable
private fun ProfessorBlock(professor: Professor, nextConcept: Concept?, progress: Float) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = professor.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${professor.subject.label} · ${professor.concepts.size} conceitos · ${professor.students} alunos",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Barra de progresso da matéria.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Text(
            text = nextConcept?.let { "Próximo na ZPD: ${it.label} (dificuldade ${(it.difficulty * 100).toInt()}%)" }
                ?: "Grafo completo para este aluno",
            fontSize = 10.sp,
            color = if (nextConcept != null) GirassolColors.Success
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}
