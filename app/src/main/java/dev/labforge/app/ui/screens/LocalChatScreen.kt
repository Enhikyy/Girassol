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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.Segmented
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel
import dev.labforge.app.vm.LocalChatViewModel

@Composable
fun LocalChatScreen(
    chatVm: LocalChatViewModel,
    labVm: LabViewModel,
    onOpenModels: () -> Unit,
    onOpenAgent: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val model = labVm.selectedModel

    LaunchedEffect(chatVm.messages.size, selectedTab) {
        if (selectedTab == 0 && chatVm.messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(chatVm.messages.size - 1) }
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) chatVm.refreshLogs()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Lab.Cyan, Lab.Violet))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.SmartToy, null, tint = Lab.OnPrimary, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chat nativo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                    Text(
                        if (chatVm.running) chatVm.status else chatVm.activeModel.ifBlank { "llama.cpp · CPU · offline" },
                        fontSize = 11.sp, color = if (chatVm.running) Lab.Green else Lab.Muted, maxLines = 1,
                    )
                }
                if (chatVm.running) {
                    SunflowerSpinner(26.dp, 8)
                    Spacer(Modifier.width(6.dp))
                }
                Pill(if (chatVm.running) "gerando" else "GGUF", if (chatVm.running) Lab.Green else Lab.Cyan)
            }
            Spacer(Modifier.height(8.dp))
            Segmented(listOf("Conversa", "Logs"), selectedTab, Modifier.fillMaxWidth()) {
                selectedTab = it
                if (it == 1) chatVm.refreshLogs()
            }
            Spacer(Modifier.height(7.dp))
        }

        if (selectedTab == 1) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagnóstico detalhado", color = Lab.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Atualizar", color = Lab.Cyan, fontSize = 12.sp,
                        modifier = Modifier.clickable { chatVm.refreshLogs() }.padding(8.dp))
                }
                GlassCard(Modifier.weight(1f), padding = 8) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            chatVm.logs.ifBlank { "Ainda não há registros." },
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            color = Lab.Text, lineHeight = 13.sp,
                        )
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(model?.name ?: "Nenhum modelo selecionado", color = if (model == null) Lab.Amber else Lab.Text, fontSize = 12.sp)
                    Text(
                        if (model != null) (model.meta?.quantLabel ?: "GGUF") + " · contexto adaptativo · histórico local"
                        else "Importe ou selecione um GGUF para conversar sem endpoint.",
                        color = Lab.Muted, fontSize = 10.sp,
                    )
                }
                Text("Modelos", color = Lab.Cyan, fontSize = 12.sp,
                    modifier = Modifier.clickable { onOpenModels() }.padding(8.dp))
            }
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (chatVm.messages.isEmpty()) {
                    item {
                        GlassCard {
                            Text(
                                "Converse diretamente com o GGUF selecionado. A geração roda no llama.cpp nativo, sem API, chave ou rede.",
                                color = Lab.Text, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Memórias relevantes do Caderno são injetadas no contexto. O histórico fica no armazenamento privado do app.",
                                color = Lab.Muted, fontSize = 11.sp, lineHeight = 15.sp,
                            )
                        }
                    }
                }
                items(chatVm.messages, key = { it.id }) { message ->
                    val user = message.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Box(
                            Modifier.fillMaxWidth(if (user) 0.88f else 0.96f)
                                .clip(RoundedCornerShape(if (user) 16.dp else 12.dp))
                                .background(
                                    if (user) Brush.linearGradient(listOf(Lab.Violet, Lab.VioletDeep))
                                    else Brush.linearGradient(listOf(Lab.Surface, Lab.SurfaceHigh)),
                                )
                                .border(1.dp, if (user) Color.Transparent else Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                message.text.ifBlank { if (chatVm.running && !user) "gerando…" else "" },
                                color = if (user) Lab.OnPrimary else Lab.Text,
                                fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Lab.Surface)
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Lab.Text),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (!chatVm.running && input.isNotBlank()) {
                                if (model != null) {
                                    chatVm.send(model.file.absolutePath, model.name, input)
                                    input = ""
                                }
                            }
                        }),
                        decorationBox = { inner ->
                            if (input.isEmpty()) Text("Mensagem para o modelo local…", color = Lab.Muted, fontSize = 13.sp)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(if (chatVm.running) Lab.Red else Lab.Violet)
                        .clickable {
                            if (chatVm.running) chatVm.stop()
                            else if (input.isNotBlank()) {
                                if (model != null) {
                                    chatVm.send(model.file.absolutePath, model.name, input)
                                    input = ""
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (chatVm.running) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                        null, tint = Lab.OnPrimary, modifier = Modifier.size(19.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "contexto " + chatVm.contextSize + " · " + chatVm.completionTokens + " tok · " + "%.1f".format(chatVm.tokensPerSecond) + " tok/s",
                    color = Lab.Muted, fontSize = 10.sp, maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Agente + ferramentas", color = Lab.Cyan, fontSize = 10.sp,
                        modifier = Modifier.clickable {
                            val transcript = chatVm.messages.takeLast(12).joinToString("\n\n") { message ->
                                (if (message.role == "user") "USUÁRIO" else "ASSISTENTE") + ":\n" + message.text
                            }.takeLast(6000)
                            onOpenAgent(transcript)
                        }.padding(4.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.Delete, "Limpar conversa", tint = Lab.Muted,
                        modifier = Modifier.size(16.dp).clickable { chatVm.clearHistory() },
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.BugReport, "Ver logs", tint = Lab.Muted,
                        modifier = Modifier.size(16.dp).clickable { selectedTab = 1; chatVm.refreshLogs() },
                    )
                }
            }
        }
    }
}
