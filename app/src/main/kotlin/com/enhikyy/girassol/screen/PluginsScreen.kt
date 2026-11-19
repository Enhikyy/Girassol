package com.enhikyy.girassol.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enhikyy.girassol.plugins.PluginStore
import kotlinx.coroutines.launch

@Composable
fun PluginsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins by PluginStore.plugins.collectAsState()
    val running by PluginStore.running.collectAsState()
    val result by PluginStore.lastResult.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { PluginStore.init(context.applicationContext) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PLUGINS", style = MaterialTheme.typography.headlineSmall)
        Text("Plugins executados em sandbox isolado. A API expõe apenas leitura do mundo e emissão limitada de intenções.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Proteções ativas", style = MaterialTheme.typography.titleMedium)
                Text("• LuaJ: sem io, os, package, require, debug ou luajava")
                Text("• Rhino: classes Java bloqueadas por ClassShutter")
                Text("• Prazo de 5 segundos e limite de instruções")
                Text("• Máximo de 64 intenções por execução")
                Text("• Histórico local com gravação atómica")
            }
        }
        Text("Plugins instalados: ${plugins.size}", style = MaterialTheme.typography.titleMedium)
        plugins.forEach { plugin ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(plugin.id, style = MaterialTheme.typography.titleSmall)
                        Text(if (running == plugin.id) "Em execução" else "Disponível")
                    }
                    Button(
                        onClick = { scope.launch { PluginStore.run(plugin) } },
                        enabled = running == null
                    ) { Text(if (running == plugin.id) "Executando…" else "Executar") }
                }
            }
        }
        result?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(if (it.ok) "Última execução: sucesso" else "Última execução: falha")
                    Text(it.render(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}
