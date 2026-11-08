package dev.labforge.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Science
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.model.AssetKind
import dev.labforge.app.ui.components.EmptyState
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.AnimatedBars
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.StatTile
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.components.humanNumber
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel

@Composable
fun ImatrixPane(vm: LabViewModel) {
    var selectedPath by remember { mutableStateOf("") }
    val selected = vm.imatrices.firstOrNull { it.absolutePath == selectedPath } ?: vm.imatrixFile

    LaunchedEffect(vm.imatrices) {
        if (selectedPath.isBlank()) selectedPath = vm.imatrixFile?.absolutePath ?: vm.imatrices.firstOrNull()?.absolutePath ?: ""
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAsset(uri, AssetKind.IMATRIX) { f, _ ->
            if (f != null) {
                selectedPath = f.absolutePath
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

        if (vm.imatrices.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.GridOn,
                title = "Nenhuma importance matrix",
                body = "Importe um arquivo .imatrix (GGUF gerado por llama-imatrix ou o formato legado .dat). " +
                    "Ele mede a ativação de cada tensor para guiar a quantização.",
                actionLabel = "Importar imatrix",
                onAction = { picker.launch(arrayOf("*/*")) },
            )
            return@Column
        }

        GlassCard {
            SelectRow(
                label = "Arquivo imatrix",
                current = selected?.name ?: "",
                options = vm.imatrices.map { "${it.name} · ${humanBytes(it.length())}" },
            ) { idx ->
                selectedPath = vm.imatrices[idx].absolutePath
                vm.parseImatrix(vm.imatrices[idx])
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Importar outro", Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
                if (selected != null) {
                    Box(
                        Modifier
                            .weight(0.6f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Lab.SurfaceHigh)
                            .clickable { vm.parseImatrix(selected) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Recarregar", fontSize = 12.sp, color = Lab.Text)
                    }
                }
            }
        }

        val report = vm.imatrixReport
        if (vm.loadingImatrix) {
            Spacer(Modifier.height(16.dp))
            GlassCard { Text("Lendo matriz…", fontSize = 12.sp, color = Lab.Muted) }
        } else if (report != null) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("tensores", report.tensorCount.toString(), Lab.Violet, Modifier.weight(1f))
                StatTile("chunks", report.chunkCount.toString(), Lab.Cyan, Modifier.weight(1f))
                StatTile("valores", humanNumber(report.totalValues), Lab.Green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("importância média", "%.4f".format(report.meanImportance), Lab.Pink, Modifier.weight(1f))
                StatTile("camadas", report.layers.size.toString(), Lab.Amber, Modifier.weight(1f))
                StatTile("formato", if (report.legacy) "DAT" else "GGUF", Lab.Indigo, Modifier.weight(1f))
            }

            if (report.datasets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "dataset: ${report.datasets.joinToString(", ").take(140)}",
                    fontSize = 11.sp,
                    color = Lab.Muted,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Cobertura", "imatrix × tensores do modelo") {
                if (vm.selectedModel != null) {
                    Text(
                        "vincular",
                        fontSize = 11.sp,
                        color = Lab.Violet,
                        modifier = Modifier.clickable { vm.linkedModelForImatrix() },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassCard {
                val coverage = vm.imatrixCoverage
                if (coverage == null) {
                    Text(
                        "Selecione um modelo na aba Modelos para comparar a cobertura da imatrix.",
                        fontSize = 11.sp,
                        color = Lab.Muted,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${coverage.matched}/${coverage.totalModelTensors} tensores cobertos",
                            fontSize = 12.sp,
                            color = Lab.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(coverage.ratio * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (coverage.ratio > 0.9f) Lab.Green else Lab.Amber,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LabProgress(coverage.ratio, if (coverage.ratio > 0.9f) Lab.Green else Lab.Amber)
                    if (coverage.missing.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            "faltam: " + coverage.missing.take(4).joinToString(", ") +
                                if (coverage.missing.size > 4) " (+${coverage.missing.size - 4})" else "",
                            fontSize = 11.sp,
                            color = Lab.Muted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        if (report != null && report.layers.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Importância por camada", "média de |ativação|")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                AnimatedBars(
                    report.layers.map { it.importance.toFloat() },
                    Modifier.fillMaxWidth().height(96.dp),
                    Lab.Violet,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("blk.0", fontSize = 11.sp, color = Lab.Muted)
                    Text(
                        "pico: blk.${report.layers.maxByOrNull { it.importance }?.layer ?: 0}",
                        fontSize = 11.sp,
                        color = Lab.Cyan,
                    )
                    Text("blk.${report.layers.last().layer}", fontSize = 11.sp, color = Lab.Muted)
                }
            }
        }

        if (report != null && report.entries.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Tensores mais importantes", "top 12 por ativação média")
            Spacer(Modifier.height(10.dp))
            GlassCard {
                val top = report.entries.sortedByDescending { it.meanAbs }.take(12)
                val max = top.maxOfOrNull { it.meanAbs }?.takeIf { it > 0 } ?: 1.0
                top.forEach { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                e.name,
                                fontSize = 11.sp,
                                color = Lab.Text,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text("%.4f".format(e.meanAbs), fontSize = 11.sp, color = Lab.Cyan)
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Lab.SurfaceHigh),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((e.meanAbs / max).toFloat().coerceIn(0.02f, 1f))
                                    .fillMaxSize()
                                    .background(Lab.Violet.copy(alpha = 0.75f)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${report.entries.size} entradas · ${humanBytes(report.bytes)} lidos",
                    fontSize = 11.sp,
                    color = Lab.Muted,
                )
            }

            Spacer(Modifier.height(16.dp))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = Lab.Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Use esta imatrix na aba Quantizar para preservar os tensores de maior ativação.",
                        fontSize = 11.sp,
                        color = Lab.Muted,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
