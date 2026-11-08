package dev.labforge.app.core.compiler

import dev.labforge.app.core.term.ShellResult
import dev.labforge.app.core.term.ShellSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class BuildVariant(val task: String, val folder: String) { DEBUG("Debug", "debug"), RELEASE("Release", "release") }
data class BuildRequest(val project: String, val module: String = ":app", val variant: BuildVariant = BuildVariant.DEBUG,
                        val offline: Boolean = false, val workers: Int = 2) {
    init {
        require(project.startsWith('/') && project.none { it == '\n' || it == '\r' || it == '\t' }) { "Diretório absoluto inválido" }
        require(module.matches(Regex("(:[A-Za-z0-9_-]+)+"))) { "Módulo inválido; exemplo :app" }
        require(workers in 1..4)
    }
}
enum class StageState { WAITING, RUNNING, PASSED, FAILED, INTERRUPTED }
data class BuildStage(val id: String, val title: String, val command: String, val state: StageState = StageState.WAITING,
                      val output: String = "", val exitCode: Int? = null, val durationMs: Long = 0)
data class BuildArtifact(val path: String, val sha256: String, val bytes: Long)
data class BuildReport(val request: BuildRequest, val startedAt: Long, val stages: List<BuildStage>, val artifacts: List<BuildArtifact> = emptyList()) {
    val ok get() = stages.isNotEmpty() && stages.all { it.state == StageState.PASSED } && artifacts.isNotEmpty()
    fun json(): JSONObject = JSONObject().put("version", 1).put("project", request.project).put("module", request.module)
        .put("variant", request.variant.name).put("offline", request.offline).put("workers", request.workers).put("startedAt", startedAt)
        .put("ok", ok).put("stages", JSONArray().apply { stages.forEach { s -> put(JSONObject().put("id", s.id).put("title", s.title)
            .put("command", s.command).put("state", s.state.name).put("output", s.output).put("exitCode", s.exitCode ?: JSONObject.NULL).put("durationMs", s.durationMs)) } })
        .put("artifacts", JSONArray().apply { artifacts.forEach { a -> put(JSONObject().put("path", a.path).put("sha256", a.sha256).put("bytes", a.bytes)) } })
    companion object {
        fun read(file: File): BuildReport {
            val j = JSONObject(file.readText()); require(j.getInt("version") == 1)
            val request = BuildRequest(j.getString("project"), j.getString("module"), BuildVariant.valueOf(j.getString("variant")), j.getBoolean("offline"), j.getInt("workers"))
            val stages = j.getJSONArray("stages"); val artifacts = j.getJSONArray("artifacts")
            return BuildReport(request, j.getLong("startedAt"), (0 until stages.length()).map { i -> stages.getJSONObject(i).let {
                val state = StageState.valueOf(it.getString("state"))
                BuildStage(it.getString("id"), it.getString("title"), it.getString("command"),
                    if (state == StageState.RUNNING) StageState.INTERRUPTED else state, if (state == StageState.RUNNING) "Girassol foi reiniciado durante esta etapa. Confira o processo no Termux antes de repetir.\n" + it.getString("output") else it.getString("output"),
                    if (it.isNull("exitCode")) null else it.getInt("exitCode"), it.getLong("durationMs"))
            } }, (0 until artifacts.length()).map { i -> artifacts.getJSONObject(i).let { BuildArtifact(it.getString("path"), it.getString("sha256"), it.getLong("bytes")) } })
        }
    }
}

/** Native orchestration; Gradle/JDK/SDK run in Termux, under its own UID. */
object BuildPipeline {
    fun plan(r: BuildRequest): List<BuildStage> {
        val options = " --no-daemon --console=plain --stacktrace --max-workers=${r.workers} -Dorg.gradle.native=false" +
            " -Pandroid.aapt2FromMavenOverride=\"\$PREFIX/bin/aapt2\"" + if (r.offline) " --offline" else ""
        fun gradle(task: String) = "set -euo pipefail\nsh ./gradlew ${r.module}:$task$options"
        val preflight = """
            set -euo pipefail
            test -f ./gradlew || { echo 'Gradle wrapper ausente neste diretório'; exit 2; }
            test -f gradle/wrapper/gradle-wrapper.jar || { echo 'gradle-wrapper.jar ausente'; exit 2; }
            java -version
            export ANDROID_HOME="${'$'}{ANDROID_HOME:-${'$'}{ANDROID_SDK_ROOT:-}}"
            test -d "${'$'}ANDROID_HOME/platforms" || { echo 'SDK Android/platforms ausente; configure ANDROID_HOME'; exit 2; }
            test -x "${'$'}PREFIX/bin/aapt2" || { echo 'aapt2 ARM64 ausente no Termux'; exit 2; }
            command -v sha256sum
            command -v unzip
            command -v apksigner || { echo 'apksigner ausente; instale/configure no Termux'; exit 2; }
            df -h .
            sh ./gradlew --version --no-daemon -Dorg.gradle.native=false
        """.trimIndent()
        val lintReport = ShellSession.quote("${r.module.removePrefix(":").replace(':', '/')}/build/reports/lint-results-${r.variant.folder}.xml")
        val lint = gradle("lint${r.variant.task}") + "\nreport=$lintReport\ntest -f \"\$report\"\nif grep -Eq 'severity=\"(Error|Fatal)\"' \"\$report\"; then echo 'Lint contém Error/Fatal'; exit 3; fi"
        val outputDir = "${r.module.removePrefix(":").replace(':', '/')}/build/outputs/apk/${r.variant.folder}"
        val artifacts = """
            set -euo pipefail
            count=0
            while IFS= read -r -d '' apk; do
                case "${'$'}apk" in *${'$'}'\t'*|*${'$'}'\n'*|*${'$'}'\r'*) echo 'Nome de APK inválido'; exit 2;; esac
                unzip -t "${'$'}apk" >/dev/null
                apksigner verify "${'$'}apk"
                hash=${'$'}(sha256sum -- "${'$'}apk"); hash=${'$'}{hash:0:64}
                bytes=${'$'}(wc -c < "${'$'}apk")
                printf 'GIRASSOL_APK\t%s\t%s\t%s\n' "${'$'}hash" "${'$'}bytes" "${'$'}apk"
                count=${'$'}((count+1))
            done < <(find "${'$'}PWD/OUTPUT_DIR" -type f -name '*.apk' -print0)
            test "${'$'}count" -gt 0 || { echo 'Nenhum APK assinado foi encontrado para esta variante'; exit 2; }
        """.trimIndent().replace("OUTPUT_DIR", outputDir)
        return listOf(BuildStage("preflight", "Diagnóstico", preflight), BuildStage("test", "Testes unitários", gradle("test${r.variant.task}UnitTest")),
            BuildStage("lint", "Análise Android", lint), BuildStage("assemble", "Compilar ${r.variant.task}", gradle("assemble${r.variant.task}")),
            BuildStage("artifacts", "Verificar APK e assinatura", artifacts))
    }

    fun parseArtifacts(output: String, request: BuildRequest): List<BuildArtifact> {
        val prefix = request.project.trimEnd('/') + "/" + request.module.removePrefix(":").replace(':', '/') + "/build/outputs/apk/${request.variant.folder}/"
        return output.lineSequence().filter { it.startsWith("GIRASSOL_APK\t") }.map { row ->
            val parts = row.split('\t'); require(parts.size == 4) { "Manifesto APK inválido" }
            val size = parts[2].trim().toLong()
            require(parts[1].matches(Regex("[a-fA-F0-9]{64}")) && size > 0 && parts[3].startsWith(prefix) && parts[3].endsWith(".apk")) { "Metadados do APK inválidos" }
            BuildArtifact(parts[3], parts[1], size)
        }.toList().also { require(it.isNotEmpty()) { "Termux não retornou metadados de APK verificado" } }
    }

    suspend fun run(request: BuildRequest, execute: suspend (String) -> ShellResult,
                    update: suspend (BuildReport) -> Unit): BuildReport {
        var report = BuildReport(request, System.currentTimeMillis(), plan(request))
        for (index in report.stages.indices) {
            currentCoroutineContext().ensureActive()
            val stage = report.stages[index]
            fun changed(row: BuildStage) = report.copy(stages = report.stages.toMutableList().apply { set(index, row) })
            report = changed(stage.copy(state = StageState.RUNNING)); update(report)
            try {
                val result = execute(stage.command)
                currentCoroutineContext().ensureActive()
                val artifacts = if (result.ok && stage.id == "artifacts") parseArtifacts(result.output, request) else report.artifacts
                report = changed(stage.copy(state = if (result.ok) StageState.PASSED else StageState.FAILED,
                    output = result.output.takeLast(80_000), exitCode = result.exitCode, durationMs = result.durationMs)).copy(artifacts = artifacts)
                update(report)
                if (!result.ok) break
            } catch (cancelled: CancellationException) {
                report = changed(stage.copy(state = StageState.INTERRUPTED, output = "Espera interrompida. O processo no Termux pode continuar; confira-o antes de repetir."))
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { update(report) }
                throw cancelled
            } catch (error: Exception) {
                report = changed(stage.copy(state = StageState.FAILED, output = error.message ?: "Falha na compilação")); update(report); break
            }
        }
        return report
    }

    fun save(file: File, report: BuildReport) {
        file.parentFile?.mkdirs()
        val tmp = File.createTempFile("build-", ".part", file.parentFile)
        try { tmp.writeText(report.json().toString(2)); check(tmp.renameTo(file)) } finally { tmp.delete() }
    }

    fun advice(output: String): String = when {
        output.contains("aapt2", true) -> "Confira o aapt2 ARM64 em \$PREFIX/bin e a configuração do SDK no Termux."
        output.contains("JAVA_HOME", true) || output.contains("java", true) -> "Confira a versão do JDK exigida pelo projeto e JAVA_HOME no Termux."
        output.contains("Could not resolve", true) || output.contains("UnknownHost", true) -> "Confira a conexão e os repositórios. O modo offline exige dependências já armazenadas."
        output.contains("No space left", true) -> "Libere espaço no armazenamento usado pelo Termux e repita a compilação."
        output.contains("SDK", true) -> "Configure ANDROID_HOME e instale a plataforma/build-tools exigidas pelo projeto."
        else -> "Abra o log da etapa, corrija a causa e execute novamente. As etapas anteriores serão revalidadas."
    }
}
