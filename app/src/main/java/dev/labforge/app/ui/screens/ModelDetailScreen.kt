package dev.labforge.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.model.ModelEntry
import dev.labforge.app.ui.components.DonutChart
import dev.labforge.app.ui.components.DonutSegment
import dev.labforge.app.ui.components.GhostButton
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.KeyValueRow
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.StatTile
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.components.humanNumber
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModelDetailScreen(vm: LabViewModel, path: String, onBack: () -> Unit, onOpenLab: (Int) -> Unit) {
    val entry by produceState<ModelEntry?>(null, path) {
        value = withContext(Dispatchers.IO) { vm.store.readModel(File(path)) }
    }
    var showDelete by remember { mutableStateOf(false) }
    var showAllMeta by remember { mutableStateOf(false) }
    var showTensors by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Lab.Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Lab.Text, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry?.name ?: "Modelo", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Lab.Text, maxLines = 1)
                Text("detalhes do arquivo GGUF", fontSize = 11.sp, color = Lab.Muted)
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Lab.Red.copy(alpha = 0.10f))
                    .clickable { showDelete = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, null, tint = Lab.Red, modifier = Modifier.size(17.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        val meta = entry?.meta
        if (meta == null) {
            GlassCard {
                Text(
                    entry?.error ?: "Lendo cabeçalho…",
                    fontSize = 12.sp,
                    color = Lab.Red,
                )
            }
        } else {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(meta.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Lab.Text, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text(entry!!.file.name, fontSize = 11.sp, color = Lab.Muted, maxLines = 1)
                    }
                    Pill(meta.quantLabel, Lab.Cyan, filled = true)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("params", meta.parameterLabel, Lab.Violet, Modifier.weight(1f))
                    StatTile("tamanho", humanBytes(meta.totalBytes), Lab.Cyan, Modifier.weight(1f))
                    StatTile("camadas", meta.blockCount.toString(), Lab.Green, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("contexto", humanNumber(meta.contextLength), Lab.Pink, Modifier.weight(1f))
                    StatTile("embed", meta.embeddingLength.toString(), Lab.Amber, Modifier.weight(1f))
                    StatTile("heads", "${meta.headCount}/${meta.headCountKv}", Lab.Indigo, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Text("mistura de quantização", fontSize = 11.sp, color = Lab.Muted)
                Spacer(Modifier.height(8.dp))
                val mix = meta.quantMix.entries.sortedByDescending { it.value }
                val colors = listOf(Lab.Violet, Lab.Cyan, Lab.Green, Lab.Pink, Lab.Amber, Lab.Indigo, Lab.Red)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        segments = mix.mapIndexed { i, e ->
                            DonutSegment(e.key.label, e.value.toFloat(), colors[i % colors.size])
                        },
                        size = 96.dp,
                        stroke = 13.dp,
                        centerLabel = meta.quantLabel,
                        centerCaption = "mix",
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        mix.take(6).forEachIndexed { i, e ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(colors[i % colors.size]))
                                Spacer(Modifier.width(6.dp))
                                Text(e.key.label, fontSize = 11.sp, color = Lab.Text, modifier = Modifier.weight(1f))
                                Text("${e.value}", fontSize = 11.sp, color = Lab.Muted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Quantizar", Modifier.weight(1f), Icons.Rounded.Science) { onOpenLab(0) }
                GhostButton("Treinar", Modifier.weight(1f)) { onOpenLab(1) }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Metadados", "${meta.metadata.size} chaves lidas do cabeçalho")
            Spacer(Modifier.height(8.dp))
            GlassCard {
                val important = listOf(
                    "general.architecture" to meta.architecture,
                    "general.name" to meta.name,
                    "general.file_type" to meta.fileType,
                    "general.parameter_count" to meta.parameterCount.toString(),
                    "tokenizer.ggml.model" to (meta.tokenizerModel ?: "—"),
                    "${meta.architecture}.context_length" to meta.contextLength.toString(),
                    "${meta.architecture}.embedding_length" to meta.embeddingLength.toString(),
                    "${meta.architecture}.block_count" to meta.blockCount.toString(),
                    "${meta.architecture}.attention.head_count" to meta.headCount.toString(),
                    "${meta.architecture}.attention.head_count_kv" to meta.headCountKv.toString(),
                )
                important.forEach { (k, v) -> KeyValueRow(k, v) }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (showAllMeta) "ocultar metadados completos" else "ver todos os ${meta.metadata.size} metadados",
                    fontSize = 11.sp,
                    color = Lab.Violet,
                    modifier = Modifier.clickable { showAllMeta = !showAllMeta },
                )
                if (showAllMeta) {
                    Spacer(Modifier.height(8.dp))
                    meta.metadata.take(400).forEach { m ->
                        val value = when (val v = m.value) {
                            null -> "—"
                            is List<*> -> "${v.size} itens"
                            else -> v.toString().take(160)
                        }
                        KeyValueRow(m.key, value)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Tensores", "${meta.tensors.size} tensores · ${humanNumber(meta.tensors.sumOf { it.elements })} valores")
            Spacer(Modifier.height(8.dp))
            GlassCard {
                val shown = if (showTensors) meta.tensors.take(400) else meta.tensors.take(8)
                shown.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.name,
                                fontSize = 11.sp,
                                color = Lab.Text,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                            Text(
                                t.dims.joinToString(" × ") + " · ${t.type.label}",
                                fontSize = 11.sp,
                                color = Lab.Muted,
                            )
                        }
                        Text(humanBytes(t.byteSize), fontSize = 11.sp, color = Lab.Cyan)
                    }
                }
                if (meta.tensors.size > 8) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (showTensors) "mostrar menos" else "mostrar mais ${minOf(392, meta.tensors.size - 8)} tensores",
                        fontSize = 11.sp,
                        color = Lab.Violet,
                        modifier = Modifier.clickable { showTensors = !showTensors },
                    )
                }
            }

            meta.chatTemplate?.let { template ->
                Spacer(Modifier.height(20.dp))
                SectionHeader("Chat template", "embutido no GGUF")
                Spacer(Modifier.height(8.dp))
                GlassCard {
                    Text(
                        template.take(900) + if (template.length > 900) "\n…" else "",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB9C6E8),
                        lineHeight = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Lab.SurfaceHigh, Lab.Surface)))
                    .padding(14.dp),
            ) {
                Column {
                    Text("Caminho no sandbox", fontSize = 11.sp, color = Lab.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(entry!!.file.absolutePath, fontSize = 11.sp, color = Lab.Text, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Versão GGUF v${meta.version} · ${meta.tensorCount} tensores · alinhamento ${meta.alignment}",
                        fontSize = 11.sp,
                        color = Lab.Muted,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Remover modelo?") },
            text = { Text("O arquivo ${entry!!.file.name} será apagado do cofre. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAsset(entry!!.file)
                    showDelete = false
                    onBack()
                }) { Text("Remover", color = Lab.Red) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar", color = Lab.Muted) } },
            containerColor = Lab.SurfaceHigh,
        )
    }
}
