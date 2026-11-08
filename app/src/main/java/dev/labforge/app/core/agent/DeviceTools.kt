package dev.labforge.app.core.agent

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Intent
import android.os.BatteryManager
import androidx.core.content.FileProvider
import dev.labforge.app.integration.android.DeviceRuntime
import dev.labforge.app.integration.android.GirassolAccessibilityService
import dev.labforge.app.integration.android.TermuxBridge
import dev.labforge.app.core.term.ShellSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

class AndroidControlTool(ws: File) : AgentTool(ws) {
    override val name = "android_control"
    override val description = "Controla a interface por Acessibilidade, sem root: hierarchy, tap, swipe, click_text, text, key, screenshot, launch, metrics."
    override fun parameters() = schema(JSONObject().apply {
        put("action", prop("string", "hierarchy, tap, swipe, click_text, text, key, screenshot, launch ou metrics"))
        listOf("x", "y", "x2", "y2", "duration", "keycode").forEach { put(it, prop("integer", it)) }
        put("text", prop("string", "Texto Unicode para o campo focado ou rótulo exato em click_text"))
        put("view_id", prop("string", "ID do campo de texto (opcional)"))
        put("package", prop("string", "Pacote Android para iniciar"))
    }, listOf("action"))

    override suspend fun execute(args: JSONObject): ToolResult = withContext(Dispatchers.Main) {
        val context = DeviceRuntime.context
        when (args.getString("action")) {
            "metrics" -> {
                val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
                val battery = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                return@withContext ToolResult(true, "RAM livre=${memory.availMem}; RAM total=${memory.totalMem}; bateria=$battery%; CPUs=${Runtime.getRuntime().availableProcessors()}")
            }
            "launch" -> {
                val intent = context.packageManager.getLaunchIntentForPackage(args.getString("package"))
                    ?: return@withContext ToolResult(false, "Aplicativo não instalado ou sem launcher")
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@withContext ToolResult(true, "Abertura solicitada ao Android")
            }
        }
        val service = GirassolAccessibilityService.connected
            ?: return@withContext ToolResult(false, "Ative Girassol em Ajustes → Acessibilidade do Android")
        when (args.getString("action")) {
            "hierarchy" -> ToolResult(true, service.hierarchy())
            "tap", "swipe" -> {
                val x = args.getInt("x").toFloat(); val y = args.getInt("y").toFloat()
                val swipe = args.getString("action") == "swipe"
                val ok = withTimeoutOrNull(10_000) { service.gesture(x, y,
                    if (swipe) args.getInt("x2").toFloat() else x,
                    if (swipe) args.getInt("y2").toFloat() else y,
                    if (swipe) args.optLong("duration", 300) else 50) } ?: false
                ToolResult(ok, if (ok) "Gesto concluído" else "Gesto recusado ou cancelado pelo Android")
            }
            "click_text" -> {
                val ok = service.clickText(args.getString("text"))
                ToolResult(ok, if (ok) "Clique realizado" else "Rótulo ausente, ambíguo ou sem ancestral clicável")
            }
            "text" -> {
                val ok = service.setText(args.getString("text"), args.optString("view_id"))
                ToolResult(ok, if (ok) "Texto atualizado" else "Campo focado/ID não encontrado ou não editável")
            }
            "key" -> {
                val action = when (args.getInt("keycode")) {
                    3 -> AccessibilityService.GLOBAL_ACTION_HOME
                    4 -> AccessibilityService.GLOBAL_ACTION_BACK
                    187 -> AccessibilityService.GLOBAL_ACTION_RECENTS
                    else -> return@withContext ToolResult(false, "Sem root, key aceita HOME=3, BACK=4 e RECENTS=187")
                }
                val ok = service.performGlobalAction(action)
                ToolResult(ok, if (ok) "Ação enviada" else "Ação recusada")
            }
            "screenshot" -> {
                val target = File(context.filesDir, "labforge/output/screenshot-${System.currentTimeMillis()}.png")
                val captured = withTimeoutOrNull(10_000) { service.screenshot(target) }
                ToolResult(captured != null, captured?.absolutePath ?: "Tempo esgotado aguardando captura pelo Android")
            }
            else -> ToolResult(false, "ação desconhecida")
        }
    }
}

class TermuxCommandTool(ws: File, private val project: String) : AgentTool(ws) {
    override val name = "termux_command"
    override val description = "Executa comandos e edições no workspace do Termux por RUN_COMMAND e devolve stdout/stderr/exitCode. Sem root. Arquivos privados do Girassol não são visíveis ao Termux."
    override fun parameters() = schema(JSONObject().apply {
        put("command", prop("string", "Comando bash"))
        put("project", prop("string", "Diretório no Termux"))
    }, listOf("command"))
    override suspend fun execute(args: JSONObject): ToolResult {
        val result = TermuxBridge.run(DeviceRuntime.context, args.optString("project", project), args.getString("command"))
        return ToolResult(result.ok, "${result.output}\nexit=${result.exitCode}")
    }
}

class AndroidBuildTool(ws: File, private val project: String = "${TermuxBridge.HOME}/girassol") : AgentTool(ws) {
    override val name = "android_build"
    override val description = "Compila/testa no Termux por RUN_COMMAND. pipeline (validação completa), diagnose, clone, build, test, lint, install. Instalação abre a tela Android para confirmação."
    override fun parameters() = schema(JSONObject().apply {
        put("action", prop("string", "pipeline, diagnose, clone, build, test, lint ou install"))
        put("project", prop("string", "Diretório do projeto no Termux; independente do workspace privado do Girassol"))
        put("repository", prop("string", "URL git para clone"))
        put("apk", prop("string", "Caminho do APK"))
        put("module", prop("string", "Módulo Gradle, padrão :app"))
        put("variant", prop("string", "debug ou release, padrão debug"))
        put("offline", prop("boolean", "Usar somente dependências em cache"))
    }, listOf("action"))
    override suspend fun execute(args: JSONObject): ToolResult {
        val action = args.getString("action")
        val context = DeviceRuntime.context
        if (action == "pipeline") {
            val variant = dev.labforge.app.core.compiler.BuildVariant.valueOf(args.optString("variant", "debug").uppercase(java.util.Locale.ROOT))
            val request = dev.labforge.app.core.compiler.BuildRequest(TermuxBridge.projectPath(args.optString("project", project)),
                args.optString("module", ":app"), variant, args.optBoolean("offline", false))
            val result = dev.labforge.app.core.compiler.BuildCoordinator.run(context, request)
            return ToolResult(result.ok, result.json().toString(2))
        }
        if (action == "install" && File(args.getString("apk")).isFile) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(args.getString("apk")))
            withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,
                "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return ToolResult(true, "Instalador aberto; a instalação depende da confirmação na tela do Android")
        }
        val dir = TermuxBridge.projectPath(args.optString("project", project))
        val options = " --no-daemon -Dorg.gradle.native=false -Pandroid.aapt2FromMavenOverride=\"\$PREFIX/bin/aapt2\""
        val command = when (action) {
            "diagnose" -> "java -version && command -v aapt2 && test -d \"\$ANDROID_HOME\" && sh ./gradlew --version"
            "clone" -> "git clone -- ${ShellSession.quote(args.getString("repository"))} ${ShellSession.quote(dir)}"
            "build" -> "sh ./gradlew :app:assembleDebug$options && find app/build/outputs/apk -name '*.apk'"
            "test" -> "sh ./gradlew :app:testDebugUnitTest$options"
            "lint" -> "sh ./gradlew :app:lintDebug$options"
            "install" -> "termux-open --view --content-type application/vnd.android.package-archive ${ShellSession.quote(args.getString("apk"))}"
            else -> return ToolResult(false, "ação desconhecida")
        }
        val result = TermuxBridge.run(context, if (action == "clone") TermuxBridge.HOME else dir, command)
        return ToolResult(result.ok, "${result.output}\nexit=${result.exitCode}" +
            if (action == "install" && result.ok) "\nInstalador solicitado; confirme a instalação no Android" else "")
    }
}

class AndroidUiTestTool(ws: File) : AgentTool(ws) {
    override val name = "android_ui_test"
    override val description = "Executa ações por Acessibilidade e assert_text na hierarquia real, sem root. Retorna o primeiro passo que falhou."
    override fun parameters() = schema(JSONObject().apply {
        put("steps", JSONObject().put("type", "array").put("items", JSONObject().put("type", "object")))
    }, listOf("steps"))
    override suspend fun execute(args: JSONObject): ToolResult {
        val steps = args.getJSONArray("steps")
        require(steps.length() > 0) { "Informe pelo menos um passo" }
        val control = AndroidControlTool(workspace)
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val result = if (step.optString("action") == "assert_text") {
                val deadline = android.os.SystemClock.elapsedRealtime() + step.optLong("timeout_ms", 5000).coerceIn(0, 30_000)
                var result: ToolResult
                do {
                    val tree = control.execute(JSONObject().put("action", "hierarchy"))
                    result = ToolResult(tree.ok && hierarchyContainsText(tree.output, step.getString("text")), tree.output)
                    if (result.ok || !tree.ok || android.os.SystemClock.elapsedRealtime() >= deadline) break
                    delay(150)
                } while (true)
                result
            } else control.execute(step)
            if (!result.ok) return ToolResult(false, "Passo ${i + 1} falhou: ${result.output}")
        }
        return ToolResult(true, "${steps.length()} passos passaram")
    }
}

fun deviceTools(workspace: File, project: String): List<AgentTool> = listOf(
    AndroidControlTool(workspace), AndroidBuildTool(workspace, project), AndroidUiTestTool(workspace), TermuxCommandTool(workspace, project))

internal fun hierarchyContainsText(xml: String, expected: String): Boolean {
    require(expected.isNotEmpty()) { "Texto da asserção não pode ser vazio" }
    val parser = android.util.Xml.newPullParser()
    parser.setInput(java.io.StringReader(xml))
    while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "node") {
            if (listOf("text", "description").any { parser.getAttributeValue(null, it).orEmpty().contains(expected) }) return true
        }
        parser.next()
    }
    return false
}
