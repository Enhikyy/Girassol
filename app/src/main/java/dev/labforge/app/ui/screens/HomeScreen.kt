package dev.labforge.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.R
import dev.labforge.app.core.native.LlamaBridge
import dev.labforge.app.service.MemoryBooster
import dev.labforge.app.service.MemoryGuard
import dev.labforge.app.ui.Routes
import dev.labforge.app.ui.components.AnimatedNumber
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.ProgressRing
import dev.labforge.app.ui.components.PulsingDot
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.AutodidactViewModel
import dev.labforge.app.vm.LabViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: LabViewModel,
    agentVm: AgentViewModel,
    onNavigate: (String) -> Unit,
    onOpenLab: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val study: AutodidactViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var availableGb by remember { mutableStateOf(MemoryGuard.availableGb(ctx)) }
    var boosting by remember { mutableStateOf(false) }
    var boostResult by remember { mutableStateOf<MemoryBooster.Result?>(null) }

    LaunchedEffect(vm.models) { availableGb = MemoryGuard.availableGb(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.girassol_mark),
                contentDescription = "Girassol",
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Girassol", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                Text("pesquisa · dados · LoRA · agentes", fontSize = 12.sp, color = Lab.Muted)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Lab.Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot(if (LlamaBridge.isAvailable) Lab.Green else Lab.Red, 6.dp)
                Spacer(Modifier.width(5.dp))
                Text("%.1f GB".format(availableGb), fontSize = 12.sp, color = Lab.Text)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Lab.Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                    .clickable { onNavigate(Routes.SETTINGS) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, "Ajustes", tint = Lab.Muted, modifier = Modifier.size(17.dp))
            }
        }

        Spacer(Modifier.height(18.dp))

        // ------------------------------------------------------------- hero
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3B2E0B), Color(0xFF1B1C0C), Color(0xFF0D1B12)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp)),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("AUTÔNOMO", Lab.Green, filled = true)
                    Spacer(Modifier.width(6.dp))
                    Pill("llama.cpp", Lab.Cyan)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Pesquise. Aprenda.\nFaça florescer.",
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "O agente pesquisa em 7 fontes gratuitas (incluindo GitHub) e no Brave Search, se configurado. Depois monta o currículo, gera o dataset e ajusta os hiperparâmetros.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(alpha = 0.66f),
                    modifier = Modifier.fillMaxWidth(0.78f),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GradientButton("Pesquisar tema") { onOpenLab(3) }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .clickable { onNavigate(Routes.CODEX) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Text("Abrir Codex", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 6.dp)
                    .size(104.dp),
                contentAlignment = Alignment.Center,
            ) {
                SunflowerSpinner(96.dp, 14)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ------------------------------------------------------- bento cards
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassCard(Modifier.weight(1f), padding = 13) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RAM", fontSize = 12.sp, color = Lab.Muted, modifier = Modifier.weight(1f))
                    PulsingDot(if (availableGb > 2) Lab.Green else Lab.Amber, 6.dp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = (availableGb / vm.deviceRamGb.coerceAtLeast(1.0)).toFloat(),
                        size = 62.dp,
                        stroke = 6.dp,
                        color = Lab.Cyan,
                        label = "%.1f".format(availableGb),
                        caption = "GB",
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            boostResult?.let { "+${it.freedMb} MB" } ?: "livre",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (boostResult != null) Lab.Green else Lab.Text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Lab.Violet.copy(alpha = 0.18f))
                                .clickable(enabled = !boosting) {
                                    scope.launch {
                                        boosting = true
                                        val r = MemoryBooster.boost(ctx)
                                        boostResult = r
                                        availableGb = r.afterMb / 1024.0
                                        boosting = false
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Text(
                                if (boosting) "…" else "Liberar",
                                fontSize = 12.sp,
                                color = Lab.Violet,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            GlassCard(Modifier.weight(1f), padding = 13, onClick = { onOpenLab(3) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = Lab.Cyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Pesquisa", fontSize = 12.sp, color = Lab.Muted, modifier = Modifier.weight(1f))
                    if (study.running) PulsingDot(Lab.Green, 6.dp)
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    study.bundle?.theme ?: "novo tema",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Lab.Text,
                    maxLines = 2,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    study.bundle?.let { "${it.docs.size} fontes · ${it.sourceCounts.size} APIs" } ?: "7 fontes + Brave",
                    fontSize = 12.sp,
                    color = Lab.Muted,
                )
                Spacer(Modifier.height(7.dp))
                study.preset?.let { p ->
                    Pill("rank ${p.rank} · ${p.epochs} ep", Lab.Violet)
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("modelos", vm.models.size, Lab.Violet, Modifier.weight(1f))
            StatCard("imatrices", vm.imatrices.size, Lab.Cyan, Modifier.weight(1f))
            StatCard("saídas", vm.outputs.size, Lab.Green, Modifier.weight(1f))
            StatCard("lições", agentVm.lessonList.size, Lab.Pink, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Pipeline", "pesquisa → dataset → LoRA → GGUF") {
            if (vm.jobRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(Lab.Amber, 6.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("${(vm.jobProgress * 100).toInt()}%", fontSize = 12.sp, color = Lab.Amber)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PipelineStep("1", "Pesquisa", "7+ fontes", Lab.Cyan)
                PipelineLine()
                PipelineStep("2", "Currículo", "LLM", Lab.Violet)
                PipelineLine()
                PipelineStep("3", "Dataset", "auto", Lab.Green)
                PipelineLine()
                PipelineStep("4", "LoRA", "rank", Lab.Amber)
                PipelineLine()
                PipelineStep("5", "GGUF", "adapter", Lab.Pink)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Lab.SurfaceHigh.copy(alpha = 0.55f))
                    .clickable { onOpenLab(2) }
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bolt, null, tint = Lab.Amber, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "A imatrix protege os tensores mais ativos durante a quantização.",
                    fontSize = 12.sp, color = Lab.Muted, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = Lab.Muted, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Modelos recentes", "toque para inspecionar") {
            Text("ver todos", fontSize = 12.sp, color = Lab.Violet, modifier = Modifier.clickable { onNavigate(Routes.MODELS) })
        }
        Spacer(Modifier.height(9.dp))
        if (vm.models.isEmpty()) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = Lab.Violet, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nenhum modelo importado", fontSize = 12.sp, color = Lab.Text, fontWeight = FontWeight.Medium)
                        Text("Copie um .gguf para começar", fontSize = 12.sp, color = Lab.Muted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Lab.Muted)
                }
            }
        } else {
            vm.models.take(3).forEach { m ->
                GlassCard(
                    Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    onClick = { onNavigate(Routes.MODEL + "/" + m.file.absolutePath) },
                    padding = 13,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Lab.Violet.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Memory, null, tint = Lab.Violet, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${m.meta?.parameterLabel ?: "?"} params · ${humanBytes(m.sizeBytes)}",
                                fontSize = 12.sp, color = Lab.Muted,
                            )
                        }
                        Pill(m.meta?.quantLabel ?: "?", Lab.Cyan)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassCard(Modifier.weight(1f), onClick = { onNavigate(Routes.CODEX) }, padding = 14) {
                Icon(Icons.Rounded.Code, null, tint = Lab.Violet, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
                Text("Codex", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                Text("implementa e testa até passar", fontSize = 12.sp, color = Lab.Muted, lineHeight = 13.sp)
            }
            GlassCard(Modifier.weight(1f), onClick = { onNavigate(Routes.AGENT) }, padding = 14) {
                Icon(Icons.Rounded.SmartToy, null, tint = Lab.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
                Text("Chat", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                Text("agente com pensamentos e ferramentas", fontSize = 12.sp, color = Lab.Muted, lineHeight = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        GlassCard(onClick = { onNavigate(Routes.LLAMATIK) }, padding = 13) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Extension, null, tint = Lab.Pink, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Llamatik Runtime", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text)
                    Text("tool registry · memória persistente · streaming", fontSize = 12.sp, color = Lab.Muted)
                }
                Pill("SDK 2.2.2", Lab.Pink)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(vm.nativeVersion, fontSize = 12.sp, color = Lab.Muted.copy(alpha = 0.7f))
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Lab.Surface.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(15.dp))
            .padding(12.dp),
    ) {
        AnimatedNumber(value, color = color, size = 19)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = Lab.Muted, maxLines = 1)
    }
}

@Composable
private fun PipelineStep(number: String, title: String, sub: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(5.dp))
        Text(title, fontSize = 12.sp, color = Lab.Text, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(sub, fontSize = 12.sp, color = Lab.Muted, maxLines = 1)
    }
}

@Composable
private fun PipelineLine() {
    Box(
        Modifier
            .padding(top = 13.dp)
            .width(9.dp)
            .height(1.dp)
            .background(Lab.Outline),
    )
}
