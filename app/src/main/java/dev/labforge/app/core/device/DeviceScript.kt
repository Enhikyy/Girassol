package dev.labforge.app.core.device

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Declarative UI script — the on-device equivalent of Maestro, built on the same
 * accessibility layer the agent uses. It is deterministic, bounded and produces a report
 * that can be attached to a Codex run as evidence.
 *
 * Shape:
 * ```json
 * {"name":"abrir wi-fi","steps":[
 *   {"action":"open","package":"com.android.settings"},
 *   {"action":"wait","text":"Rede e internet","timeout_ms":5000},
 *   {"action":"tap","text":"Rede e internet"},
 *   {"action":"assert","text":"Wi-Fi"}
 * ]}
 * ```
 */
data class ScriptStep(val index: Int, val action: String, val args: JSONObject)

data class DeviceScript(val name: String, val steps: List<ScriptStep>)

data class StepResult(val index: Int, val action: String, val ok: Boolean, val detail: String)

data class ScriptReport(
    val name: String,
    val ok: Boolean,
    val steps: List<StepResult>,
    val elapsedMs: Long,
) {
    fun render(): String = buildString {
        append(if (ok) "PASSOU" else "FALHOU").append(" · ").append(name)
        append(" · ").append(steps.count { it.ok }).append('/').append(steps.size)
        append(" passos · ").append(elapsedMs).append(" ms\n")
        steps.forEach { step ->
            append(if (step.ok) "✓ " else "✗ ").append(step.index + 1).append(". ")
            append(step.action).append(" — ").append(step.detail).append('\n')
        }
    }
}

object DeviceScriptParser {

    const val MAX_STEPS = 25

    fun parse(text: String): DeviceScript {
        val root = JSONObject(text)
        val rawSteps = root.optJSONArray("steps") ?: JSONArray()
        require(rawSteps.length() in 1..MAX_STEPS) {
            "o roteiro precisa de 1 a $MAX_STEPS passos"
        }
        val steps = (0 until rawSteps.length()).map { index ->
            val item = rawSteps.optJSONObject(index)
                ?: throw IllegalArgumentException("passo ${index + 1} não é objeto")
            val action = item.optString("action").lowercase().trim()
            require(action in ALLOWED) { "ação desconhecida no passo ${index + 1}: $action" }
            ScriptStep(index, action, item)
        }
        return DeviceScript(root.optString("name", "roteiro").take(80), steps)
    }

    private val ALLOWED = setOf("open", "tap", "type", "swipe", "key", "wait", "assert", "sleep")
}

class DeviceScriptRunner(private val controller: DeviceController) {

    suspend fun run(
        script: DeviceScript,
        onStep: (StepResult) -> Unit = {},
    ): ScriptReport {
        val started = System.currentTimeMillis()
        val results = ArrayList<StepResult>(script.steps.size)

        for (step in script.steps) {
            val result = runCatching { execute(step) }
                .getOrElse { StepResult(step.index, step.action, false, "exceção: ${it.message}") }
            results.add(result)
            onStep(result)
            if (!result.ok) break
        }

        return ScriptReport(
            name = script.name,
            ok = results.isNotEmpty() && results.all { it.ok } && results.size == script.steps.size,
            steps = results,
            elapsedMs = System.currentTimeMillis() - started,
        )
    }

    private suspend fun execute(step: ScriptStep): StepResult {
        val args = step.args
        val selector = selector(args)
        return when (step.action) {
            "open" -> {
                val pkg = args.optString("package").trim()
                val ok = pkg.isNotBlank() && controller.openApp(pkg)
                StepResult(step.index, step.action, ok, if (ok) "app aberto: $pkg" else "pacote inválido: $pkg")
            }

            "tap" -> {
                val snapshot = controller.snapshot(400)
                    ?: return StepResult(step.index, step.action, false, "sem leitura da tela")
                val node = snapshot.find(selector)
                    ?: return StepResult(step.index, step.action, false, "não encontrado: ${selector.describe()}")
                val ok = controller.tap(node.bounds.centerX, node.bounds.centerY)
                delay(350)
                StepResult(
                    step.index, step.action, ok,
                    if (ok) "toque em \"${node.text.ifBlank { node.desc }}\"" else "gesto recusado",
                )
            }

            "type" -> {
                val value = args.optString("value").ifBlank { args.optString("text") }
                val ok = if (selector.isEmpty) controller.setTextFocused(value) else controller.setText(selector, value)
                StepResult(step.index, step.action, ok, if (ok) "texto aplicado" else "campo não editável")
            }

            "swipe" -> {
                val snapshot = controller.snapshot(120)
                    ?: return StepResult(step.index, step.action, false, "sem leitura da tela")
                val bounds = snapshot.screenBounds()
                val direction = args.optString("direction", "up")
                val (x1, y1, x2, y2) = swipePoints(bounds, direction)
                val ok = controller.swipe(x1, y1, x2, y2, args.optLong("duration_ms", 280L))
                StepResult(step.index, step.action, ok, if (ok) "swipe $direction" else "gesto recusado")
            }

            "key" -> {
                val key = args.optString("key", "back")
                val action = when (key) {
                    "home" -> GlobalAction.HOME
                    "recents" -> GlobalAction.RECENTS
                    "notifications" -> GlobalAction.NOTIFICATIONS
                    else -> GlobalAction.BACK
                }
                val ok = controller.global(action)
                StepResult(step.index, step.action, ok, if (ok) "tecla $key" else "ação recusada")
            }

            "wait" -> {
                val timeout = args.optInt("timeout_ms", 6000).coerceIn(300, 30000)
                val deadline = System.currentTimeMillis() + timeout
                var last: UiSnapshot? = null
                var found: UiNode? = null
                while (System.currentTimeMillis() < deadline && found == null) {
                    last = controller.snapshot(400)
                    found = last?.find(selector)
                    if (found == null) delay(300)
                }
                StepResult(
                    step.index, step.action, found != null,
                    if (found != null) "apareceu \"${found.text.ifBlank { found.desc }}\""
                    else "timeout (${timeout}ms); tela: ${last?.signature() ?: "?"}",
                )
            }

            "assert" -> {
                val snapshot = controller.snapshot(400)
                    ?: return StepResult(step.index, step.action, false, "sem leitura da tela")
                val present = snapshot.find(selector) != null
                val expectAbsent = args.optBoolean("absent", false)
                val ok = if (expectAbsent) !present else present
                StepResult(
                    step.index, step.action, ok,
                    if (ok) (if (expectAbsent) "ausente como esperado" else "presente como esperado")
                    else if (expectAbsent) "ainda presente: ${selector.describe()}"
                    else "não encontrado: ${selector.describe()}",
                )
            }

            else -> {
                delay(args.optLong("ms", 500L).coerceIn(50L, 10_000L))
                StepResult(step.index, step.action, true, "espera concluída")
            }
        }
    }

    private fun selector(args: JSONObject) = UiSelector(
        id = args.optString("id").takeIf { it.isNotBlank() },
        text = args.optString("text").takeIf { it.isNotBlank() },
        desc = args.optString("desc").takeIf { it.isNotBlank() },
        role = args.optString("role").takeIf { it.isNotBlank() },
        exact = args.optBoolean("exact", false),
        index = args.optInt("index", 0).coerceAtLeast(0),
    )

    private fun swipePoints(bounds: Bounds, direction: String): IntArray = when (direction) {
        "down" -> intArrayOf(bounds.centerX, (bounds.height * 0.28).toInt(), bounds.centerX, (bounds.height * 0.78).toInt())
        "left" -> intArrayOf((bounds.width * 0.82).toInt(), bounds.centerY, (bounds.width * 0.22).toInt(), bounds.centerY)
        "right" -> intArrayOf((bounds.width * 0.22).toInt(), bounds.centerY, (bounds.width * 0.82).toInt(), bounds.centerY)
        else -> intArrayOf(bounds.centerX, (bounds.height * 0.78).toInt(), bounds.centerX, (bounds.height * 0.28).toInt())
    }
}
