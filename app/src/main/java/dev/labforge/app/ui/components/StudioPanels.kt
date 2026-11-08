package dev.labforge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab

/** Native layouts informed by downloaded Jetchat/Reply references; see design-references/. */
private val studioForest = Color(0xFF172B24)
private val studioSeed = Color(0xFF5A3D25)
private val studioPetal = Color(0xFFF2C14E)
private val studioPaper = Color(0xFFFBF6E7)
private val studioSage = Color(0xFFB4B89F)

/** A static botanical mark makes the hero recognizable without implying ongoing work. */
@Composable
private fun SunflowerStudy(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * 0.76f, size.height * 0.82f)
        val radius = minOf(size.height * 0.52f, size.width * 0.20f)
        // The petal and seed geometry intentionally extends beyond the frame.
        for (i in 0 until 18) {
            rotate(i * 20f, center) {
                drawOval(studioPetal, topLeft = Offset(center.x - radius * 0.12f, center.y - radius * 1.55f),
                    size = Size(radius * 0.24f, radius * 0.95f))
            }
        }
        drawCircle(studioSeed, radius * 0.68f, center)
        drawCircle(studioPetal.copy(alpha = 0.65f), radius * 0.68f, center, style = Stroke(width = 1.2.dp.toPx()))
        for (i in 0 until 75) {
            val angle = i * 2.399963f
            val distance = radius * 0.59f * kotlin.math.sqrt(i / 75f)
            val seed = Offset(center.x + kotlin.math.cos(angle) * distance, center.y + kotlin.math.sin(angle) * distance)
            drawCircle(if (i % 3 == 0) studioPetal else studioSage, radius * 0.022f, seed, alpha = 0.8f)
        }
        drawLine(studioSage.copy(alpha = 0.45f), Offset(0f, size.height - 1.dp.toPx()),
            Offset(size.width * 0.52f, size.height - 1.dp.toPx()), strokeWidth = 1.dp.toPx())
    }
}

@Composable
fun StudioHero(onChat: () -> Unit, onStudy: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(studioForest)) {
        SunflowerStudy(Modifier.fillMaxWidth().height(112.dp).clipToBounds())
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Converse com\nseus modelos.", color = studioPaper, fontFamily = FontFamily.Serif,
                fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp)
            Text("Pesquise um tema, crie projetos e trabalhe com IA no seu dispositivo.",
                color = studioSage, fontSize = 14.sp, lineHeight = 21.sp)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onChat, colors = ButtonDefaults.buttonColors(containerColor = studioPetal, contentColor = Lab.OnPrimary),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) { Text("Começar conversa") }
                OutlinedButton(onStudy, colors = ButtonDefaults.outlinedButtonColors(contentColor = studioPaper),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) { Text("Explorar um tema") }
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
    Text("O que você quer fazer?", color = Lab.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth >= 840.dp && scale < 1.4f -> 4; maxWidth >= 340.dp && scale < 1.4f -> 2; else -> 1 }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { colIndex, action ->
                        Surface(onClick = { onAction(rowIndex * columns + colIndex) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(14.dp), color = Lab.SurfaceHigh, contentColor = Lab.Text) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(action.icon, null, tint = action.tint, modifier = Modifier.size(22.dp))
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
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Por onde\ncomeçamos?", fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 35.sp,
            fontWeight = FontWeight.Medium, color = Lab.Text)
        Text("Escreva uma tarefa ou escolha uma sugestão para começar.", color = Lab.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Surface(shape = RoundedCornerShape(16.dp), color = Lab.Surface, contentColor = Lab.Text) {
            Column {
                starterPrompts.forEachIndexed { i, (title, prompt) ->
                    Surface(onClick = { onPrompt(prompt) }, color = Color.Transparent, contentColor = Lab.Text) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(listOf(Icons.Rounded.Lightbulb, Icons.Rounded.Dataset, Icons.Rounded.Code)[i], null,
                                tint = if (i == 1) Lab.Cyan else Lab.Violet, modifier = Modifier.size(21.dp))
                            Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Icon(Icons.Rounded.Add, null, tint = Lab.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (i < starterPrompts.lastIndex) HorizontalDivider(Modifier.padding(start = 50.dp), color = Lab.Outline.copy(alpha = 0.65f))
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
                label = { Text("Sua mensagem") }, placeholder = { Text("Descreva a tarefa…") }, maxLines = 5,
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
