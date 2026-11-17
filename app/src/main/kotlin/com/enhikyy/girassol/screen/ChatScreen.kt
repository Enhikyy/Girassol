package com.enhikyy.girassol.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.MyApplication
import com.enhikyy.girassol.ai.NativeLlmEngine
import com.enhikyy.girassol.ai.ModelManager
import com.enhikyy.girassol.ai.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Uma mensagem da conversa. */
data class ChatMessage(
    val id: Long,
    val content: String,
    val isUser: Boolean,
    val author: String? = null,
)

/**
 * Tela CHAT com modelo LLM local (100% nativo via JNI).
 *
 * Se houver modelo carregado, envia a mensagem e mostra a resposta token-a-token.
 * Se não houver modelo, mostra "Carregue um modelo em Configurações".
 */
@Composable
fun ChatScreen() {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentResponse by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Acesso ao motor LLM. Observar o status faz a tela recompor quando o modelo carrega.
    val modelStatus by ModelManager.status.collectAsState()
    val app = MyApplication.getInstance()
    val engine = app.llmEngine
    val hasModel = engine is NativeLlmEngine && engine.isLoaded && modelStatus !is ModelStatus.Loading

    // Mantém a vista no fim à medida que as mensagens chegam.
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty() || isGenerating) {
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || isGenerating) return

        // Mensagem do usuário
        messages = messages + ChatMessage(
            id = System.currentTimeMillis(),
            content = text,
            isUser = true,
        )
        input = ""
        isGenerating = true
        currentResponse = ""

        if (!hasModel) {
            // Sem modelo: mensagem informativa imediata
            val msg = "Carregue um modelo em Configurações"
            messages = messages + ChatMessage(
                id = System.currentTimeMillis() + 1,
                content = msg,
                isUser = false,
                author = "GIRASSOL",
            )
            isGenerating = false
            return
        }

        // Com modelo: gera streaming (generate() já roda em Dispatchers.Default)
        val llmEngine = engine
        val answer = StringBuilder()
        scope.launch(AppLogger.globalCoroutineExceptionHandler) {
            try {
                val ok = llmEngine.generate(text) { token ->
                    // Chega na thread de background: acumula e publica o texto parcial na principal.
                    answer.append(token)
                    val partial = answer.toString()
                    scope.launch(Dispatchers.Main) { currentResponse = partial }
                }

                if (ok) {
                    // Geração concluída com sucesso
                    messages = messages + ChatMessage(
                        id = System.currentTimeMillis() + 1,
                        content = answer.toString(),
                        isUser = false,
                        author = "GIRASSOL",
                    )
                }
            } catch (e: Exception) {
                AppLogger.log("ERROR", "CHAT", "Erro na geração: ${e.message}", e)
                messages = messages + ChatMessage(
                    id = System.currentTimeMillis() + 1,
                    content = "Erro ao gerar resposta: ${e.message}",
                    isUser = false,
                    author = "GIRASSOL",
                )
            } finally {
                isGenerating = false
                currentResponse = ""
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "CHAT",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (hasModel) "Modelo ativo — geração token-a-token"
                    else "Nenhum modelo carregado",
                    fontSize = 10.sp,
                    color = if (hasModel) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    item { EmptyChatHint(hasModel) }
                }
                items(messages) { message ->
                    MessageRow(message)
                }
                // Mostra resposta sendo gerada em tempo real
                if (isGenerating && currentResponse.isNotBlank()) {
                    item {
                        MessageRow(ChatMessage(
                            id = -1,
                            content = currentResponse + "▊",
                            isUser = false,
                            author = "GIRASSOL",
                        ))
                    }
                } else if (isGenerating && !hasModel) {
                    // Estado de "carregando" sem modelo
                    item {
                        GeneratingPlaceholder()
                    }
                }
            }

            ChatInput(
                text = input,
                enabled = !isGenerating,
                onTextChange = { input = it },
                onSend = { send() },
            )
        }
    }
}

/** Placeholder enquanto gera (sem modelo ou aguardando primeiro token). */
@Composable
private fun GeneratingPlaceholder() {
    val transition = rememberInfiniteTransition(label = "genPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = pulse),
            )
            Text(
                text = "Gerando resposta...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

/** Uma mensagem: sem balões coloridos, apenas autor + texto e uma linha-guia. */
@Composable
private fun MessageRow(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = if (message.isUser) "VOCÊ" else (message.author ?: "GIRASSOL"),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (message.isUser) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(if (message.isUser) 14.dp else 40.dp)
                    .background(
                        if (message.isUser) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
            )
            Text(
                text = message.content,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (message.isUser) 0.9f else 0.95f,
                ),
            )
        }
    }
}

@Composable
private fun EmptyChatHint(hasModel: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (hasModel) "Nenhuma mensagem ainda." else "Carregue um modelo em Configurações",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = if (hasModel)
                "Envie uma pergunta — a resposta chegará token a token."
            else
                "Abra Configurações → Modelos → escolha um arquivo .gguf",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/** Caixa de entrada com bordas 24dp; o botão de enviar só aparece com texto. */
@Composable
private fun ChatInput(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = {
            Text(
                "Mensagem...",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        },
        shape = RoundedCornerShape(24.dp),
        maxLines = 4,
        trailingIcon = {
            AnimatedVisibility(
                visible = text.isNotBlank() && enabled,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120)),
            ) {
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.Send,
                        contentDescription = "Enviar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}