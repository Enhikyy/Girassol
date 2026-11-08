package dev.labforge.app.core.agent

import dev.labforge.app.core.evolution.EvidenceJournal
import dev.labforge.app.core.evolution.InferenceStudy
import dev.labforge.app.core.evolution.SkillTree
import dev.labforge.app.core.settings.AppSettings
import dev.labforge.app.integration.android.DeviceRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class SkillTreeTool(ws: File, private val journal: EvidenceJournal) : AgentTool(ws) {
    override val name = "skill_tree"
    override val description = "Consulta habilidades observadas, falhas e próxima prática. Só resultados executados alimentam o diário."
    override fun parameters() = schema(JSONObject())
    override suspend fun execute(args: JSONObject) = withContext(Dispatchers.IO) {
        ToolResult(true, SkillTree.describe(journal.all()))
    }
}
class InferenceBenchmarkTool(ws: File, private val settings: AppSettings) : AgentTool(ws) {
    override val name = "inference_benchmark"
    override val description = "Mede inferência GGUF local em 1, metade e todas as CPUs, duas vezes. Salva relatório e recomenda threads; não altera ajustes. Inclui custo de carga/prompt."
    override fun parameters() = schema(JSONObject())
    override suspend fun execute(args: JSONObject): ToolResult {
        val output = File(DeviceRuntime.context.filesDir, "labforge/runs/inference-benchmark.json")
        val result = InferenceStudy.run(settings, output)
        return ToolResult(!result.isNull("recommendedThreads"), result.toString(2))
    }
}
