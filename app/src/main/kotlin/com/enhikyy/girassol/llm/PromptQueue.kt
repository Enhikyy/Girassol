package com.enhikyy.girassol.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Prioridade de um pedido de inferência. */
enum class PromptPriority { INTERACTIVE, BACKGROUND }

/** Estado de um pedido na fila. */
enum class PromptState { QUEUED, RUNNING, DONE, FAILED }

/** Um pedido de inferência e o seu resultado observável. */
data class PromptRequest(
    val id: Long = nextId.incrementAndGet(),
    val prompt: String,
    val systemPrompt: String? = null,
    val priority: PromptPriority = PromptPriority.BACKGROUND,
    val maxTokens: Int = 256,
) {
    val state = MutableStateFlow(PromptState.QUEUED)
    val result = CompletableDeferred<String>()

    companion object {
        private val nextId = AtomicLong(1)
    }
}

/**
 * Fila assíncrona de prompts do Sistema 2.
 *
 * A simulação nunca bloqueia num modelo de linguagem. Cada pensamento entra
 * aqui como um [PromptRequest] e é resolvido em segundo plano, com prioridade
 * para o que o utilizador pediu no Chat. Se não houver modelo carregado, a
 * [backend] responde com uma síntese local — a fila funciona igual, e nada
 * rebenta por falta de um `.task` de 1 GB.
 */
class PromptQueue(
    private val backend: InferenceBackend = LocalHeuristicBackend(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Quantos pedidos podem esperar antes de se descartarem os mais antigos. */
    private val maxPending = 64

    private val pending = ArrayDeque<PromptRequest>()
    private val mutex = Mutex()
    private var worker: Job? = null

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _completed = MutableStateFlow(0)
    val completed: StateFlow<Int> = _completed.asStateFlow()

    /** Enfileira um prompt e devolve o pedido, que completa com a resposta. */
    fun enqueue(
        prompt: String,
        systemPrompt: String? = null,
        priority: PromptPriority = PromptPriority.BACKGROUND,
        maxTokens: Int = 256,
    ): PromptRequest {
        val request = PromptRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            priority = priority,
            maxTokens = maxTokens,
        )
        scope.launch {
            mutex.withLock {
                if (pending.size >= maxPending) {
                    // Descarta o pedido de fundo mais antigo; interativos ficam.
                    val victim = pending.indexOfFirst { it.priority == PromptPriority.BACKGROUND }
                    if (victim >= 0) {
                        val dropped = pending.removeAt(victim)
                        dropped.state.value = PromptState.FAILED
                        dropped.result.completeExceptionally(RuntimeException("fila cheia"))
                    }
                }
                if (priority == PromptPriority.INTERACTIVE) pending.addFirst(request)
                else pending.addLast(request)
                _queueSize.value = pending.size
            }
            ensureWorker()
        }
        return request
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            _active.value = true
            while (true) {
                val next = mutex.withLock { pending.removeFirstOrNull().also { _queueSize.value = pending.size } }
                if (next == null) break
                run(next)
            }
            _active.value = false
        }
    }

    private suspend fun run(request: PromptRequest) {
        request.state.value = PromptState.RUNNING
        try {
            val answer = withContext(Dispatchers.Default) {
                backend.generate(request)
            }
            request.state.value = PromptState.DONE
            request.result.complete(answer)
            _completed.value += 1
        } catch (error: Throwable) {
            request.state.value = PromptState.FAILED
            request.result.completeExceptionally(error)
        }
    }

    /** Cancela tudo o que está à espera. */
    fun clear() {
        scope.launch {
            mutex.withLock {
                pending.forEach {
                    it.state.value = PromptState.FAILED
                    it.result.completeExceptionally(RuntimeException("fila limpa"))
                }
                pending.clear()
                _queueSize.value = 0
            }
        }
    }
}

/** Contrato de um motor de inferência. */
interface InferenceBackend {
    /** Verdadeiro quando existe um modelo de linguagem real carregado. */
    val isModelLoaded: Boolean

    suspend fun generate(request: PromptRequest): String
}

/**
 * Backend local determinístico.
 *
 * Não é um placeholder: compõe respostas a partir do estado do mundo e das
 * palavras-chave do prompt. Serve dois propósitos reais — manter o Sistema 2 a
 * funcionar sem modelo descarregado, e fornecer uma linha de base para comparar
 * quando o Gemma estiver disponível.
 */
class LocalHeuristicBackend(
    private val worldSummary: () -> String = { "ecossistema estável" },
) : InferenceBackend {

    override val isModelLoaded: Boolean = false

    override suspend fun generate(request: PromptRequest): String {
        val prompt = request.prompt.lowercase()
        val context = worldSummary()

        val focus = when {
            listOf("matem", "calcul", "número", "formul").any(prompt::contains) -> "matemática"
            listOf("histór", "guerra", "época", "civiliza").any(prompt::contains) -> "história"
            listOf("program", "código", "kotlin", "java", "app").any(prompt::contains) -> "programação"
            listOf("seguran", "cripto", "prote", "firewall").any(prompt::contains) -> "segurança"
            listOf("colhei", "agente", "mundo", "simula").any(prompt::contains) -> "ecossistema"
            else -> "observação geral"
        }

        return buildString {
            append("Raciocinei sobre \"").append(request.prompt.trim().take(160)).append("\" ")
            append("no domínio de ").append(focus).append(".\n\n")
            append("Estado atual: ").append(context).append(".\n")
            append(
                when (focus) {
                    "matemática" ->
                        "O caminho útil aqui é linearizar o problema antes de o resolver: " +
                            "reduza a uma base, verifique os pré-requisitos e só depois generalize."
                    "história" ->
                        "Cruze duas fontes independentes antes de aceitar a narrativa; " +
                            "o que se repete em fontes distintas é o que resiste ao tempo."
                    "programação" ->
                        "Comece pelo contrato: entradas, saídas e erros. " +
                            "Com o contrato estável, a implementação torna-se substituível."
                    "segurança" ->
                        "Assuma que a fronteira vai falhar e desenhe o que acontece depois dela. " +
                            "Confiança explícita é sempre preferível a confiança implícita."
                    "ecossistema" ->
                        "Os agentes estão a cooperar mais do que a competir — " +
                            "sinal de que os drives sociais estão saudáveis."
                    else ->
                        "Refine a pergunta com um domínio concreto e eu aprofundo o raciocínio."
                },
            )
        }
    }
}
