package dev.labforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.R
import dev.labforge.app.ui.theme.Lab

/** Native layouts informed by downloaded Jetchat/Reply references; see design-references/. */
@Composable
fun StudioHero(onChat: () -> Unit, onStudy: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF393016), Color(0xFF18291E), Color(0xFF101B17))))
        .border(1.dp, Lab.Violet.copy(alpha = 0.24f), RoundedCornerShape(30.dp))) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_studio_spark), null, tint = Lab.Violet, modifier = Modifier.size(20.dp))
                    Text("SEU ESTÚDIO DE IA", color = Lab.Violet, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                }
                SunflowerSpinner(40.dp, 10)
            }
            Text("Uma ideia.\nNovas possibilidades.", color = Lab.Text, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
            Text("Converse, pesquise e transforme conhecimento em projetos. Seu próximo passo começa aqui.", color = Lab.Muted, fontSize = 14.sp, lineHeight = 21.sp)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onChat, colors = ButtonDefaults.buttonColors(containerColor = Lab.Violet, contentColor = Lab.OnPrimary), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("Começar conversa"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
                OutlinedButton(onStudy, colors = ButtonDefaults.outlinedButtonColors(contentColor = Lab.Text), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) { Text("Explorar um tema") }
            }
        }
    }
}

private data class StudioAction(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color)
private val actions = listOf(
    StudioAction("Conversar", "Ideias e respostas", Icons.Rounded.ChatBubbleOutline, Lab.Cyan),
    StudioAction("Criar código", "Do plano à execução", Icons.Rounded.Code, Lab.Violet),
    StudioAction("Pesquisar", "Conhecimento com fontes", Icons.Rounded.TravelExplore, Lab.Pink),
    StudioAction("Compilar APK", "Validar seu projeto", Icons.Rounded.Build, Lab.Green),
)

@Composable
fun WorkspaceGrid(onAction: (Int) -> Unit) {
    Text("O que vamos criar?", color = Lab.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth >= 840.dp && scale < 1.4f -> 4; maxWidth >= 340.dp && scale < 1.4f -> 2; else -> 1 }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { colIndex, action ->
                        Card(onClick = { onAction(rowIndex * columns + colIndex) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Lab.SurfaceHigh.copy(alpha = 0.7f), contentColor = Lab.Text)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(action.tint.copy(alpha = 0.12f)).padding(10.dp)) { Icon(action.icon, null, tint = action.tint, modifier = Modifier.size(22.dp)) }
                                    Icon(Icons.Rounded.NorthEast, null, tint = Lab.Muted, modifier = Modifier.size(16.dp))
                                }
                                Text(action.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(action.subtitle, color = Lab.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val starterPrompts = listOf(
    "Explorar uma ideia" to "Ajude-me a transformar uma ideia em um projeto Android com etapas claras.",
    "Revisar um dataset" to "Analise o dataset atual, encontre linhas inválidas e escreva um relatório com as correções sugeridas.",
    "Criar e testar" to "Crie um script que converte JSONL em texto de treino e teste com um arquivo de exemplo.",
)

@Composable
fun ConversationWelcome(onPrompt: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_studio_spark), null, tint = Lab.Violet, modifier = Modifier.size(36.dp))
        Text("Como posso ajudar\nvocê a criar?", fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
        Text("Comece com uma ideia ou escolha um ponto de partida.", color = Lab.Muted, fontSize = 13.sp, lineHeight = 19.sp)
        starterPrompts.forEachIndexed { i, (title, prompt) ->
            OutlinedCard(onClick = { onPrompt(prompt) }, colors = CardDefaults.outlinedCardColors(containerColor = Lab.Surface, contentColor = Lab.Text), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(listOf(Icons.Rounded.Lightbulb, Icons.Rounded.Dataset, Icons.Rounded.Code)[i], null, tint = Lab.Violet, modifier = Modifier.size(22.dp))
                    Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Icon(Icons.Rounded.ArrowOutward, null, tint = Lab.Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ConversationComposer(input: String, running: Boolean, onInput: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Lab.Bg, contentColor = Lab.Text, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            OutlinedTextField(input, onInput, Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                placeholder = { Text("Descreva a tarefa…") }, maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Lab.Violet, unfocusedBorderColor = Lab.Outline, focusedTextColor = Lab.Text, unfocusedTextColor = Lab.Text,
                    focusedContainerColor = Lab.Surface, unfocusedContainerColor = Lab.Surface, cursorColor = Lab.Violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank() && !running) onSend() }),
                trailingIcon = {
                    IconButton(onClick = { if (running) onStop() else onSend() }, enabled = running || input.isNotBlank(), modifier = Modifier.padding(6.dp).size(48.dp)
                        .clip(RoundedCornerShape(17.dp)).background(if (running) Lab.Red else if (input.isNotBlank()) Lab.Violet else Lab.SurfaceHigh)) {
                        Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, if (running) "Parar execução" else "Enviar tarefa", tint = if (running || input.isNotBlank()) Lab.OnPrimary else Lab.Muted)
                    }
                })
            Text(if (running) "Executando sua tarefa · você pode interromper" else "Confira os resultados antes de usar.", color = Lab.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        }
    }
}

@Composable
fun ExecutionCard(title: String, body: String, status: String, detail: String = "", failed: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val accent = if (failed) Lab.Red else Lab.Cyan
    OutlinedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Lab.Surface, contentColor = Lab.Text), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.AccountTree, null, tint = accent, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(status, color = accent, fontSize = 11.sp)
                }
                IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    if (expanded) "Recolher $title" else "Expandir $title", tint = Lab.Muted) }
            }
            if (expanded) {
                if (detail.isNotBlank() && detail != "{}") SelectionContainer { Text(detail, color = Lab.Muted, fontSize = 11.sp) }
                SelectionContainer { Text(body, color = Lab.Text, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) }
            } else Text(body, color = Lab.Muted, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
