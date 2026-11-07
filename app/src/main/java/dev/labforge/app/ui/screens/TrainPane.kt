package dev.labforge.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.AppSelection
import dev.labforge.app.core.Perf
import dev.labforge.app.core.model.AssetKind
import dev.labforge.app.service.MemoryGuard
import dev.labforge.app.core.train.ChatFormat
import dev.labforge.app.ui.components.Console
import dev.labforge.app.ui.components.EmptyState
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.LabField
import dev.labforge.app.ui.components.LineChart
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.Segmented
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.components.humanNumber
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel
import dev.labforge.app.vm.LoraConfig
import dev.labforge.app.vm.TrainConfig
import java.io.File

@Composable
fun TrainPane(vm: LabViewModel) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0 = LoRA, 1 = full
    var modelPath by remember { mutableStateOf("") }
    var datasetPath by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(ChatFormat.CHATML) }
    val autoPreset = remember { AppSelection.preset }
    var epochs by remember { mutableStateOf(autoPreset?.epochs ?: 3) }
    var lrText by remember { mutableStateOf(autoPreset?.lr?.let { "%.7f".format(it).trimEnd('0').trimEnd('.') } ?: "1e-4") }
    var ctx by remember { mutableStateOf(autoPreset?.ctx ?: 512) }
    var batch by remember { mutableStateOf(128) }
    var rank by remember { mutableStateOf(autoPreset?.rank ?: 16) }
    var targets by remember { mutableStateOf(if (autoPreset?.targets?.contains("ffn") == true) 1 else 0) } // 0 attn, 1 attn+ffn
    var filterMode by remember { mutableStateOf(0) }
    var customFilter by remember { mutableStateOf("") }
    var outputName by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    val model = vm.models.firstOrNull { it.file.absolutePath == modelPath } ?: vm.selectedModel ?: vm.models.firstOrNull()
    val dataset = vm.datasets.firstOrNull { it.absolutePath == datasetPath } ?: vm.builtDataset ?: vm.datasets.firstOrNull()
    val meta = model?.meta
    val tokens = vm.datasetStats?.approxTokens ?: (dataset?.length()?.div(3.6)?.toLong() ?: 0L)
    val bits = when {
        meta?.quantLabel?.contains("Q8") == true -> 8.5
        meta?.quantLabel?.contains("Q6") == true -> 6.56
        meta?.quantLabel?.contains("Q5") == true -> 5.67
        meta?.quantLabel?.contains("Q4") == true -> 4.85
        meta?.quantLabel?.contains("Q3") == true -> 3.9
        meta?.quantLabel?.contains("Q2") == true -> 2.7
        else -> 4.5
    }
    val eta = if (tokens > 0 && meta != null) {
        Perf.trainingSeconds(tokens, epochs, meta.parameterCount, bits, vm.deviceCores, mode == 0)
    } else 0.0
    val budget = model?.let { MemoryGuard.check(context, it.sizeBytes, if (mode == 0) "lora" else "full") }

    LaunchedEffect(vm.models) {
        if (modelPath.isBlank()) modelPath = AppSelection.modelPath ?: vm.models.firstOrNull()?.file?.absolutePath ?: ""
    }
    LaunchedEffect(vm.datasets) {
        if (datasetPath.isBlank()) {
            datasetPath = AppSelection.datasetPath ?: vm.builtDataset?.absolutePath
                ?: vm.datasets.firstOrNull { it.extension.equals("txt", true) }?.absolutePath
                ?: vm.datasets.firstOrNull()?.absolutePath ?: ""
        }
    }

    val pickDataset = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAsset(uri, AssetKind.DATASET) { f, _ ->
            if (f != null) datasetPath = f.absolutePath
        }
    }

    val filters = listOf(
        "" to "Todos os pesos",
        "attn_q,attn_v" to "Atenção Q+V",
        "attn_q,attn_k,attn_v,attn_output" to "Atenção completa",
        "attn_q,attn_v,ffn" to "Atenção + FFN",
    )
    val activeFilter = if (filterMode == filters.size) customFilter else filters.getOrElse(filterMode) { filters[0] }.first

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        if (vm.models.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.AutoAwesome,
                title = "Escolha um modelo base",
                body = "LoRA funciona com qualquer GGUF (inclusive quantizado): os pesos ficam congelados e só as matrizes A/B são treinadas.",
            )
            return@Column
        }

        if (autoPreset != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("preset do autoestudo", Lab.Cyan, filled = true)
                Text(
                    "rank ${autoPreset.rank} · ${autoPreset.epochs} epochs · ctx ${autoPreset.ctx}",
                    fontSize = 9.sp, color = Lab.Muted,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Segmented(listOf("LoRA nativo", "Full fine-tune"), mode, Modifier.fillMaxWidth()) { mode = it }
        Spacer(Modifier.height(12.dp))

        GlassCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    if (mode == 0) Icons.Rounded.AutoAwesome else Icons.Rounded.Warning,
                    null,
                    tint = if (mode == 0) Lab.Cyan else Lab.Amber,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        if (mode == 0) "Low-rank adapters (LoRA)" else "Fine-tuning completo",
                        fontSize = 11.5.sp,
                        color = if (mode == 0) Lab.Cyan else Lab.Amber,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (mode == 0)
                            "Treina apenas A/B (rank $rank) e exporta um adaptador GGUF carregável em llama.cpp/Ollama. O modelo base fica congelado, então cabe muito mais no aparelho."
                        else
                            "Atualiza os pesos selecionados. Exige GGUF F32/F16 e RAM de ~4× o modelo; use apenas para modelos pequenos.",
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = Lab.Muted,
                    )
                }
            }
            if (budget != null) {
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${"%.1f".format(budget.availableGb)} GB livres", if (budget.ok) Lab.Green else Lab.Amber)
                    Pill("~${"%.1f".format(budget.requiredGb)} GB", Lab.Cyan)
                    Pill(if (mode == 0) "base congelada" else "pesos treináveis", Lab.Violet)
                }
                Spacer(Modifier.height(5.dp))
                Text(budget.message, fontSize = 9.2.sp, color = if (budget.ok) Lab.Muted else Lab.Amber)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Dados")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            SelectRow(
                label = "Modelo GGUF",
                current = model?.name ?: "",
                options = vm.models.map { "${it.name} · ${it.meta?.quantLabel ?: "?"}" },
            ) { idx -> modelPath = vm.models[idx].file.absolutePath; vm.selectModel(vm.models[idx]) }
            Spacer(Modifier.height(12.dp))
            SelectRow(
                label = "Dataset",
                current = dataset?.name ?: "",
                options = vm.datasets.map { "${it.name} · ${humanBytes(it.length())}" },
                emptyText = "importe um .jsonl ou .txt",
            ) { idx -> datasetPath = vm.datasets[idx].absolutePath }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    GradientButton("Importar dataset", Modifier.fillMaxWidth(), Icons.Rounded.Dataset) {
                        pickDataset.launch(arrayOf("*/*"))
                    }
                }
                if (dataset?.extension?.lowercase() in setOf("jsonl", "json")) {
                    Box(Modifier.weight(0.7f)) {
                        GradientButton(
                            "Converter",
                            Modifier.fillMaxWidth(),
                            enabled = !vm.preparingDataset,
                            loading = vm.preparingDataset,
                        ) {
                            vm.prepareDataset(listOf(dataset!!), format, dataset.nameWithoutExtension)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChatFormat.entries.forEach { f ->
                    val active = format == f
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) Lab.Violet.copy(alpha = 0.18f) else Lab.Surface)
                            .border(
                                1.dp,
                                if (active) Lab.Violet.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(9.dp),
                            )
                            .clickable { format = f }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(f.label, fontSize = 9.sp, color = if (active) Color.White else Lab.Muted, maxLines = 1)
                    }
                }
            }
            val stats = vm.datasetStats
            if (stats != null) {
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("${stats.documents} docs", Lab.Green)
                    Pill("${humanNumber(stats.approxTokens)} tokens", Lab.Cyan)
                    Pill(stats.detectedFormat, Lab.Violet)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Hiperparâmetros")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            if (mode == 0) {
                Stepper("Rank LoRA", rank, listOf(8, 16, 32, 64), Modifier.fillMaxWidth()) { rank = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Atenção" to 0, "Atenção + FFN" to 1).forEach { (label, value) ->
                        val active = targets == value
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Lab.Cyan.copy(alpha = 0.16f) else Lab.Surface)
                                .border(
                                    1.dp,
                                    if (active) Lab.Cyan.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { targets = value }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, fontSize = 10.sp, color = if (active) Color.White else Lab.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Stepper("Epochs", epochs, listOf(1, 2, 3, 5), Modifier.fillMaxWidth()) { epochs = it }
                Spacer(Modifier.height(10.dp))
                Stepper("Contexto", ctx, listOf(256, 512, 1024, 2048), Modifier.fillMaxWidth()) { ctx = it }
                Spacer(Modifier.height(10.dp))
                LabField("Learning rate", lrText, placeholder = "1e-4", onValueChange = { lrText = it })
            } else {
                Stepper("Epochs", epochs, listOf(1, 2, 3, 5), Modifier.fillMaxWidth()) { epochs = it }
                Spacer(Modifier.height(10.dp))
                Stepper("Contexto", ctx, listOf(256, 512, 1024, 2048), Modifier.fillMaxWidth()) { ctx = it }
                Spacer(Modifier.height(10.dp))
                Stepper("Batch", batch, listOf(32, 64, 128, 256), Modifier.fillMaxWidth()) { batch = it }
                Spacer(Modifier.height(10.dp))
                LabField("Learning rate", lrText, placeholder = "1e-5", onValueChange = { lrText = it })
                Spacer(Modifier.height(10.dp))
                GlassCard(onClick = { showAdvanced = !showAdvanced }, padding = 11) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tensores treináveis", fontSize = 11.sp, color = Lab.Text, modifier = Modifier.weight(1f))
                        Text(if (showAdvanced) "−" else "+", fontSize = 14.sp, color = Lab.Violet)
                    }
                    if (showAdvanced) {
                        filters.forEachIndexed { i, (_, label) ->
                            val active = filterMode == i
                            Text(
                                label,
                                fontSize = 10.5.sp,
                                color = if (active) Lab.Text else Lab.Muted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) Lab.Violet.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable { filterMode = i }
                                    .padding(8.dp),
                            )
                        }
                        Text(
                            "Personalizado",
                            fontSize = 10.5.sp,
                            color = if (filterMode == filters.size) Lab.Text else Lab.Muted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (filterMode == filters.size) Lab.Violet.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { filterMode = filters.size }
                                .padding(8.dp),
                        )
                        if (filterMode == filters.size) {
                            LabField("Substrings", customFilter, placeholder = "attn_q,ffn", onValueChange = { customFilter = it })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = Lab.Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tempo estimado", fontSize = 10.sp, color = Lab.Muted)
                    Text(
                        if (eta > 0) Perf.fmtDuration(eta) else "importe o dataset para estimar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Lab.Text,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("tokens", fontSize = 9.sp, color = Lab.Muted)
                    Text(if (tokens > 0) humanNumber(tokens) else "—", fontSize = 11.sp, color = Lab.Cyan)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        LabField(
            label = "Nome do arquivo de saída (opcional)",
            value = outputName,
            placeholder = model?.let {
                if (mode == 0) "${it.file.nameWithoutExtension}-lora-r$rank.gguf"
                else "${it.file.nameWithoutExtension}-finetuned.gguf"
            } ?: "",
            onValueChange = { outputName = it },
        )

        Spacer(Modifier.height(14.dp))
        GradientButton(
            if (mode == 0) "Treinar LoRA" else "Iniciar fine-tuning",
            Modifier.fillMaxWidth(),
            Icons.Rounded.AutoAwesome,
            enabled = model != null && dataset != null && !vm.jobRunning,
            loading = vm.jobRunning && (vm.jobKind == "lora" || vm.jobKind == "full"),
        ) {
            val m = model ?: return@GradientButton
            val d = dataset ?: return@GradientButton
            if (mode == 0) {
                vm.runLora(
                    LoraConfig(
                        model = m.file,
                        dataset = d,
                        epochs = epochs,
                        lr = lrText.toFloatOrNull() ?: 1e-4f,
                        nCtx = ctx,
                        threads = vm.deviceCores,
                        rank = rank,
                        alpha = rank.toFloat(),
                        targets = if (targets == 1) "attn,ffn" else "attn",
                        outputName = outputName.trim(),
                    ),
                )
            } else {
                vm.runFinetune(
                    TrainConfig(
                        model = m.file,
                        dataset = d,
                        epochs = epochs,
                        lr = lrText.toFloatOrNull() ?: 1e-5f,
                        nCtx = ctx,
                        nBatch = batch,
                        nUbatch = minOf(batch, 128),
                        threads = vm.deviceCores,
                        filter = activeFilter,
                        outputName = outputName.trim(),
                    ),
                )
            }
        }

        if (vm.lossHistory.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("loss", fontSize = 11.sp, color = Lab.Muted, modifier = Modifier.weight(1f))
                    Text(
                        "último %.4f".format(vm.lossHistory.last()),
                        fontSize = 12.sp,
                        color = Lab.Cyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LineChart(vm.lossHistory, Modifier.fillMaxWidth().height(90.dp))
            }
        }

        if (vm.jobKind == "lora" || vm.jobKind == "full" || vm.jobTitle.startsWith("LoRA") || vm.jobTitle.startsWith("Fine-tuning")) {
            Spacer(Modifier.height(16.dp))
            JobPanel(vm)
        }

        Spacer(Modifier.height(28.dp))
    }
}
