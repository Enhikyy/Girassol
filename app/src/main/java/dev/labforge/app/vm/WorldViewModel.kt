package dev.labforge.app.vm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.labforge.app.LabForgeApp
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.settings.SettingsRepository
import dev.labforge.app.core.world.ActionType
import dev.labforge.app.core.world.CompanyStrategy
import dev.labforge.app.core.world.HappeningType
import dev.labforge.app.core.world.WorldState
import dev.labforge.app.core.world.agent
import dev.labforge.app.core.world.company
import dev.labforge.app.core.world.day
import dev.labforge.app.core.world.displayName
import dev.labforge.app.core.world.minuteOfDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class WorldSelectionKind { AGENT, COMPANY, BUILDING }

data class WorldDialogueMessage(
    val text: String,
    val fromUser: Boolean,
    val pending: Boolean = false,
)

class WorldViewModel(app: Application) : AndroidViewModel(app) {
    private val runtime = (app as LabForgeApp).worldRuntime
    private val settingsRepository = SettingsRepository(app)
    val world = runtime.state
    val status = runtime.status

    var selectedId by mutableStateOf<String?>(null)
        private set
    var selectionKind by mutableStateOf<WorldSelectionKind?>(null)
        private set
    var focusRequestId by mutableStateOf(0L)
        private set
    var focusTargetId by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var dialogueAgentId by mutableStateOf<String?>(null)
        private set
    var dialogue by mutableStateOf<List<WorldDialogueMessage>>(emptyList())
        private set

    private var dialogueJob: Job? = null

    fun selectAgent(id: String?) {
        selectedId = id
        selectionKind = if (id == null) null else WorldSelectionKind.AGENT
        runtime.setSelectedAgent(id)
    }

    fun selectCompany(id: String?) {
        selectedId = id
        selectionKind = if (id == null) null else WorldSelectionKind.COMPANY
        runtime.setSelectedAgent(null)
    }

    fun selectBuilding(id: String?) {
        selectedId = id
        selectionKind = if (id == null) null else WorldSelectionKind.BUILDING
        runtime.setSelectedAgent(null)
    }

    fun selectFromScene(id: String) {
        when {
            id.startsWith("agent_") -> selectAgent(id)
            id.startsWith("company_") -> selectCompany(id)
            else -> selectBuilding(id)
        }
    }

    fun clearSelection() {
        selectedId = null
        selectionKind = null
        runtime.setSelectedAgent(null)
    }

    fun focusOn(id: String) {
        focusTargetId = id
        focusRequestId++
    }

    fun setVisibleAgents(ids: Set<String>) = runtime.setVisibleAgents(ids)

    fun setPlayback(paused: Boolean, speed: Int) {
        viewModelScope.launch { runtime.setPlayback(paused, speed) }
    }

    fun setParameters(demand: Float, research: Float, catchUp: Boolean) {
        viewModelScope.launch { runtime.setParameters(demand, research, catchUp) }
    }

    fun applyHappening(type: HappeningType, description: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                runtime.applyHappening(type, description)
                    .onSuccess { notice = "Acontecimento aplicado à simulação." }
                    .onFailure { notice = it.message ?: "Não foi possível aplicar o acontecimento." }
            } finally {
                busy = false
            }
        }
    }

    fun foundCompany(name: String, strategy: CompanyStrategy) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                runtime.foundCompany(name, strategy)
                    .onSuccess { world ->
                        val company = world.companies.last()
                        selectCompany(company.id)
                        notice = "${company.name} foi fundada com capital real da reserva."
                    }
                    .onFailure { notice = it.message ?: "Não foi possível fundar a empresa." }
            } finally {
                busy = false
            }
        }
    }

    fun openDialogue(agentId: String) {
        if (dialogueAgentId != agentId) {
            dialogueJob?.cancel()
            dialogueAgentId = agentId
            dialogue = emptyList()
        }
    }

    fun sendDialogue(text: String) {
        val agentId = dialogueAgentId ?: return
        val world = runtime.state.value
        val agent = world.agent(agentId) ?: return
        val message = text.trim().take(2_000)
        if (message.isEmpty() || busy) return
        busy = true
        dialogue = dialogue + WorldDialogueMessage(message, fromUser = true)
        dialogueJob = viewModelScope.launch {
            try {
                runtime.addDialogueMemory(agentId, "Usuário", message, isUser = true)
                val settings = settingsRepository.settings.first()
                if (settings.model.isBlank()) error("Configure um modelo em Ajustes antes de conversar.")
                if (settings.apiKey.isBlank() && settings.endpoint.contains("api.openai.com")) {
                    error("Configure a chave do provedor ou um endpoint local em Ajustes.")
                }
                val prompt = JSONObject().apply {
                    put("role", "system")
                    put("content", agentPrompt(world, agentId))
                }
                val user = JSONObject().apply {
                    put("role", "user")
                    put("content", message)
                }
                val reply = LlmClient(settings.endpoint, settings.apiKey, settings.model, settings.temperature)
                    .chat(JSONArray().put(prompt).put(user), tools = null)
                val content = reply.content.trim()
                if (content.isBlank()) error("O modelo respondeu sem conteúdo.")
                runtime.addDialogueMemory(agentId, agent.name, content, isUser = false)
                dialogue = dialogue + WorldDialogueMessage(content, fromUser = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                dialogue = dialogue + WorldDialogueMessage(
                    "Falha na inferência: ${failure.message ?: "erro desconhecido"}. Nenhuma resposta fictícia foi criada.",
                    fromUser = false,
                )
            } finally {
                busy = false
            }
        }
    }

    fun stopDialogue() {
        dialogueJob?.cancel()
        dialogue = dialogue.map { it.copy(pending = false) }
        // O finally do job liberará busy; outra operação pode ser a proprietária desse estado.
    }

    fun clearNotice() { notice = null }

    fun acknowledgeRecoveryWarning() = runtime.acknowledgeRecoveryWarning()

    fun save() = runtime.requestSave()

    override fun onCleared() {
        runtime.setSelectedAgent(null)
        runtime.requestSave()
        super.onCleared()
    }

    private fun agentPrompt(world: WorldState, agentId: String): String {
        val agent = world.agent(agentId) ?: error("Habitante não encontrado.")
        val company = agent.employerId?.let { world.company(it) }
        val relations = agent.relationships.entries.sortedByDescending { it.value }.take(5).joinToString("\n") { (id, affinity) ->
            "- ${world.agent(id)?.name ?: id}: afinidade ${"%.2f".format(affinity)}"
        }.ifBlank { "- Ainda não há vínculos registrados." }
        val memories = agent.memories.take(8).joinToString("\n") { "- [dia ${it.simMinute / 1_440}] ${it.text}" }
            .ifBlank { "- Sem memórias anteriores." }
        return """
Você é ${agent.name}, um habitante autônomo do mundo persistente Aurora, dentro do Girassol.
Responda em português, em primeira pessoa, com até 180 palavras. Não apresente respostas como ferramentas reais fora da conversa.
Use somente o estado abaixo como fatos. Você pode interpretar sentimentos, mas não invente eventos, capacidades ou histórico.

Profissão: ${agent.profession.displayName()}
Empresa: ${company?.name ?: "sem vínculo"}
Atividade atual: ${agent.action?.type?.displayName() ?: "sem plano ativo"}
Emoção: ${agent.emotion}
Necessidades (0..100): energia ${agent.needs.energy}, nutrição ${agent.needs.nutrition}, social ${agent.needs.social}, motivação ${agent.needs.motivation}, satisfação ${agent.needs.satisfaction}, estresse ${agent.needs.stress}
Personalidade (0..1): ${agent.personality}
Habilidades: ${agent.skills}
Objetivos: ${agent.goals.joinToString { "${it.description} (${(it.progress * 100).toInt()}%)" }}
Relacionamentos:
$relations
Memórias recentes:
$memories
Dia do mundo: ${world.day}; minuto do dia: ${world.minuteOfDay}
""".trimIndent()
    }
}
