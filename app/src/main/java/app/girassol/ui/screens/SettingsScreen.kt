package app.girassol.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.girassol.core.data.ThemeMode
import app.girassol.ui.components.ScreenHeader
import app.girassol.ui.components.SectionCard
import app.girassol.ui.components.formatBytes
import app.girassol.vm.AppViewModel

@Composable
fun SettingsScreen(model: AppViewModel, onBack: () -> Unit) {
    val settings by model.settings.collectAsState()
    val models by model.models.collectAsState()
    val importBytes by model.importBytes.collectAsState()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importModel(uri) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeader("Preferências · privadas", "Controle do laboratório", "Modelos, aparência e automação ficam no dispositivo.") {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
        } }
        item {
            SectionCard("Modelos GGUF", subtitle = "A inferência usa Llamatik/llama.cpp. O arquivo é copiado para o armazenamento privado antes de ser carregado.") {
                Button(onClick = { importer.launch(arrayOf("*/*")) }, enabled = importBytes == null) {
                    Icon(Icons.Default.Add, null)
                    Text(if (importBytes == null) " Importar modelo" else " Importando ${formatBytes(importBytes ?: 0)}")
                }
                if (models.isEmpty()) Text("Nenhum modelo importado. Um GGUF compatível é necessário para Chat e Codex.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(models, key = { it.path }) { localModel ->
            Card {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.activeModelPath == localModel.path, onClick = { model.selectModel(localModel) })
                    Column(Modifier.weight(1f)) {
                        Text(localModel.name, style = MaterialTheme.typography.titleMedium)
                        Text(formatBytes(localModel.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { model.deleteModel(localModel) }) { Icon(Icons.Default.Delete, "Excluir ${localModel.name}") }
                }
            }
        }
        item {
            SectionCard("Aparência e movimento") {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Escuro" }
                        if (settings.themeMode == mode) Button(onClick = { model.setTheme(mode) }) { Text(label) }
                        else OutlinedButton(onClick = { model.setTheme(mode) }) { Text(label) }
                    }
                }
                Text("Escala das transições: ${"%.1f".format(settings.animationScale)}×")
                Slider(value = settings.animationScale, onValueChange = model::setAnimationScale, valueRange = 0f..2f, steps = 3)
            }
        }
        item {
            SectionCard("Automação e limites") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Sono profundo automático", style = MaterialTheme.typography.titleMedium)
                        Text("A cada 12 horas, com carregamento, bateria suficiente e aparelho ocioso.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.automaticSleep, onCheckedChange = model::setAutomaticSleep)
                }
                Text("Sem root, sem desativação do SELinux e sem execução de comandos copiados de fontes externas. A instalação de APKs sempre exige confirmação do Android.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Builds no aparelho exigem ARM64 e toolchain validado. LoRA permanece bloqueado até existir um motor real compatível. Fallbacks remotos não são usados nesta revisão.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
