package dev.labforge.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.core.compiler.*
import dev.labforge.app.vm.BuildViewModel

@Composable
fun BuildPane(vm: BuildViewModel = viewModel()) {
    val report by vm.report.collectAsState()
    val running by vm.running.collectAsState()
    BuildContent(vm.project, vm.module, vm.variant, vm.offline, report, running, vm.ownsRun, vm.message,
        { vm.project = it }, { vm.module = it }, { vm.variant = it }, { vm.offline = it }, vm::start, vm::stopWaiting)
}

/** Material patterns shared with Flutter: adaptive panes, clear states and accessible controls. */
@Composable
fun BuildContent(project: String, module: String, variant: BuildVariant, offline: Boolean, report: BuildReport?,
                 running: Boolean, canStop: Boolean, message: String, onProject: (String) -> Unit,
                 onModule: (String) -> Unit, onVariant: (BuildVariant) -> Unit, onOffline: (Boolean) -> Unit,
                 onStart: () -> Unit, onStop: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    val stages = report?.stages.orEmpty()
    val active = stages.firstOrNull { it.id == selected } ?: stages.firstOrNull { it.state == StageState.FAILED || it.state == StageState.INTERRUPTED }
        ?: stages.firstOrNull { it.state == StageState.RUNNING } ?: stages.lastOrNull { it.state == StageState.PASSED }
    @Composable fun configuration() {
        Text("Central de compilação", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Do projeto ao APK verificado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Projeto Android", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(project, onProject, Modifier.fillMaxWidth(), enabled = !running, label = { Text("Diretório no Termux") }, singleLine = true)
                OutlinedTextField(module, onModule, Modifier.fillMaxWidth(), enabled = !running, label = { Text("Módulo Gradle") }, supportingText = { Text("Exemplo: :app ou :mobile:app") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuildVariant.entries.forEach { v -> FilterChip(selected = variant == v, onClick = { onVariant(v) }, label = { Text(v.task) }, enabled = !running, modifier = Modifier.heightIn(min = 48.dp)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dependências offline", modifier = Modifier.weight(1f))
                    Switch(offline, onOffline, enabled = !running)
                }
                Text("Requer Termux configurado com JDK, SDK, aapt2 ARM64, unzip e apksigner. Não requer root.", style = MaterialTheme.typography.bodySmall)
                if (!running) Button(onStart, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (report == null) "Validar e compilar" else "Revalidar e compilar") }
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Aguardando resultado da etapa", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (canStop) OutlinedButton(onStop, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Interromper espera") }
                    Text("Interromper a espera não encerra o processo externo no Termux.", style = MaterialTheme.typography.bodySmall)
                }
                if (message.isNotBlank()) Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    @Composable fun results() {
        if (report == null) {
            OutlinedCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tudo pronto para começar", style = MaterialTheme.typography.titleLarge)
                    Text("Informe o projeto já existente no Termux. Cada execução verifica o ambiente, roda testes e lint, monta a variante e confere os APKs.")
                    Text("Nenhuma etapa será marcada como concluída antes de receber o resultado.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(if (report.ok) "APK verificado" else if (running) "Compilação em andamento" else "Resultado da execução", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("${report.request.project} · ${report.request.variant.task}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            stages.forEachIndexed { index, stage ->
                val label = when (stage.state) { StageState.WAITING -> "Aguardando"; StageState.RUNNING -> "Em execução"; StageState.PASSED -> "Concluída"; StageState.FAILED -> "Falhou"; StageState.INTERRUPTED -> "Interrompida" }
                OutlinedCard(onClick = { selected = stage.id }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(when (stage.state) { StageState.PASSED -> Icons.Rounded.CheckCircle; StageState.FAILED, StageState.INTERRUPTED -> Icons.Rounded.ErrorOutline; else -> Icons.Rounded.HourglassEmpty }, null)
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${stage.title}", fontWeight = FontWeight.SemiBold)
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                        if (stage.exitCode != null) Text("exit ${stage.exitCode}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            active?.let { stage ->
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Log · ${stage.title}", style = MaterialTheme.typography.titleMedium)
                        if (stage.state == StageState.FAILED) Text(BuildPipeline.advice(stage.output), color = MaterialTheme.colorScheme.error)
                        Text("${stage.durationMs / 1000.0}s · últimas 80 mil letras da saída recebida", style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(stage.output.ifBlank { "A saída fica disponível quando a etapa termina." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) }
                    }
                }
            }
            report.artifacts.forEach { apk ->
                Spacer(Modifier.height(12.dp))
                OutlinedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("APK · ${apk.bytes} bytes", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer { Text("${apk.path}\nSHA-256\n${apk.sha256}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                        Text("Arquivo no Termux. Instale por termux-open ou pela ação install do agente; o Android pede confirmação.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        if (maxWidth >= 840.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) { configuration() }
            Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) { results() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            configuration(); results()
        }
    }
    }
}
