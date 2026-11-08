package dev.labforge.app.core.device

import dev.labforge.app.core.agent.AgentTool
import dev.labforge.app.core.agent.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Device tools exposed to the ReAct agent. They are only useful when the user enabled
 * the accessibility service and the "ferramentas de dispositivo" setting; otherwise each
 * call returns an actionable error instead of failing silently.
 */
abstract class DeviceTool(workspace: File) : AgentTool(workspace) {

    protected fun controller(): DeviceController? = DeviceBridge.controller()

    protected val journal: ActionJournal by lazy {
        ActionJournal(File(workspace, ".girassol/device-journal.jsonl"))
    }

    protected fun unavailable(): ToolResult = ToolResult(
        false,
        "Acessibilidade desativada. Peça ao usuário para abrir Ajustes → Autonomia → " +
            "Acessibilidade do Girassol e ativar o serviço. Sem isso não é possível ler a tela.",
    )

    protected fun selectorFrom(args: JSONObject): UiSelector = UiSelector(
        id = args.optString("id").takeIf { it.isNotBlank() },
        text = args.optString("text").takeIf { it.isNotBlank() },
        desc = args.optString("desc").takeIf { it.isNotBlank() },
        role = args.optString("role").takeIf { it.isNotBlank() },
        exact = args.optBoolean("exact", false),
        index = args.optInt("index", 0).coerceAtLeast(0),
    )

    /**
     * Runs [block], captures the screen signature before/after and appends one journal
     * entry. Journaling failures never change the tool result.
     */
    protected suspend fun record(
        action: String,
        detail: String,
        before: UiSnapshot?,
        block: suspend () -> Pair<Boolean, String>,
    ): ToolResult {
        val started = System.currentTimeMillis()
        val (ok, message) = try {
            block()
        } catch (error: Throwable) {
            false to "falha: ${error.message ?: error.javaClass.simpleName}"
        }
        val after = runCatching { controller()?.snapshot(120) }.getOrNull()
        runCatching {
            journal.append(
                DeviceActionRecord(
                    at = started,
                    action = action,
                    detail = detail,
                    ok = ok,
                    before = before?.signature().orEmpty(),
                    after = after?.signature().orEmpty(),
                    note = message,
                ),
            )
        }
        return ToolResult(ok, message)
    }

    protected suspend fun snapshotOrNull(maxNodes: Int = 400): UiSnapshot? =
        withContext(Dispatchers.IO) { runCatching { controller()?.snapshot(maxNodes) }.getOrNull() }
}

class DeviceUiTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_ui"
    override val description =
        "Lê a árvore de acessibilidade da tela atual (pacote, textos, ids, posições e ações). " +
            "Use antes de tocar ou digitar, e de novo depois para confirmar a mudança."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("max_nodes", JSONObject().apply {
                put("type", "integer"); put("description", "máximo de nós (20–400, padrão 120)")
            })
        })
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        controller() ?: return unavailable()
        val max = args.optInt("max_nodes", 120).coerceIn(20, 400)
        val snapshot = snapshotOrNull(max) ?: return ToolResult(false, "Não foi possível ler a janela ativa.")
        return ToolResult(true, snapshot.describe(max))
            .also { _ ->
                runCatching {
                    journal.append(
                        DeviceActionRecord(
                            System.currentTimeMillis(), name, "max=$max", true,
                            snapshot.signature(), snapshot.signature(), "leitura",
                        ),
                    )
                }
            }
    }
}

class DeviceTapTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_tap"
    override val description =
        "Toca na tela. Prefira informar text/desc/id de um nó; usar x/y é o último recurso."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("x", JSONObject().apply { put("type", "integer") })
            put("y", JSONObject().apply { put("type", "integer") })
            put("text", JSONObject().apply { put("type", "string") })
            put("desc", JSONObject().apply { put("type", "string") })
            put("id", JSONObject().apply { put("type", "string") })
            put("index", JSONObject().apply { put("type", "integer") })
        })
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val before = snapshotOrNull(300)
        val selector = selectorFrom(args)
        val hasPoint = args.has("x") && args.has("y")

        return record(
            action = name,
            detail = if (hasPoint) "x=${args.optInt("x")} y=${args.optInt("y")}" else selector.describe(),
            before = before,
        ) {
            val target = if (!selector.isEmpty) before?.find(selector) else null
            val (x, y) = when {
                target != null -> target.bounds.centerX to target.bounds.centerY
                hasPoint -> args.optInt("x") to args.optInt("y")
                else -> return@record false to "Informe um seletor (text/desc/id) ou x/y."
            }
            val ok = withContext(Dispatchers.IO) { controller.tap(x, y) }
            if (ok) true to "toque em ($x,$y)"
            else false to "o sistema recusou o gesto em ($x,$y)"
        }
    }
}

class DeviceTypeTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_type"
    override val description =
        "Digita texto no campo indicado (text/desc/id) ou no campo que já está com foco."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("text", JSONObject().apply { put("type", "string") })
            put("value", JSONObject().apply { put("type", "string") })
            put("id", JSONObject().apply { put("type", "string") })
            put("desc", JSONObject().apply { put("type", "string") })
            put("index", JSONObject().apply { put("type", "integer") })
        })
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val value = args.optString("value").ifBlank { args.optString("text") }
        if (value.isBlank()) return ToolResult(false, "Informe o texto em value.")
        val before = snapshotOrNull(300)
        val selector = selectorFrom(args)

        return record(name, "campo=${selector.describe()} chars=${value.length}", before) {
            val ok = withContext(Dispatchers.IO) {
                if (selector.isEmpty) controller.setTextFocused(value)
                else controller.setText(selector, value)
            }
            if (ok) true to "texto aplicado (${value.length} chars)"
            else false to "campo não encontrado ou não editável; leia a tela com device_ui"
        }
    }
}

class DeviceSwipeTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_swipe"
    override val description =
        "Rola a tela. direction=up move o conteúdo para cima (dedo sobe); também aceita down/left/right."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("direction", JSONObject().apply {
                put("type", "string"); put("enum", org.json.JSONArray(listOf("up", "down", "left", "right")))
            })
            put("duration_ms", JSONObject().apply { put("type", "integer") })
        })
        put("required", org.json.JSONArray().put("direction"))
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val before = snapshotOrNull(200) ?: return ToolResult(false, "Sem árvore de tela para calcular a área.")
        val screen = before.screenBounds()
        val direction = args.optString("direction", "up").lowercase()
        val duration = args.optInt("duration_ms", 280).coerceIn(80, 2000).toLong()

        val (x1, y1, x2, y2) = when (direction) {
            "up" -> listOf(screen.centerX, (screen.height * 0.78).toInt(), screen.centerX, (screen.height * 0.28).toInt())
            "down" -> listOf(screen.centerX, (screen.height * 0.28).toInt(), screen.centerX, (screen.height * 0.78).toInt())
            "left" -> listOf((screen.width * 0.82).toInt(), screen.centerY, (screen.width * 0.22).toInt(), screen.centerY)
            else -> listOf((screen.width * 0.22).toInt(), screen.centerY, (screen.width * 0.82).toInt(), screen.centerY)
        }

        return record(name, direction, before) {
            val ok = withContext(Dispatchers.IO) { controller.swipe(x1, y1, x2, y2, duration) }
            if (ok) true to "swipe $direction ($x1,$y1)→($x2,$y2)"
            else false to "gesto recusado"
        }
    }
}

class DeviceKeyTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_key"
    override val description = "Pressiona uma tecla de sistema: back, home, recents ou notifications."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("key", JSONObject().apply {
                put("type", "string")
                put("enum", org.json.JSONArray(listOf("back", "home", "recents", "notifications")))
            })
        })
        put("required", org.json.JSONArray().put("key"))
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val key = args.optString("key", "back").lowercase()
        val action = when (key) {
            "home" -> GlobalAction.HOME
            "recents" -> GlobalAction.RECENTS
            "notifications" -> GlobalAction.NOTIFICATIONS
            else -> GlobalAction.BACK
        }
        val before = snapshotOrNull(120)
        return record(name, key, before) {
            val ok = withContext(Dispatchers.IO) { controller.global(action) }
            if (ok) true to "tecla $key" else false to "ação global recusada"
        }
    }
}

class DeviceOpenTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_open"
    override val description = "Abre um app pelo package name (ex.: com.android.settings)."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("package", JSONObject().apply { put("type", "string") })
        })
        put("required", org.json.JSONArray().put("package"))
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val pkg = args.optString("package").trim()
        if (pkg.isBlank()) return ToolResult(false, "Informe package.")
        val before = snapshotOrNull(80)
        return record(name, pkg, before) {
            val ok = withContext(Dispatchers.IO) { controller.openApp(pkg) }
            if (ok) true to "app aberto: $pkg" else false to "pacote sem activity de lançamento: $pkg"
        }
    }
}

class DeviceWaitTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_wait"
    override val description =
        "Espera um nó aparecer na tela (text/desc/id), até timeout_ms. Use depois de abrir um app."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("text", JSONObject().apply { put("type", "string") })
            put("desc", JSONObject().apply { put("type", "string") })
            put("id", JSONObject().apply { put("type", "string") })
            put("timeout_ms", JSONObject().apply { put("type", "integer") })
        })
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val selector = selectorFrom(args)
        if (selector.isEmpty) return ToolResult(false, "Informe text, desc ou id.")
        val timeout = args.optInt("timeout_ms", 6000).coerceIn(500, 30000)
        val deadline = System.currentTimeMillis() + timeout
        var last: UiSnapshot? = null

        while (System.currentTimeMillis() < deadline) {
            val snapshot = snapshotOrNull(300)
            last = snapshot
            val node = snapshot?.find(selector)
            if (node != null) {
                return ToolResult(
                    true,
                    "encontrado após ${timeout - (deadline - System.currentTimeMillis())} ms: " +
                        "${node.role.substringAfterLast('.')} \"${node.text.ifBlank { node.desc }}\" @${node.bounds.centerX},${node.bounds.centerY}",
                )
            }
            delay(350)
        }
        return ToolResult(
            false,
            "timeout de ${timeout} ms. Última tela:\n" + (last?.describe(60) ?: "(sem leitura)"),
        )
    }
}

class DeviceScreenshotTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_screenshot"
    override val description =
        "Captura a tela atual em PNG (Android 11+) e devolve o caminho do arquivo."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val before = snapshotOrNull(80)
        return record(name, "captura", before) {
            val file = withContext(Dispatchers.IO) { controller.screenshot() }
            if (file == null) false to "captura indisponível (Android < 11 ou serviço sem permissão)"
            else true to "captura: ${file.absolutePath} (${file.length() / 1024} KB)"
        }
    }
}

class DeviceScriptTool(workspace: File) : DeviceTool(workspace) {
    override val name = "device_script"
    override val description =
        "Executa um roteiro declarativo de UI (open/wait/tap/type/swipe/key/assert/sleep) e " +
            "devolve um relatório passo a passo. Use para auto-teste ou para repetir um fluxo conhecido."

    override fun parameters(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("script", JSONObject().apply {
                put("type", "string")
                put("description", "JSON do roteiro; veja o formato na descrição da ferramenta")
            })
            put("path", JSONObject().apply {
                put("type", "string")
                put("description", "alternativa: arquivo .json dentro do workspace")
            })
        })
    }

    override suspend fun execute(args: JSONObject): ToolResult {
        val controller = controller() ?: return unavailable()
        val raw = args.optString("script").ifBlank {
            val path = args.optString("path").trim()
            if (path.isBlank()) return ToolResult(false, "Informe script (JSON) ou path.")
            val file = resolve(path)
            if (!file.isFile) return ToolResult(false, "arquivo não encontrado: $path")
            runCatching { file.readText() }.getOrElse {
                return ToolResult(false, "falha ao ler $path: ${it.message}")
            }
        }
        val script = runCatching { DeviceScriptParser.parse(raw) }
            .getOrElse { return ToolResult(false, "roteiro inválido: ${it.message}") }

        val before = snapshotOrNull(80)
        val started = System.currentTimeMillis()
        val report = DeviceScriptRunner(controller).run(script)
        val after = snapshotOrNull(80)
        runCatching {
            journal.append(
                DeviceActionRecord(
                    at = started,
                    action = name,
                    detail = script.name,
                    ok = report.ok,
                    before = before?.signature().orEmpty(),
                    after = after?.signature().orEmpty(),
                    note = report.steps.lastOrNull()?.detail.orEmpty(),
                ),
            )
        }
        return ToolResult(report.ok, report.render())
    }
}

fun deviceTools(workspace: File): List<AgentTool> = listOf(
    DeviceUiTool(workspace),
    DeviceTapTool(workspace),
    DeviceTypeTool(workspace),
    DeviceSwipeTool(workspace),
    DeviceKeyTool(workspace),
    DeviceOpenTool(workspace),
    DeviceWaitTool(workspace),
    DeviceScreenshotTool(workspace),
    DeviceScriptTool(workspace),
)
