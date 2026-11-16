package dev.labforge.app.core.rsi

import dev.labforge.app.core.agent.AgentLlm
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

data class CollaborationBrief(
    val instruction: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val completedRoles: Int,
)

/**
 * Lightweight multi-role harness for the existing model runtime. Roles collaborate
 * through explicit artifacts; no extra model weights or hidden claims of MoE execution.
 */
object RsiCollaborators {
    suspend fun prepare(task: String, llm: AgentLlm, rounds: Int = 2): CollaborationBrief {
        val roles = listOf(
            "planner" to "Decomponha a tarefa em objetivo, etapas verificáveis, ferramentas necessárias e critérios de conclusão.",
            "critic" to "Audite o plano anterior. Procure premissas sem evidência, riscos, etapas ausentes e critérios de validação fracos. Não invente fatos.",
            "synthesizer" to "Produza instruções operacionais curtas para executar a tarefa, incorporando apenas críticas válidas. Preserve as restrições do usuário.",
        ).take(rounds.coerceIn(1, 3))
        var artifact = "Solicitação: $task"
        var promptTokens = 0
        var completionTokens = 0
        var completed = 0
        for ((role, directive) in roles) {
            try {
                val reply = llm.chat(
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content",
                            "Você é o papel $role em uma equipe interna de agentes. $directive " +
                                "Retorne somente um artefato conciso em português. Não execute ferramentas nem alegue execução."))
                        put(JSONObject().put("role", "user").put("content", artifact))
                    },
                    null,
                )
                if (reply.content.isNotBlank()) artifact = reply.content.take(6000)
                promptTokens += reply.promptTokens.coerceAtLeast(0)
                completionTokens += reply.completionTokens.coerceAtLeast(0)
                completed++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                break
            }
        }
        return CollaborationBrief(
            instruction = if (completed == 0) "" else
                "Plano colaborativo preliminar (não substitui verificação):\n$artifact",
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            completedRoles = completed,
        )
    }
}
