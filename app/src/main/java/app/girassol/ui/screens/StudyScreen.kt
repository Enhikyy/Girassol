package app.girassol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.girassol.ui.components.ScreenHeader
import app.girassol.ui.components.SectionCard
import app.girassol.ui.components.formatTime
import app.girassol.vm.AppViewModel

@Composable
fun StudyScreen(model: AppViewModel) {
    val notes by model.notes.collectAsState()
    val memory by model.memory.collectAsState()
    val busy by model.busy.collectAsState()
    val dataset by model.dataset.collectAsState()
    var topic by rememberSaveable { mutableStateOf("") }
    var datasetTopic by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeader("Estudo · conhecimento rastreável", "Biblioteca, Faculdade e Casa", "Pesquise fontes, prepare exemplos e consolide o que realmente aconteceu no sistema.") }
        item {
            SectionCard("Autoestudo na Biblioteca", subtitle = "Consulta real à Wikipédia e ao Crossref. O conteúdo e a origem ficam disponíveis para auditoria.", icon = Icons.Default.MenuBook) {
                OutlinedTextField(value = topic, onValueChange = { topic = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("Tema de pesquisa") }, placeholder = { Text("Ex.: inferência local em Android") }, singleLine = true)
                Button(onClick = { model.research(topic) }, enabled = topic.trim().length >= 3 && busy == null) { Text("Pesquisar e guardar fontes") }
                dataset?.let {
                    Text("Último dataset: ${it.examples} exemplos · ${it.bytes / 1024} KB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Text(it.path, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard("Especialização na Faculdade", subtitle = "A geração de dataset é uma preparação de dados, não um treinamento de pesos.", icon = Icons.Default.School) {
                OutlinedTextField(value = datasetTopic, onValueChange = { datasetTopic = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("Filtro do dataset (opcional)") }, singleLine = true)
                Button(onClick = { model.createDataset(datasetTopic) }, enabled = notes.isNotEmpty() && busy == null) { Text("Gerar JSONL com as fontes") }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Treino LoRA bloqueado nesta revisão", style = MaterialTheme.typography.titleMedium)
                        Text("Nenhum motor de treinamento LoRA compatível foi instalado e validado. O Girassol não converterá datasets ou consolidações em uma alegação falsa de fine-tuning. O Mundo só mostrará treino de pesos quando um executor real for adicionado e verificado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionCard("Sono profundo na Casa", subtitle = "Consolidação determinística do histórico: competências operacionais, padrões e falhas pendentes. Não modifica os pesos do GGUF.", icon = Icons.Default.Home) {
                Button(onClick = model::consolidate, enabled = busy == null) { Text("Iniciar consolidação agora") }
                if (memory.lastSleepAt > 0) Text("Última execução: ${formatTime(memory.lastSleepAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                memory.skills.forEach { skill ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(skill.title, fontWeight = FontWeight.SemiBold)
                            Text("${(skill.mastery * 100).toInt()}% · ${skill.experiences} eventos")
                        }
                        LinearProgressIndicator(progress = { skill.mastery }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (memory.insights.isNotEmpty()) item {
            SectionCard("Sínteses para revisão") {
                memory.insights.forEach { insight ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Text(insight.title, style = MaterialTheme.typography.titleMedium, color = if (insight.needsReview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text(insight.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("FONTES SALVAS · ${notes.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
        items(notes, key = { it.id }) { note ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    Text("${note.source} · ${note.url}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(note.content.take(680) + if (note.content.length > 680) "…" else "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
