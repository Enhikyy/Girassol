package app.girassol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.girassol.ui.AppDestination
import app.girassol.ui.components.ActivityFeed
import app.girassol.ui.components.Metric
import app.girassol.ui.components.OperationCard
import app.girassol.ui.components.ScreenHeader
import app.girassol.ui.components.SectionCard
import app.girassol.vm.AppViewModel

@Composable
fun HomeScreen(model: AppViewModel, onNavigate: (AppDestination) -> Unit, onSettings: () -> Unit) {
    val activities by model.activities.collectAsState()
    val notes by model.notes.collectAsState()
    val projects by model.projects.collectAsState()
    val models by model.models.collectAsState()
    val memory by model.memory.collectAsState()
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader("Girassol · local-first", "Seu laboratório, em movimento.", "IA, pesquisa, engenharia Android e memória em um único organismo nativo.") {
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configurações") }
        } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Nada acontece só na interface.", style = MaterialTheme.typography.headlineMedium)
                    Text("Cada operação real desloca o agente no Mundo. Pesquise na Biblioteca, compile no Studio e revise falhas no Consultório.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onNavigate(AppDestination.WORLD) }) { Icon(Icons.Default.Public, null); Text(" Abrir Mundo") }
                        OutlinedButton(onClick = { onNavigate(AppDestination.STUDIO) }) { Icon(Icons.Default.Android, null); Text(" Criar app") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("modelos locais", models.size.toString(), Modifier.weight(1f))
                Metric("notas na Biblioteca", notes.size.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                Metric("projetos Android", projects.size.toString(), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            }
        }
        activities.primary?.let { operation -> item { OperationCard(operation) } }
        item {
            SectionCard("Pulso do sistema", subtitle = "Operações e eventos reais, mais recentes primeiro.") {
                ActivityFeed(activities)
            }
        }
        item {
            SectionCard("Memória operacional", subtitle = if (memory.lastSleepAt == 0L) "A primeira consolidação ainda não foi executada." else "Última consolidação registrada neste aparelho.") {
                Text("${memory.insights.size} sínteses · ${memory.skills.sumOf { it.experiences }} experiências classificadas")
                OutlinedButton(onClick = { model.consolidate() }) { Text("Consolidar agora na Casa") }
            }
        }
    }
}
