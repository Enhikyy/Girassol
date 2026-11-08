package dev.labforge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SmartToy
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.Handoff
import dev.labforge.app.ui.components.ConversationWelcome
import dev.labforge.app.ui.components.ConversationComposer
import dev.labforge.app.ui.components.ExecutionCard
import dev.labforge.app.ui.components.Console
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.Segmented
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AgentMessage
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.LabViewModel

@Composable
fun AgentScreen(agentVm: AgentViewModel, vm: LabViewModel, onOpenCodex: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        Handoff.toChat?.let {
            agentVm.injectFromCodex(it)
            Handoff.toChat = null
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Lab.Cyan.copy(alpha = 0.8f), Lab.Violet))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.SmartToy, null, tint = Lab.OnPrimary, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                    Text(
                        if (agentVm.running) agentVm.status else "estudante de TI · git · terminal · caderno",
                        fontSize = 11.sp,
                        color = if (agentVm.running) Lab.Green else Lab.Muted,
                    )
                }
                if (agentVm.running) {
                    SunflowerSpinner(28.dp, 8)
                    Spacer(Modifier.width(4.dp))
                }
                Pill(if (agentVm.running) "executando" else "pronto", if (agentVm.running) Lab.Green else Lab.Muted)
            }
            Spacer(Modifier.height(8.dp))
            TokenMeter(agentVm)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("Chat", "Terminal", "Caderno"), tab, Modifier.fillMaxWidth()) { tab = it }
            Spacer(Modifier.height(4.dp))
        }
        when (tab) {
            0 -> ChatPane(agentVm, vm, onOpenCodex)
            1 -> TerminalPane(agentVm)
            else -> NotebookPane(agentVm)
        }
    }
}

@Composable
private fun ChatPane(agentVm: AgentViewModel, vm: LabViewModel, onOpenCodex: () -> Unit) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(agentVm.messages.size) {
        if (agentVm.messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(agentVm.messages.size) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!dev.labforge.app.core.agent.LlmClient.ready(vm.settings)) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                GlassCard(padding = 11) {
                    Text(
                        "Selecione um GGUF instruct em Ajustes para usar o agente local ou configure um endpoint de API.",
                        fontSize = 11.sp,
                        color = Lab.Amber,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (agentVm.messages.isEmpty()) {
                item { ConversationWelcome { input = it } }
            }
            items(agentVm.messages, key = { it.id }) { m -> MessageBubble(m, onOpenCodex) }
            item { Spacer(Modifier.height(6.dp)) }
        }

        ConversationComposer(input, agentVm.running, { input = it }, {
            if (input.isNotBlank() && !agentVm.running) { agentVm.send(input.trim()); input = "" }
        }, agentVm::stop)
    }
}

@Composable
private fun MessageBubble(m: AgentMessage, onOpenCodex: () -> Unit) {
    when (m.role) {
        AgentMessage.Role.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .background(Brush.linearGradient(listOf(Lab.Violet.copy(alpha = 0.85f), Lab.VioletDeep)))
                    .padding(12.dp),
            ) {
                Text(m.text, fontSize = 14.sp, color = Lab.OnPrimary, lineHeight = 20.sp)
            }
        }

        AgentMessage.Role.THOUGHT -> ExecutionCard("Análise do agente", m.text, "Registro da execução")

        AgentMessage.Role.TOOL -> ExecutionCard(m.toolName.ifBlank { "Ferramenta" }, m.text,
            if (m.pending) "Aguardando resultado" else if (m.ok) "Executada" else "Falhou", m.toolArgs, failed = !m.ok && !m.pending)

        AgentMessage.Role.FINAL -> GlassCard(padding = 13) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SmartToy, null, tint = Lab.Cyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("resultado", fontSize = 11.sp, color = Lab.Cyan, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(7.dp))
            Text(m.text, fontSize = 12.sp, color = Lab.Text, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "abrir no Codex →",
                fontSize = 11.sp,
                color = Lab.Violet,
                modifier = Modifier.clickable {
                    Handoff.toCodex = m.text
                    onOpenCodex()
                },
            )
        }

        AgentMessage.Role.ERROR -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Lab.Red.copy(alpha = 0.10f))
                .border(1.dp, Lab.Red.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(11.dp),
        ) {
            Text(m.text, fontSize = 11.sp, color = Lab.Red, lineHeight = 15.sp)
        }

        AgentMessage.Role.SYSTEM -> Text(m.text, fontSize = 11.sp, color = Lab.Muted)
    }
}

@Composable
private fun TerminalPane(agentVm: AgentViewModel) {
    var command by remember { mutableStateOf("") }
    val quick = listOf("pwd", "ls -la", "df -h", "uptime", "cat /proc/meminfo | head -5")

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("status", "commit", "log", "reset").forEach { action ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Lab.Surface)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(9.dp))
                        .clickable { agentVm.gitAction(action) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text("git $action", fontSize = 11.sp, color = Lab.Cyan, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (agentVm.gitOut.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                agentVm.gitOut.take(800),
                fontSize = 11.sp,
                color = Lab.Muted,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Console(
            agentVm.terminalOut.ifBlank { "sandbox sh (toybox)\ndigite um comando abaixo" },
            Modifier.weight(1f),
            maxHeight = 2000,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quick.forEach { q ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Lab.Surface)
                        .clickable { command = q }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) { Text(q, fontSize = 11.sp, color = Lab.Muted, fontFamily = FontFamily.Monospace) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0C08))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 11.dp, vertical = 11.dp),
            ) {
                Text("$ ", fontSize = 11.sp, color = Lab.Green, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp, color = Lab.Text, fontFamily = FontFamily.Monospace,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (command.isNotBlank()) { agentVm.runTerminal(command); command = "" }
                    }),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Lab.Violet)
                    .clickable {
                        if (command.isNotBlank()) { agentVm.runTerminal(command); command = "" }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Enviar", tint = Lab.OnPrimary, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun NotebookPane(agentVm: AgentViewModel) {
    LaunchedEffect(Unit) { agentVm.refreshLessons() }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        if (agentVm.lessonList.isEmpty()) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MenuBook, null, tint = Lab.Pink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Caderno vazio", fontSize = 12.sp, color = Lab.Text, fontWeight = FontWeight.Medium)
                        Text(
                            "Quando o agente resolve um bug, ele grava a solução aqui com memory_save.",
                            fontSize = 11.sp,
                            color = Lab.Muted,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(agentVm.lessonList, key = { it.id }) { l ->
                    GlassCard(padding = 13) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MenuBook, null, tint = Lab.Pink, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                l.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Lab.Text,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.Delete,
                                null,
                                tint = Lab.Muted,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { agentVm.deleteLesson(l.id) },
                            )
                        }
                        if (l.tags.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                l.tags.take(4).forEach { t -> Pill(t, Lab.Violet) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(l.body.take(420), fontSize = 11.sp, color = Lab.Muted, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}


@Composable
private fun TokenMeter(agentVm: AgentViewModel) {
    val tps = if (agentVm.tokensPerSecond > 0)
        "%.1f t/s".format(agentVm.tokensPerSecond)
    else if (agentVm.estimatedSeconds > 0) "~${dev.labforge.app.core.Perf.fmtDuration(agentVm.estimatedSeconds)}"
    else "—"

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Lab.Surface.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "tokens nativos ${agentVm.localPromptTokens}",
            fontSize = 11.sp,
            color = Lab.Cyan,
            fontFamily = FontFamily.Monospace,
        )
        Text("  ·  ", fontSize = 11.sp, color = Lab.Outline)
        Text(
            "envio ${agentVm.promptTokens} · recebido ${agentVm.completionTokens}",
            fontSize = 11.sp,
            color = Lab.Muted,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        Text(tps, fontSize = 11.sp, color = Lab.Green, fontFamily = FontFamily.Monospace)
    }
}
