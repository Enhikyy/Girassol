package dev.labforge.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.model.AssetKind
import dev.labforge.app.ui.components.Console
import dev.labforge.app.ui.components.EmptyState
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.LabField
import dev.labforge.app.ui.components.ProgressRing
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.components.humanNumber
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel
import dev.labforge.app.vm.QUANT_OPTIONS
import dev.labforge.app.vm.QuantConfig
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuantizePane(vm: LabViewModel) {
    var ftype by remember { mutableStateOf("Q4_K_M") }
    var threads by remember { mutableStateOf(vm.deviceCores.coerceAtMost(6).coerceAtLeast(2)) }
    var useImatrix by remember { mutableStateOf(true) }
    var modelPath by remember { mutableStateOf("") }
    var imatrixPath by remember { mutableStateOf("") }
    var outputName by remember { mutableStateOf("") }

    val model = vm.models.firstOrNull { it.file.absolutePath == modelPath } ?: vm.models.firstOrNull()
    LaunchedEffect(vm.models) {
        if (modelPath.isBlank()) modelPath = vm.models.firstOrNull()?.file?.absolutePath ?: ""
    }
    LaunchedEffect(vm.imatrices) {
        if (imatrixPath.isBlank()) imatrixPath = vm.imatrixFile?.absolutePath
            ?: vm.imatrices.firstOrNull()?.absolutePath ?: ""
    }

    val imatrix = vm.imatrices.firstOrNull { it.absolutePath == imatrixPath } ?: vm.imatrixFile
    val option = QUANT_OPTIONS.firstOrNull { it.ftype == ftype } ?: QUANT_OPTIONS[4]
    val estimate = model?.let { LabViewModel.estimateSize(it, option.bits) } ?: 0L
    val pickImatrix = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAsset(uri, AssetKind.IMATRIX) { f, _ ->
            if (f != null) {
                imatrixPath = f.absolutePath
                vm.parseImatrix(f)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        if (vm.models.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Memory,
                title = "Nenhum modelo para quantizar",
                body = "Importe um GGUF na aba Modelos. A quantização é feita pelo motor llama.cpp embutido, com suporte a imatrix.",
            )
            return@Column
        }

        GlassCard {
            SelectRow(
                label = "Modelo de entrada",
                current = model?.name ?: "",
                options = vm.models.map { "${it.name} · ${humanBytes(it.sizeBytes)}" },
            ) { idx -> modelPath = vm.models[idx].file.absolutePath; outputName = "" }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model?.meta?.let {
                    Pill("${it.parameterLabel} params", Lab.Violet)
                    Pill(it.architecture, Lab.Cyan)
                    Pill(it.quantLabel, Lab.Green)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Tipo de quantização", "alvo de bits por peso")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            QUANT_OPTIONS.forEach { opt ->
                val active = opt.ftype == ftype
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Lab.Violet.copy(alpha = 0.20f) else Lab.Surface)
                        .border(
                            1.dp,
                            if (active) Lab.Violet.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { ftype = opt.ftype }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        opt.label,
                        fontSize = 11.5.sp,
                        color = if (active) Color.White else Lab.Text,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        "${"%.1f".format(opt.bits)} bpw · ${opt.note}",
                        fontSize = 11.sp,
                        color = if (active) Lab.Cyan else Lab.Muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = Lab.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(0.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Estimativa de saída", fontSize = 11.sp, color = Lab.Muted)
                    Text(humanBytes(estimate), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("origem", fontSize = 11.sp, color = Lab.Muted)
                    Text(humanBytes(model?.sizeBytes ?: 0), fontSize = 12.sp, color = Lab.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Estimativa por tensor (1D mantidos em F32). O tamanho real pode variar ±5%%.",
                fontSize = 11.sp,
                color = Lab.Muted,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Importance matrix", "opcional, recomendado")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            ToggleRow(
                title = "Usar imatrix",
                subtitle = "Protege os tensores com maior ativação",
                checked = useImatrix,
                onCheckedChange = { useImatrix = it },
            )
            if (useImatrix) {
                Spacer(Modifier.height(12.dp))
                if (vm.imatrices.isEmpty()) {
                    Text("Nenhum arquivo imatrix importado.", fontSize = 11.sp, color = Lab.Muted)
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Importar imatrix (.gguf / .dat)", Modifier.fillMaxWidth()) {
                        pickImatrix.launch(arrayOf("*/*"))
                    }
                } else {
                    SelectRow(
                        label = "Arquivo",
                        current = imatrix?.name ?: "",
                        options = vm.imatrices.map { "${it.name} · ${humanBytes(it.length())}" },
                    ) { idx ->
                        imatrixPath = vm.imatrices[idx].absolutePath
                        vm.parseImatrix(vm.imatrices[idx])
                    }
                    if (vm.imatrixReport != null) {
                        Spacer(Modifier.height(8.dp))
                        vm.imatrixReport?.let { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill("${r.tensorCount} tensores", Lab.Green)
                                if (r.chunkCount > 0) Pill("${r.chunkCount} chunks", Lab.Cyan)
                                Pill(if (r.legacy) "legado .dat" else "GGUF", Lab.Violet)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        GlassCard {
            Text(
                "Threads · $threads",
                fontSize = 11.sp,
                color = Lab.Muted,
                fontWeight = FontWeight.Medium,
            )
            Slider(
                value = threads.toFloat(),
                onValueChange = { threads = it.roundToInt().coerceAtLeast(1) },
                valueRange = 1f..vm.deviceCores.toFloat().coerceAtLeast(1f),
                steps = (vm.deviceCores - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Lab.Violet,
                    activeTrackColor = Lab.Violet,
                    inactiveTrackColor = Lab.SurfaceHigh,
                ),
            )
            Spacer(Modifier.height(4.dp))
            LabField(
                label = "Nome do arquivo de saída (opcional)",
                value = outputName,
                placeholder = model?.let { "${it.file.nameWithoutExtension}-$ftype.gguf" } ?: "",
                onValueChange = { outputName = it },
            )
        }

        Spacer(Modifier.height(20.dp))
        GradientButton(
            "Quantizar com ${option.label}",
            Modifier.fillMaxWidth(),
            Icons.Rounded.Science,
            enabled = model != null && !vm.jobRunning,
            loading = vm.jobRunning && vm.jobTitle.startsWith("Quantizando"),
        ) {
            model?.let {
                vm.runQuantize(
                    QuantConfig(
                        model = it.file,
                        ftype = ftype,
                        threads = threads,
                        imatrix = if (useImatrix) imatrix else null,
                        outputName = outputName.trim(),
                    ),
                )
            }
        }

        if (vm.jobRunning && vm.jobTitle.startsWith("Quantizando") || vm.jobDone && vm.jobTitle.startsWith("Quantizando")) {
            Spacer(Modifier.height(16.dp))
            JobPanel(vm)
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun JobPanel(vm: LabViewModel) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (vm.jobRunning) vm.jobProgress.coerceAtLeast(0.03f) else 1f,
                size = 58.dp,
                stroke = 6.dp,
                color = when {
                    vm.jobRunning -> Lab.Amber
                    vm.jobSuccess -> Lab.Green
                    else -> Lab.Red
                },
                label = if (vm.jobRunning) "${(vm.jobProgress * 100).toInt()}%" else if (vm.jobSuccess) "✓" else "!",
                caption = "job",
            )
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    vm.jobTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Lab.Text,
                )
                if (vm.jobRunning) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "restam ~${dev.labforge.app.core.Perf.fmtEta(vm.jobStartedAt, vm.jobProgress)}",
                        fontSize = 11.sp, color = Lab.Muted,
                    )
                    Spacer(Modifier.height(4.dp))
                    LabProgress(vm.jobProgress.coerceAtLeast(0.03f), Lab.Amber, 5)
                }
            }
            if (vm.jobRunning) {
                Spacer(Modifier.size(8.dp))
                SunflowerSpinner(32.dp, 10)
            } else if (vm.jobDone) {
                Text(
                    "limpar",
                    fontSize = 11.sp,
                    color = Lab.Violet,
                    modifier = Modifier.clickable { vm.clearJob() },
                )
            }
        }
        if (vm.jobSummary.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(
                vm.jobSummary,
                fontSize = 11.sp,
                color = if (vm.jobSuccess) Lab.Green else Lab.Red,
            )
        }
        Spacer(Modifier.height(10.dp))
        Console(vm.jobLog, maxHeight = 210)
    }
}
