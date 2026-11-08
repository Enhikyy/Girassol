package dev.labforge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.Handoff
import dev.labforge.app.ui.components.Console
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.LabField
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.Segmented
import dev.labforge.app.ui.components.StatTile
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AgentMessage
import dev.labforge.app.vm.CodexViewModel

@Composable
fun CodexScreen(vm: CodexViewModel, onOpenChat: () -> Unit) {
    var task by rememberSaveable { mutableStateOf("") }
    var testCommand by rememberSaveable { mutableStateOf("ls -la") }
    var autoTest by rememberSaveable { mutableStateOf(true) }
    var unlimited by rememberSaveable { mutableStateOf(true) }
    var maxIterations by rememberSaveable { mutableStateOf(6) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var showConfig by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val prefill = Handoff.toCodex
        if (!prefill.isNullOrBlank()) {
            task = prefill
            Handoff.toCodex = null
            showConfig = true
        }
    }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) runCatching { listState.animateScrollToItem(vm.messages.size - 1) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Lab.Violet.copy(alpha = 0.16f))
                        .border(1.dp, Lab.Violet.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Code, null, tint = Lab.Violet, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Codex", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                    Text(
                        when {
                            vm.running -> vm.status
                            vm.iteration > 0 -> "pronto · ${vm.iteration} iterações"
                            else -> "agente autônomo de código e testes"
                        },
                        fontSize = 11.sp,
                        color = if (vm.running) Lab.Green else Lab.Muted,
                    )
                }
                if (vm.running) {
                    SunflowerSpinner(28.dp, 8)
                    Spacer(Modifier.width(4.dp))
                }
                Pill(if (vm.running) "autônomo" else "pronto", if (vm.running) Lab.Green else Lab.Muted)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (showConfig) "−" else "+",
                    fontSize = 18.sp,
                    color = Lab.Muted,
                    modifier = Modifier.clickable { showConfig = !showConfig }.padding(6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            if (showConfig) {
                GlassCard {
                    LabField(
                        label = "Tarefa",
                        value = task,
                        placeholder = "Ex.: crie um conversor JSONL→texto em sh, com testes, e faça passar",
                        singleLine = false,
                        onValueChange = { task = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    LabField(
                        label = "Comando de teste",
                        value = testCommand,
                        placeholder = "ex.: sh tests/run.sh",
                        onValueChange = { testCommand = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    ToggleRowCompact(
                        title = "Rodar testes e corrigir sozinho",
                        checked = autoTest,
                        onChange = { autoTest = it },
                    )
                    if (autoTest) {
                        Spacer(Modifier.height(6.dp))
                        ToggleRowCompact(
                            title = "Loop sem limite até passar",
                            checked = unlimited,
                            onChange = { unlimited = it },
                        )
                        if (!unlimited) {
                            Spacer(Modifier.height(8.dp))
                            Stepper("Máximo de iterações", maxIterations, listOf(2, 4, 6, 10), Modifier.fillMaxWidth()) {
                                maxIterations = it
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton(
                            if (vm.running) "Parar" else "Executar",
                            Modifier.weight(1f),
                            if (vm.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            enabled = vm.running || task.isNotBlank(),
                        ) {
                            if (vm.running) vm.stop()
                            else vm.start(task, testCommand, autoTest, unlimited, maxIterations)
                        }
                        if (!vm.running && vm.messages.isNotEmpty()) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, Lab.Outline, RoundedCornerShape(14.dp))
                                    .clickable { vm.clear(); task = "" }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                            ) { Text("limpar", fontSize = 12.sp, color = Lab.Muted) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("iterações", vm.iteration.toString(), Lab.Violet, Modifier.weight(1f))
                StatTile("tokens", (vm.totalPromptTokens + vm.totalCompletionTokens).toString(), Lab.Cyan, Modifier.weight(1f))
                StatTile("tempo", "%ds".format(vm.elapsedMs / 1000), Lab.Green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("Execução", "Diff", "Testes"), tab, Modifier.fillMaxWidth()) { tab = it }
            Spacer(Modifier.height(4.dp))
        }

        when (tab) {
            0 -> Column(Modifier.fillMaxSize()) {
                if (vm.liveText.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Lab.Violet.copy(alpha = 0.08f))
                            .padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Lab.Violet, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            vm.liveText.takeLast(200),
                            fontSize = 11.sp,
                            color = Lab.Muted,
                            maxLines = 3,
                            lineHeight = 14.sp,
                        )
                    }
                }
                if (vm.messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Code, null, tint = Lab.Violet.copy(alpha = 0.5f), modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Descreva o que construir.", fontSize = 13.sp, color = Lab.Text)
                            Text("Ele planeja, edita, roda testes e corrige até passar.", fontSize = 11.sp, color = Lab.Muted)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(vm.messages, key = { it.id }) { m -> CodexMessage(m) }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
                if (!vm.running && vm.messages.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Lab.Cyan.copy(alpha = 0.10f))
                                .border(1.dp, Lab.Cyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .clickable {
                                    vm.shareToChat()
                                    onOpenChat()
                                }
                                .padding(11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Send, null, tint = Lab.Cyan, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Enviar resumo para o Chat", fontSize = 11.sp, color = Lab.Cyan, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            1 -> Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("working tree", fontSize = 11.sp, color = Lab.Muted, modifier = Modifier.weight(1f))
                    Text(
                        "atualizar",
                        fontSize = 11.sp,
                        color = Lab.Cyan,
                        modifier = Modifier.clickable { vm.refreshDiff() },
                    )
                }
                Console(vm.diff.ifBlank { "sem alterações capturadas ainda" }, Modifier.weight(1f), maxHeight = 4000)
                Spacer(Modifier.height(12.dp))
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Icon(Icons.Rounded.BugReport, null, tint = Lab.Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("última execução de testes", fontSize = 11.sp, color = Lab.Muted)
                }
                Console(vm.testOutput.ifBlank { "os testes ainda não rodaram" }, Modifier.weight(1f), maxHeight = 4000)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ToggleRowCompact(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) Lab.Violet else Lab.SurfaceHigh)
                .border(1.dp, if (checked) Lab.Violet else Lab.Outline, RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 11.sp, color = if (checked) Lab.Text else Lab.Muted)
    }
}

@Composable
private fun CodexMessage(m: AgentMessage) {
    var expanded by remember(m.id) { mutableStateOf(false) }
    when (m.role) {
        AgentMessage.Role.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Lab.Violet.copy(alpha = 0.20f))
                    .border(1.dp, Lab.Violet.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) { Text(m.text, fontSize = 11.5.sp, color = Lab.Text, lineHeight = 16.sp) }
        }
        AgentMessage.Role.TOOL -> GlassCard(padding = 10, onClick = { expanded = !expanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Terminal,
                    null,
                    tint = if (m.ok) Lab.Cyan else Lab.Red,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    m.toolName.ifBlank { "tool" },
                    fontSize = 11.sp,
                    color = Lab.Text,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (m.pending) Text("…", color = Lab.Amber, fontSize = 12.sp)
                else Text(if (expanded) "−" else "+", color = Lab.Muted, fontSize = 12.sp)
            }
            if (m.toolArgs.isNotBlank() && m.toolArgs != "{}") {
                Text(
                    m.toolArgs.take(140),
                    fontSize = 11.sp,
                    color = Lab.Muted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                m.text.take(if (expanded) 8000 else 260),
                fontSize = 11.sp,
                color = if (m.ok) Color(0xFFB9C6E8) else Lab.Red.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        AgentMessage.Role.THOUGHT -> Row(Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Lab.Violet.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                m.text,
                fontSize = 11.sp,
                color = Lab.Muted,
                lineHeight = 15.sp,
                maxLines = if (expanded) 80 else 10,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        AgentMessage.Role.FINAL -> GlassCard(padding = 11) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Lab.Cyan, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("resultado", fontSize = 11.sp, color = Lab.Cyan, fontWeight = FontWeight.SemiBold)
            }
            Text(m.text, fontSize = 11.5.sp, color = Lab.Text, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
        }
        AgentMessage.Role.ERROR -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Lab.Red.copy(alpha = 0.10f))
                .padding(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Error, null, tint = Lab.Red, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(m.text, fontSize = 11.sp, color = Lab.Red)
            }
        }
        AgentMessage.Role.SYSTEM -> Text(m.text, fontSize = 11.sp, color = Lab.Muted, modifier = Modifier.padding(vertical = 2.dp))
    }
}

