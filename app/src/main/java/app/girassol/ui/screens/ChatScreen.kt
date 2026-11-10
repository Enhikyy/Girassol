package app.girassol.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.girassol.core.chat.ChatRole
import app.girassol.ui.components.ScreenHeader
import app.girassol.vm.AppViewModel
import java.io.File

@Composable
fun ChatScreen(model: AppViewModel) {
    val messages by model.chat.collectAsState()
    val settings by model.settings.collectAsState()
    val generating by model.generating.collectAsState()
    val importBytes by model.importBytes.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importModel(uri)
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader(
            "Chat · inferência no aparelho",
            "Conversa local",
            if (settings.activeModelPath.isBlank()) "Nenhum modelo ativo. Importe um GGUF compatível." else File(settings.activeModelPath).name,
        ) {
            IconButton(onClick = { model.clearChat() }, enabled = messages.isNotEmpty() && !generating) { Icon(Icons.Default.Delete, "Limpar conversa") }
        }
        if (settings.activeModelPath.isBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("O chat não usa uma API remota nem finge responder.", style = MaterialTheme.typography.titleMedium)
                    Text("Selecione um arquivo .gguf. A validação e a cópia acontecem no armazenamento privado; modelos grandes podem exigir memória e armazenamento consideráveis.")
                    Button(onClick = { importer.launch(arrayOf("*/*")) }, enabled = importBytes == null) {
                        Icon(Icons.Default.Add, null); Text(if (importBytes == null) " Importar GGUF" else " Importando…")
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) item {
                Text("Pergunte algo depois de importar um modelo. O histórico fica somente neste aparelho.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            }
            items(messages, key = { it.id }) { message ->
                val user = message.role == ChatRole.USER
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        SelectionContainer(Modifier.padding(14.dp)) {
                            Text(message.content.ifBlank { if (generating) "Gerando…" else "Sem conteúdo" }, color = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(12_000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensagem para o modelo local") },
                minLines = 1,
                maxLines = 5,
                enabled = !generating,
            )
            if (generating) IconButton(onClick = model::cancelGeneration) { Icon(Icons.Default.Close, "Cancelar geração") }
            else IconButton(onClick = { model.sendChat(input); input = "" }, enabled = input.isNotBlank()) { Icon(Icons.Default.Send, "Enviar") }
        }
    }
}
