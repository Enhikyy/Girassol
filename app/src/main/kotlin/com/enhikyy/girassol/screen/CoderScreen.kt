package com.enhikyy.girassol.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enhikyy.girassol.plugins.PluginStore
import kotlinx.coroutines.launch

@Composable
fun CoderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins by PluginStore.plugins.collectAsState()
    val running by PluginStore.running.collectAsState()
    val result by PluginStore.lastResult.collectAsState()

    var selected by remember { mutableStateOf<PluginStore.PluginFile?>(null) }
    var source by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("my_plugin") }
    var extension by remember { mutableStateOf("lua") }
    var status by remember { mutableStateOf("Inicializando sandbox…") }
    var history by remember { mutableStateOf(emptyList<PluginStore.PluginVersion>()) }

    LaunchedEffect(Unit) {
        runCatching { PluginStore.init(context.applicationContext) }
            .onSuccess { status = "Sandbox Lua/JS pronto" }
            .onFailure { status = "Falha ao abrir plugins: ${it.message}" }
    }

    LaunchedEffect(selected?.id, plugins) {
        val current = selected
        if (current != null && plugins.any { it.id == current.id }) {
            source = PluginStore.readSource(current).orEmpty()
            history = PluginStore.history(current)
        } else if (current != null) {
            selected = null
            source = ""
            history = emptyList()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CODER STUDIO", style = MaterialTheme.typography.headlineSmall)
        Text("LuaJ + Rhino • sandbox sem acesso direto a ficheiros, rede ou classes Java")
        Text(status, style = MaterialTheme.typography.bodySmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Novo plugin", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nome (letras, números e _)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { extension = "lua" }, enabled = extension != "lua") { Text("Lua") }
                    Button(onClick = { extension = "js" }, enabled = extension != "js") { Text("JavaScript") }
                    Button(onClick = {
                        scope.launch {
                            val created = PluginStore.create(newName.trim(), extension)
                            status = if (created) "Plugin criado" else "Nome inválido ou plugin já existente"
                            if (created) {
                                selected = plugins.firstOrNull { it.id == "${newName.trim()}.$extension" }
                            }
                        }
                    }) { Text("Criar") }
                }
            }
        }

        Text("Plugins locais", style = MaterialTheme.typography.titleMedium)
        if (plugins.isEmpty()) {
            Text("Ainda não há plugins. Os exemplos são instalados na primeira abertura.")
        } else {
            plugins.forEach { plugin ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(plugin.id, style = MaterialTheme.typography.titleSmall)
                            Text(if (running == plugin.id) "Executando…" else "Última alteração: ${plugin.lastModified}")
                        }
                        TextButton(onClick = { selected = plugin }) { Text(if (selected?.id == plugin.id) "Selecionado" else "Editar") }
                    }
                }
            }
        }

        if (selected != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Editor • ${selected!!.id}", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                        label = { Text("Código-fonte") },
                        minLines = 10,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val saved = PluginStore.save(selected!!, source)
                                status = if (saved) "Salvo com histórico" else "Falha ao salvar (limite 64 KiB ou plugin em execução)"
                                history = PluginStore.history(selected!!)
                            }
                        }, enabled = running != selected!!.id) { Text("Salvar") }
                        Button(onClick = {
                            scope.launch {
                                status = "Execução solicitada; aguarda autonomia ativa"
                                PluginStore.run(selected!!)
                            }
                        }, enabled = running == null) { Text(if (running == selected!!.id) "Executando…" else "Executar") }
                        TextButton(onClick = {
                            scope.launch {
                                val deleted = PluginStore.delete(selected!!)
                                status = if (deleted) "Plugin removido" else "Não foi possível remover"
                                if (deleted) selected = null
                            }
                        }, enabled = running != selected!!.id) { Text("Excluir") }
                    }
                    if (history.isNotEmpty()) {
                        Text("Histórico (até 20 versões)", style = MaterialTheme.typography.titleSmall)
                        history.take(5).forEach { version ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${version.timestamp} • ${version.sizeBytes} bytes")
                                TextButton(onClick = {
                                    scope.launch {
                                        val restored = PluginStore.restore(selected!!, version)
                                        status = if (restored) "Versão restaurada" else "Falha ao restaurar"
                                        source = PluginStore.readSource(selected!!).orEmpty()
                                        history = PluginStore.history(selected!!)
                                    }
                                }) { Text("Restaurar") }
                            }
                        }
                    }
                }
            }
        }

        result?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(if (it.ok) "Execução concluída" else "Execução falhou", style = MaterialTheme.typography.titleMedium)
                    Text(it.render(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
