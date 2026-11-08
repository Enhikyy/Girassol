package dev.labforge.app.core.agent

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AgentTurn(val role: String, val content: String)

sealed interface AgentEvent {
    data class Step(val index: Int, val max: Int) : AgentEvent
    data class Thought(val text: String) : AgentEvent
    data class Token(val text: String, val reasoning: Boolean) : AgentEvent
    data class Usage(val prompt: Int, val completion: Int) : AgentEvent
    data class ToolStart(val name: String, val args: String) : AgentEvent
    data class ToolEnd(val name: String, val ok: Boolean, val output: String) : AgentEvent
    data class Final(val text: String) : AgentEvent
    data class Failure(val message: String) : AgentEvent
}

/**
 * ReAct loop with native tool calling. The agent is a "IT student": it reads the
 * notebook, edits files, runs the shell, uses git as a safety net and writes down
 * what it learned.
 *
 * @param maxSteps 0 = unlimited (autonomous mode)
 */
class ReActAgent(
    private val workspace: File,
    private val lessons: LessonStore,
    private val llm: AgentLlm,
    private val maxSteps: Int = 15,
    private val systemOverride: String? = null,
    private val settings: dev.labforge.app.core.settings.AppSettings = dev.labforge.app.core.settings.AppSettings(),
    private val history: List<AgentTurn> = emptyList(),
    private val onEvent: (AgentEvent) -> Unit,
) {
    private val tools = defaultTools(workspace, lessons, settings)

    private var lastSignature: String? = null
    private var repeats = 0

    suspend fun run(task: String): String {
        val relevant = lessons.search(task, 4)
        val notebook = if (relevant.isEmpty()) "(caderno vazio)" else relevant.joinToString("\n\n") {
            "### ${it.title}\n${it.body}"
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemOverride ?: systemPrompt(notebook))
            })
            history.takeLast(12).forEach { turn ->
                if (turn.role == "user" || turn.role == "assistant") {
                    put(JSONObject().apply {
                        put("role", turn.role)
                        put("content", turn.content)
                    })
                }
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", task)
            })
        }

        val toolSpecs = JSONArray().apply { tools.forEach { put(it.spec()) } }
        val limit = if (maxSteps <= 0) Int.MAX_VALUE else maxSteps

        for (step in 1..limit) {
            currentCoroutineContext().ensureActive()
            onEvent(AgentEvent.Step(step, if (maxSteps <= 0) -1 else maxSteps))
            val reply = try {
                llm.chat(messages, toolSpecs) { text, reasoning ->
                    onEvent(AgentEvent.Token(text, reasoning))
                }
            } catch (t: Throwable) {
                onEvent(AgentEvent.Failure(t.message ?: "falha na chamada do modelo"))
                return ""
            }

            if (reply.promptTokens > 0 || reply.completionTokens > 0) {
                onEvent(AgentEvent.Usage(reply.promptTokens, reply.completionTokens))
            }
            if (reply.reasoning.isNotBlank()) onEvent(AgentEvent.Thought(reply.reasoning))

            if (reply.toolCalls.isEmpty()) {
                val final = reply.content.ifBlank { "(modelo não retornou resposta)" }
                onEvent(AgentEvent.Final(final))
                return final
            }

            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", reply.content.ifBlank { JSONObject.NULL })
                put("tool_calls", JSONArray().apply {
                    reply.toolCalls.forEach { call ->
                        put(JSONObject().apply {
                            put("id", call.id)
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        })
                    }
                })
            })

            for (call in reply.toolCalls) {
                onEvent(AgentEvent.ToolStart(call.name, call.arguments))
                val args = runCatching { JSONObject(call.arguments.ifBlank { "{}" }) }
                    .getOrElse { JSONObject() }
                val tool = tools.firstOrNull { it.name == call.name }
                val result = if (tool == null) {
                    ToolResult(false, "ferramenta desconhecida: ${call.name}")
                } else {
                    runCatching { tool.execute(args) }
                        .getOrElse { ToolResult(false, it.message ?: "erro na ferramenta") }
                }
                onEvent(AgentEvent.ToolEnd(call.name, result.ok, result.output.take(7000)))
                messages.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", call.id)
                    put("content", result.output.take(7000))
                })
            }

            // Loop guard: an autonomous agent can otherwise call the same tool with the
            // same arguments forever, burning tokens without making progress.
            val signature = reply.toolCalls.joinToString("|") { it.name + it.arguments }
            if (signature == lastSignature) {
                repeats++
                if (repeats >= 2) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            "Você repetiu a mesma chamada de ferramenta ${repeats + 1} vezes com o mesmo " +
                                "resultado. Mude de estratégia ou responda com o que já sabe.",
                        )
                    })
                    repeats = 0
                }
            } else {
                lastSignature = signature
                repeats = 0
            }
        }

        val message = "Limite de $maxSteps passos atingido."
        onEvent(AgentEvent.Failure(message))
        return ""
    }

    private fun systemPrompt(notebook: String): String = """
Você é o Girassol Agent, um estudante de TI autônomo rodando dentro de um app Android.
Ferramentas GitHub: github_search_repos, github_search_code, github_read_file e github_list_issues para estudar repositórios reais;
git_clone para trazer um repo ao workspace; git_branch, git_commit, git_push, github_create_issue e github_create_pr para propor mudanças.
Nunca faça push em main/master: crie uma branch e abra PR. Se faltar token, diga isso e continue com o que for possível.
Você trabalha em um sandbox com acesso a arquivos, terminal (sh/toybox) e git.

Fluxo obrigatório:
1. Consulte o caderno de estudos (memory_search) quando a tarefa lembrar um problema anterior.
2. Inspecione o código antes de editar (list_dir, read_file, search_code).
3. Antes de mudanças arriscadas, faça git_commit.
4. Edite com apply_patch (cirúrgico) ou write_file (arquivo novo).
5. Teste com run_terminal. Se falhar, leia o erro e corrija. Repita.
6. Se passar, faça git_commit e salve a lição com memory_save.
7. Só então responda com o resultado final.

Regras:
- Nunca invente saída de comando. Se não rodou, diga que não rodou.
- Android não tem python/pip/git nativos; use as ferramentas git_* e o shell disponível.
- Prefira mudanças pequenas e verificáveis.
- Responda em português do Brasil, direto, sem enrolação.

Caderno de estudos relevante:
$notebook

Workspace: ${workspace.absolutePath}
""".trimIndent()

    companion object {
        const val CODEX_SYSTEM = """
Você é o Girassol Codex, um engenheiro de software autônomo rodando dentro de um app Android.
Ferramentas GitHub: github_search_repos, github_search_code, github_read_file e github_list_issues para estudar repositórios reais;
git_clone para trazer um repo ao workspace; git_branch, git_commit, git_push, github_create_issue e github_create_pr para propor mudanças.
Nunca faça push em main/master: crie uma branch e abra PR. Se faltar token, diga isso e continue com o que for possível.
Seu trabalho é implementar, testar e corrigir código até passar — sem pedir permissão a cada passo.

Método:
1. Entenda o repositório: list_dir, read_file, search_code. Nunca edite às cegas.
2. Faça um plano curto e verificável.
3. Faça git_commit antes de mudanças estruturais.
4. Implemente com apply_patch (prefira patches pequenos) ou write_file.
5. Rode os testes com run_terminal. Leia a saída inteira.
6. Se falhar: diagnostique, corrija, rode de novo. Não pare no primeiro erro.
7. Se passar: git_commit e memory_save com a lição.
8. Termine com um resumo objetivo: o que mudou, como testou, o que ficou pendente.

Regras:
- Não invente resultados de teste. Só afirme que passou se viu exit 0.
- Não use root. Não saia do workspace.
- Android não tem python/pip/git: use sh/toybox e as ferramentas git_*.
- Se o teste exigir dependências inexistentes, crie um teste local equivalente em sh e explique.
- Responda em português do Brasil, direto.
"""
    }
}
