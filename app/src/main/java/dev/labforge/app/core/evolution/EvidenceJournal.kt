package dev.labforge.app.core.evolution

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Observed tool results, not self-assessed proficiency. Arguments/output stay out of the journal. */
class EvidenceJournal(dir: File) {
    private val file = File(dir, "execution-evidence.json")
    private val lock = locks.getOrPut(file.canonicalPath) { Any() }
    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
        private fun canonical(value: Any?): String = when (value) {
            is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            else -> JSONArray().put(value).toString().let { it.substring(1, it.length - 1) }
        }
    }
    data class Event(val tool: String, val action: String, val signature: String, val ok: Boolean,
                     val millis: Long, val at: Long, val recovered: Boolean)

    fun all(): List<Event> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        val json = JSONObject(file.readText())
        require(json.getInt("version") == 1) { "Versão do diário não suportada" }
        val rows = json.getJSONArray("events")
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            Event(it.getString("tool"), it.getString("action"), it.getString("signature"), it.getBoolean("ok"),
                it.getLong("millis"), it.getLong("at"), it.getBoolean("recovered"))
        } }
    }

    fun record(tool: String, args: JSONObject, ok: Boolean, millis: Long): Event = synchronized(lock) {
        val signature = MessageDigest.getInstance("SHA-256").digest((tool + canonical(args)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val previous = all()
        val event = Event(tool, args.optString("action"), signature, ok, millis.coerceAtLeast(0), System.currentTimeMillis(),
            ok && previous.lastOrNull { it.signature == signature }?.ok == false)
        val rows = JSONArray()
        (previous + event).takeLast(512).forEach { e -> rows.put(JSONObject()
            .put("tool", e.tool).put("action", e.action).put("signature", e.signature).put("ok", e.ok)
            .put("millis", e.millis).put("at", e.at).put("recovered", e.recovered)) }
        file.parentFile?.mkdirs()
        val tmp = File.createTempFile("evidence-", ".part", file.parentFile)
        try {
            tmp.writeText(JSONObject().put("version", 1).put("events", rows).toString())
            check(tmp.renameTo(file)) { "Não foi possível persistir evidências" }
        } finally { tmp.delete() }
        event
    }
}

data class Skill(val id: String, val title: String, val prerequisites: List<String>, val tools: Set<String>, val actions: Set<String> = emptySet())
data class SkillProgress(val skill: Skill, val successes: Int, val failures: Int, val recoveries: Int, val available: Boolean)

object SkillTree {
    val skills = listOf(
        Skill("read", "Explorar código", emptyList(), setOf("read_file", "search_code", "list_dir")),
        Skill("edit", "Editar projetos", listOf("read"), setOf("write_file", "apply_patch")),
        Skill("verify", "Compilar e validar", listOf("edit"), setOf("android_build"), setOf("build", "test", "lint", "pipeline")),
        Skill("ui", "Testar interfaces", listOf("verify"), setOf("android_ui_test")),
        Skill("research", "Pesquisar fontes", emptyList(), setOf("research_web")),
        Skill("inference", "Medir inferência local", emptyList(), setOf("inference_benchmark")),
    )
    fun progress(events: List<EvidenceJournal.Event>): List<SkillProgress> {
        fun matches(s: Skill, e: EvidenceJournal.Event) = e.tool in s.tools && (s.actions.isEmpty() || e.action in s.actions)
        val passed = skills.filter { s -> events.any { it.ok && matches(s, it) } }.map { it.id }.toSet()
        fun completed(id: String): Boolean = id in passed && skills.first { it.id == id }.prerequisites.all { completed(it) }
        return skills.map { s ->
            val evidence = events.filter { matches(s, it) }
            SkillProgress(s, evidence.count { it.ok }, evidence.count { !it.ok }, evidence.count { it.recovered }, s.prerequisites.all { completed(it) })
        }
    }
    fun next(events: List<EvidenceJournal.Event>): SkillProgress? = progress(events)
        .filter { it.available }.sortedWith(compareBy<SkillProgress> { it.successes > 0 }.thenByDescending { it.failures }.thenBy { it.successes }).firstOrNull()

    fun describe(events: List<EvidenceJournal.Event>): String = progress(events).joinToString("\n") {
        "${it.skill.title}: ${it.successes} sucessos, ${it.failures} falhas, ${it.recoveries} recuperações; pré-requisitos ${if (it.available) "observados" else "pendentes"}"
    } + "\nPróxima prática sugerida: ${next(events)?.skill?.title.orEmpty()}. Resultados de ferramentas não certificam domínio."
}
