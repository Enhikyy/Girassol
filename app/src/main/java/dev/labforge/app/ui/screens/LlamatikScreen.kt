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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.ui.components.Console
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.LabField
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LlamatikViewModel

@Composable
fun LlamatikScreen(onBack: () -> Unit) {
    val vm: LlamatikViewModel = viewModel()
    var prompt by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Lab.Cyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Extension, null, tint = Lab.Cyan, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Llamatik Runtime", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                Text("SDK 2.2.2 · tool registry · memória persistente", fontSize = 9.5.sp, color = Lab.Muted)
            }
            if (vm.streaming) SunflowerSpinner(28.dp, 8) else Pill(if (vm.initialized) "online" else "…", if (vm.initialized) Lab.Green else Lab.Amber)
        }

        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("status", fontSize = 10.sp, color = Lab.Muted, modifier = Modifier.weight(1f))
                Text(vm.status, fontSize = 10.sp, color = Lab.Cyan, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Text("${vm.tools.size} ferramentas registradas", fontSize = 10.sp, color = Lab.Text, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                vm.tools.take(6).forEach { Pill(it, Lab.Violet) }
            }
            if (vm.tools.size > 6) {
                Spacer(Modifier.height(4.dp))
                Text("+${vm.tools.size - 6} ferramentas", fontSize = 8.5.sp, color = Lab.Muted)
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "Core nativo do Llamatik desativado: ele embarca outro llama.cpp e colidiria com o motor de treino. " +
                    "A camada de agente, tools e memória do SDK está ativa; a inferência usa o motor do Girassol.",
                fontSize = 8.5.sp, color = Lab.Muted, lineHeight = 12.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Ferramenta REST via Retrofit", "jsonplaceholder.typicode.com · sem auth")
        Spacer(Modifier.height(8.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cloud, null, tint = Lab.Green, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("public_api_posts", fontSize = 11.sp, color = Lab.Text, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Lab.Green.copy(alpha = 0.16f))
                        .clickable(enabled = !vm.testingTool) { vm.testRestApi() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(if (vm.testingTool) "…" else "executar", fontSize = 9.5.sp, color = Lab.Green)
                }
            }
            vm.toolResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 9.5.sp, color = Lab.Muted, lineHeight = 14.sp, maxLines = 8)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Streaming do agente", "AgentStreamChunk em tempo real")
        Spacer(Modifier.height(8.dp))
        GlassCard {
            LabField("Prompt", prompt, placeholder = "Pesquise sobre KDA e resuma em 3 tópicos", singleLine = false) { prompt = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    if (vm.streaming) "Parar" else "Stream",
                    Modifier.weight(1f),
                    Icons.Rounded.PlayArrow,
                    enabled = vm.initialized && (prompt.isNotBlank() || vm.streaming),
                ) {
                    if (!vm.streaming) vm.stream(prompt)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Lab.Outline, RoundedCornerShape(12.dp))
                        .clickable { vm.clearOutput() }
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                ) { Text("limpar", fontSize = 10.sp, color = Lab.Muted) }
            }
            Spacer(Modifier.height(10.dp))
            Console(vm.output.ifBlank { "aguardando…" }, maxHeight = 260)
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Memória persistente", "ciclo de sono / consolidação")
        Spacer(Modifier.height(8.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = Lab.Pink, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("${vm.memoryCount} mensagens", fontSize = 11.sp, color = Lab.Text, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Lab.Pink.copy(alpha = 0.14f))
                        .clickable { vm.consolidate() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("consolidar", fontSize = 9.5.sp, color = Lab.Pink) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "A memória usa PersistentMemory + MemoryStorage em arquivo. O consolidate grava o estado e alimenta o ciclo de sono.",
                fontSize = 8.5.sp, color = Lab.Muted, lineHeight = 12.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Lab.Outline, RoundedCornerShape(12.dp))
                .clickable { onBack() }
                .padding(11.dp),
            contentAlignment = Alignment.Center,
        ) { Text("voltar", fontSize = 11.sp, color = Lab.Muted) }

        Spacer(Modifier.height(30.dp))
    }
}
