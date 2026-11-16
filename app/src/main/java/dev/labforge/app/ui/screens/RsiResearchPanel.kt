package dev.labforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.File
import dev.labforge.app.core.rsi.RsiWeightBundle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.labforge.app.core.native.LlamaBridge
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GhostButton
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AutonomyViewModel

/** Native UI for the on-device skill/replay/experiment workbench. */
@Composable
fun RsiResearchPanel(vm: AutonomyViewModel) {
    val lab = vm.state.rsiLab
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var experimentName by remember { mutableStateOf("") }
    var baseline by remember { mutableStateOf("0.5") }
    var candidate by remember { mutableStateOf("0.6") }
    var samples by remember { mutableStateOf("5") }
    var holdout by remember { mutableStateOf("3") }
    var nativeCheck by remember { mutableStateOf<String?>(null) }
    var checkingNative by remember { mutableStateOf(false) }
    var weightStatus by remember { mutableStateOf("Nenhum pacote de pesos importado") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val weightPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            weightStatus = "Validando pacote…"
            weightStatus = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "labforge/rsi/weights").apply { mkdirs() }
                    val file = File(dir, "adapter-" + System.currentTimeMillis() + ".rsiw")
                    try {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "não foi possível abrir o arquivo" }
                            file.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var copied = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    require(copied <= 256L * 1024L * 1024L) { "arquivo acima de 256 MiB" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        val bundle = RsiWeightBundle.load(file)
                        "Pacote validado · " + bundle.architecture + " · hidden=" + bundle.hiddenSize +
                            " · loops=" + bundle.reasoningSteps + " · SHA-256 OK"
                    } catch (failure: Throwable) {
                        file.delete()
                        throw failure
                    }
                }.getOrElse { "Pacote rejeitado: " + (it.message ?: "erro de validação") }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    SectionHeader("Laboratório evolutivo", "skills versionadas · replay · validação")
    Spacer(Modifier.height(10.dp))
    GlassCard {
        if (lab == null) {
            Text("Carregando laboratório…", color = Lab.Muted, fontSize = 12.sp)
        } else {
            Text(
                "Replay: ${lab.replays.size} execuções · ${(lab.replaySuccessRate * 100).toInt()}% sucesso · " +
                    "${lab.meanLatencyMs.toLong()} ms · ${lab.meanTokens.toInt()} tokens médios",
                color = Lab.Text,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Skills: ${lab.skills.size} candidatas/históricas · versão ativa: ${lab.approvedSkill?.title ?: "nenhuma"}",
                color = Lab.Muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome da skill") },
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it.take(12000) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Instruções reutilizáveis") },
            minLines = 3,
            maxLines = 6,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                vm.proposeRsiSkill(title, body)
                title = ""
                body = ""
            },
            enabled = title.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Criar candidata") }
        Spacer(Modifier.height(6.dp))
        Text(
            "A candidata é salva localmente e passa por validação estrutural. A aprovação é manual; " +
                "nenhuma skill altera código executável ou pesos GGUF.",
            color = Lab.Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        lab?.skills?.take(8)?.forEach { skill ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${skill.title} · v${skill.version}${if (skill.approved) " · ativa" else ""}",
                        color = Lab.Text,
                        fontSize = 12.sp,
                    )
                    Text(
                        "${skill.validation} · evidências: ${skill.evidenceCount}",
                        color = Lab.Muted,
                        fontSize = 10.sp,
                    )
                }
                if (!skill.approved) {
                    GhostButton("Validar e ativar") { vm.approveRsiSkill(skill.id) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Reverter para versão anterior") { vm.rollbackRsiSkill() }
        Spacer(Modifier.height(8.dp))
        Text(
            "O motor de experimentos registra comparações baseline/holdout e bloqueia candidatos " +
                "sem amostras mínimas ou sem ganho. O replay guarda hash da tarefa, não o texto privado.",
            color = Lab.Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text("Registrar avaliação held-out", color = Lab.Text, fontSize = 13.sp)
        OutlinedTextField(experimentName, { experimentName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Experimento") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(baseline, { baseline = it }, Modifier.weight(1f), label = { Text("Baseline 0–1") }, singleLine = true)
            OutlinedTextField(candidate, { candidate = it }, Modifier.weight(1f), label = { Text("Candidato 0–1") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(samples, { samples = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Amostras") }, singleLine = true)
            OutlinedTextField(holdout, { holdout = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Holdout") }, singleLine = true)
        }
        Button(
            onClick = {
                val b = baseline.toDoubleOrNull()
                val c = candidate.toDoubleOrNull()
                val s = samples.toIntOrNull()
                val h = holdout.toIntOrNull()
                if (b != null && c != null && s != null && h != null) {
                    vm.recordRsiExperiment(experimentName, b, c, s, h)
                    experimentName = ""
                }
            },
            enabled = experimentName.isNotBlank() && (baseline.toDoubleOrNull() ?: -1.0) in 0.0..1.0 &&
                (candidate.toDoubleOrNull() ?: -1.0) in 0.0..1.0 && (samples.toIntOrNull() ?: 0) >= 0 &&
                (holdout.toIntOrNull() ?: 0) >= 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Registrar resultado medido") }
        lab?.experiments?.take(5)?.forEach { experiment ->
            Text(
                "${experiment.name}: ${"%.3f".format(experiment.baselineScore)} → ${"%.3f".format(experiment.candidateScore)} · ${experiment.status}",
                color = Lab.Muted,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Backend C++/NDK · arquiteturas experimentais", color = Lab.Text, fontSize = 13.sp)
        Text(
            "RDT: refinamento recorrente com gate · Modus_X: estado recorrente + memória associativa delta-rule · " +
                "Lunaris: top-k experts + IRL + mediador e fusão. Kernels nativos compilados separadamente do runtime GGUF.",
            color = Lab.Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                checkingNative = true
                scope.launch {
                    nativeCheck = withContext(Dispatchers.IO) { LlamaBridge.rsiKernelSelfTest() }
                    checkingNative = false
                }
            },
            enabled = !checkingNative && LlamaBridge.isAvailable,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (checkingNative) "Testando kernels…" else "Testar kernels C++/NDK") }
        nativeCheck?.let { result ->
            Text(result, color = if (result.contains("\"ok\":true")) Lab.Green else Lab.Amber, fontSize = 11.sp)
        }
        Button(
            onClick = { weightPicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Importar pacote de pesos .rsiw") }
        Text(weightStatus, color = if (weightStatus.startsWith("Pacote validado")) Lab.Green else Lab.Muted, fontSize = 10.sp)
        Text(
            "Formato RSIW exige arquitetura, dimensões, tensores FP32 e SHA-256. Não existe checkpoint gerativo oficial dessas arquiteturas disponível para importar neste momento.",
            color = Lab.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
        Text(
            "Importante: os kernels são operações de camada/estado e ainda não substituem o decoder completo do llama.cpp. " +
                "Os checkpoints treinados específicos destas arquiteturas não foram publicados pelos respectivos projetos; " +
                "não são criados pesos aleatórios nem apresentados como modelos prontos.",
            color = Lab.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}
