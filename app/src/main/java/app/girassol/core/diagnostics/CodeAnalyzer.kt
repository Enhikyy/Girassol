package app.girassol.core.diagnostics

import app.girassol.core.activity.ActivityHub
import app.girassol.core.activity.ActivityKind
import app.girassol.core.data.SafeFiles
import app.girassol.core.studio.ProjectRepository
import java.io.File

enum class Severity { ERROR, WARNING, INFO }
data class CodeIssue(val file: String, val line: Int, val severity: Severity, val code: String, val message: String, val autoFix: Boolean = false)

class CodeAnalyzer(private val projects: ProjectRepository, private val activities: ActivityHub) {
    fun analyze(project: File): List<CodeIssue> {
        val operation = activities.begin(ActivityKind.DIAGNOSIS, "Sessão de diagnóstico", "Analisando código e configuração")
        return try {
            val issues = projects.listFiles(project).filter { it.substringAfterLast('.') in setOf("kt", "java", "kts", "xml", "properties") }
                .flatMap { path -> inspect(projects.read(project, path), path) }
            activities.complete(operation, "${issues.size} achados: ${issues.count { it.severity == Severity.ERROR }} erro(s)")
            issues.sortedWith(compareBy({ it.severity }, { it.file }, { it.line }))
        } catch (failure: Throwable) {
            activities.fail(operation, failure.message ?: "Falha no diagnóstico")
            throw failure
        }
    }

    fun applySafeFix(project: File, issue: CodeIssue) {
        require(issue.autoFix && issue.code == "TRAILING_SPACE") { "Esta correção exige revisão manual" }
        val content = projects.read(project, issue.file)
        projects.write(project, issue.file, content.lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n")
    }

    private fun inspect(content: String, path: String): List<CodeIssue> {
        val issues = mutableListOf<CodeIssue>()
        var braces = 0
        content.lines().forEachIndexed { index, line ->
            val number = index + 1
            if (line != line.trimEnd()) issues += CodeIssue(path, number, Severity.INFO, "TRAILING_SPACE", "Espaços desnecessários no fim da linha", true)
            if (Regex("\\b(TODO|FIXME)\\b").containsMatchIn(line)) issues += CodeIssue(path, number, Severity.WARNING, "PENDING", "Pendência explícita no código")
            if (Regex("(?i)(api[_-]?key|secret|password)\\s*[=:]\\s*[\"'][^\"']{8,}").containsMatchIn(line)) issues += CodeIssue(path, number, Severity.ERROR, "SECRET", "Possível segredo embutido; mova para armazenamento protegido")
            if (line.contains("http://") && !line.contains("schemas.android.com")) issues += CodeIssue(path, number, Severity.WARNING, "CLEARTEXT", "URL HTTP sem criptografia")
            if (path.endsWith(".kt") && Regex("\\bprint(ln)?\\s*\\(").containsMatchIn(line)) issues += CodeIssue(path, number, Severity.INFO, "LOG", "Prefira um logger controlado em produção")
            if (path.endsWith(".kt") || path.endsWith(".java") || path.endsWith(".kts")) {
                val clean = line.replace(Regex("""("(?:\\.|[^"\\])*")|('(?:\\.|[^'\\])*')"""), "")
                braces += clean.count { it == '{' } - clean.count { it == '}' }
                if (braces < 0) { issues += CodeIssue(path, number, Severity.ERROR, "BRACES", "Chave de fechamento sem abertura correspondente"); braces = 0 }
            }
        }
        if (braces != 0) issues += CodeIssue(path, content.lineSequence().count(), Severity.ERROR, "BRACES", "Chaves desbalanceadas no arquivo")
        if (content.isNotEmpty() && !content.endsWith("\n")) issues += CodeIssue(path, content.lineSequence().count(), Severity.INFO, "NEWLINE", "Adicione quebra de linha ao final")
        return issues
    }
}
