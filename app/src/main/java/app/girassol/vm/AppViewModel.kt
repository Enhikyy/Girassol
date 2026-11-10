package app.girassol.vm

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.girassol.AppContainer
import app.girassol.core.activity.ActivityKind
import app.girassol.core.ai.LocalModel
import app.girassol.core.chat.ChatRole
import app.girassol.core.data.ThemeMode
import app.girassol.core.diagnostics.CodeIssue
import app.girassol.core.learning.SleepScheduler
import app.girassol.core.research.DatasetResult
import app.girassol.core.studio.AndroidProject
import app.girassol.core.studio.BuildResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Notice(val message: String, val error: Boolean = false)
data class CodexProposal(val path: String, val before: String, val after: String)

class AppViewModel(private val container: AppContainer) : ViewModel() {
    val activityHub = container.activities
    val activities = container.activities.state
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, app.girassol.core.data.GirassolSettings())
    val notes = container.research.notes
    val memory = container.memory.state
    val chat = container.chat.messages
    val toolchain = container.toolchain.state

    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val notice = mutableNotice.asStateFlow()
    private val mutableModels = MutableStateFlow(container.models.list())
    val models = mutableModels.asStateFlow()
    private val mutableImportBytes = MutableStateFlow<Long?>(null)
    val importBytes = mutableImportBytes.asStateFlow()
    private val mutableGenerating = MutableStateFlow(false)
    val generating = mutableGenerating.asStateFlow()
    private val mutableBusy = MutableStateFlow<String?>(null)
    val busy = mutableBusy.asStateFlow()
    private val mutableDataset = MutableStateFlow<DatasetResult?>(null)
    val dataset = mutableDataset.asStateFlow()

    private val mutableProjects = MutableStateFlow<List<AndroidProject>>(emptyList())
    val projects = mutableProjects.asStateFlow()
    private val mutableProject = MutableStateFlow<AndroidProject?>(null)
    val project = mutableProject.asStateFlow()
    private val mutableFiles = MutableStateFlow<List<String>>(emptyList())
    val files = mutableFiles.asStateFlow()
    private val mutableFile = MutableStateFlow<String?>(null)
    val selectedFile = mutableFile.asStateFlow()
    private val mutableEditor = MutableStateFlow("")
    val editor = mutableEditor.asStateFlow()
    private val mutableSavedEditor = MutableStateFlow("")
    val editorDirty = MutableStateFlow(false)
    private val mutableIssues = MutableStateFlow<List<CodeIssue>>(emptyList())
    val issues = mutableIssues.asStateFlow()
    private val mutableBuilding = MutableStateFlow(false)
    val building = mutableBuilding.asStateFlow()
    private val mutableBuild = MutableStateFlow<BuildResult?>(null)
    val buildResult = mutableBuild.asStateFlow()
    private val mutableTesting = MutableStateFlow(false)
    val testing = mutableTesting.asStateFlow()
    private val mutableTestResult = MutableStateFlow<BuildResult?>(null)
    val testResult = mutableTestResult.asStateFlow()
    private val mutableCodex = MutableStateFlow<CodexProposal?>(null)
    val codex = mutableCodex.asStateFlow()

    init { refreshProjects() }

    fun dismissNotice() { mutableNotice.value = null }
    private fun notify(message: String, error: Boolean = false) { mutableNotice.value = Notice(message, error) }
    private fun launchTask(label: String, block: suspend () -> Unit) = viewModelScope.launch {
        mutableBusy.value = label
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) { notify(failure.message ?: "Operação falhou", true) }
        finally { mutableBusy.value = null }
    }

    fun setTheme(value: ThemeMode) = launchTask("Aplicando tema") { container.settings.setTheme(value) }
    fun setAnimationScale(value: Float) = launchTask("Ajustando movimento") { container.settings.setAnimationScale(value) }
    fun setAutomaticSleep(value: Boolean) = launchTask("Configurando sono") {
        container.settings.setAutomaticSleep(value)
        SleepScheduler.schedule(container.appContext, value)
    }

    fun importModel(uri: Uri) = launchTask("Importando modelo GGUF") {
        mutableImportBytes.value = 0
        try {
            val model = withContext(Dispatchers.IO) {
                container.models.import(container.appContext, uri) { mutableImportBytes.value = it }
            }
            mutableModels.value = container.models.list()
            if (settings.value.activeModelPath.isBlank()) container.settings.setActiveModel(model.path)
            notify("Modelo ${model.name} importado e validado")
        } finally { mutableImportBytes.value = null }
    }

    fun selectModel(model: LocalModel) = launchTask("Selecionando modelo") {
        container.settings.setActiveModel(model.path)
        notify("${model.name} será usado na próxima inferência")
    }

    fun deleteModel(model: LocalModel) = launchTask("Excluindo modelo") {
        if (mutableGenerating.value) { notify("Cancele a inferência antes de excluir o modelo ativo", true); return@launchTask }
        if (settings.value.activeModelPath == model.path) {
            container.llm.close()
            container.settings.setActiveModel("")
        }
        withContext(Dispatchers.IO) { container.models.delete(model) }
        mutableModels.value = container.models.list()
    }

    fun sendChat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || mutableGenerating.value) return
        val modelPath = settings.value.activeModelPath
        if (modelPath.isBlank() || !File(modelPath).isFile) {
            notify("Importe e selecione um modelo GGUF antes de conversar", true); return
        }
        val history = chat.value.takeLast(10)
        container.chat.append(ChatRole.USER, clean)
        val pending = container.chat.append(ChatRole.ASSISTANT, "")
        val operation = container.activities.begin(ActivityKind.CHAT, "Conversando com o modelo", File(modelPath).name)
        mutableGenerating.value = true
        viewModelScope.launch {
            val answer = StringBuilder()
            try {
                val context = history.joinToString("\n\n") { "${if (it.role == ChatRole.USER) "Usuário" else "Assistente"}: ${it.content}" }.takeLast(18_000)
                container.llm.generate(
                    modelPath = modelPath,
                    system = "Você é Girassol, um assistente local útil e rigoroso. Responda em português, admita incertezas e não invente execuções.",
                    context = context,
                    user = clean,
                    onDelta = { delta ->
                        synchronized(answer) {
                            answer.append(delta)
                            container.chat.update(pending.id, answer.toString(), persist = false)
                        }
                    },
                )
                container.chat.update(pending.id, answer.toString().ifBlank { "O modelo concluiu sem produzir texto." })
                container.activities.complete(operation, "Resposta local concluída")
            } catch (cancelled: CancellationException) {
                container.chat.update(pending.id, answer.toString().ifBlank { "Geração cancelada." })
                container.activities.fail(operation, "Geração cancelada")
                throw cancelled
            } catch (failure: Throwable) {
                container.chat.update(pending.id, "Falha na inferência local: ${failure.message ?: "erro desconhecido"}")
                container.activities.fail(operation, failure.message ?: "Falha na inferência")
                notify(failure.message ?: "Falha na inferência", true)
            } finally { mutableGenerating.value = false }
        }
    }

    fun cancelGeneration() { container.llm.cancel() }

    fun clearChat() = container.chat.clear()

    fun research(topic: String) = launchTask("Pesquisando fontes públicas") {
        val found = container.research.research(topic)
        notify("${found.size} fontes integradas à Biblioteca")
    }

    fun createDataset(topic: String) = launchTask("Gerando dataset auditável") {
        mutableDataset.value = withContext(Dispatchers.IO) { container.research.createDataset(topic) }
        notify("Dataset criado: ${mutableDataset.value?.examples} exemplos. Nenhum peso do modelo foi alterado.")
    }

    fun consolidate() = launchTask("Consolidando memória") {
        val snapshot = withContext(Dispatchers.IO) { container.memory.consolidate() }
        notify("Sono concluído: ${snapshot.insights.size} sínteses disponíveis")
    }

    fun refreshProjects() = launchTask("Abrindo workspace") {
        val list = withContext(Dispatchers.IO) { container.projects.listProjects() }
        mutableProjects.value = list
        val current = mutableProject.value
        if (current != null) {
            list.firstOrNull { it.root == current.root }?.let { refreshed ->
                mutableProject.value = refreshed
                mutableFiles.value = container.projects.listFiles(refreshed.root)
            }
        }
    }

    fun createProject(name: String, packageName: String, compose: Boolean) = launchTask("Criando projeto Android") {
        val created = withContext(Dispatchers.IO) { container.projects.createProject(name, packageName, compose) }
        mutableProjects.value = container.projects.listProjects()
        selectProject(created)
        notify("Projeto ${created.name} criado")
    }

    fun selectProject(value: AndroidProject) {
        mutableProject.value = value
        mutableFiles.value = runCatching { container.projects.listFiles(value.root) }.getOrDefault(emptyList())
        mutableIssues.value = emptyList(); mutableBuild.value = null; mutableCodex.value = null
        val preferred = mutableFiles.value.firstOrNull { it.endsWith("MainActivity.kt") }
            ?: mutableFiles.value.firstOrNull { it.endsWith(".kt") || it.endsWith(".kts") }
            ?: mutableFiles.value.firstOrNull()
        if (preferred != null) selectFile(preferred) else { mutableFile.value = null; setEditor("") }
    }

    fun selectFile(path: String) {
        val current = mutableProject.value ?: return
        runCatching { container.projects.read(current.root, path) }
            .onSuccess { mutableFile.value = path; setEditor(it) }
            .onFailure { notify(it.message ?: "Não foi possível abrir o arquivo", true) }
    }

    private fun setEditor(value: String) {
        mutableEditor.value = value; mutableSavedEditor.value = value; editorDirty.value = false
    }

    fun editEditor(value: String) {
        mutableEditor.value = value
        editorDirty.value = value != mutableSavedEditor.value
    }

    fun saveFile() = launchTask("Salvando arquivo") {
        val current = mutableProject.value ?: return@launchTask
        val path = mutableFile.value ?: return@launchTask
        withContext(Dispatchers.IO) { container.projects.write(current.root, path, mutableEditor.value) }
        mutableSavedEditor.value = mutableEditor.value; editorDirty.value = false
        refreshProjectsSilently()
        notify("$path salvo")
    }

    fun diagnose() = launchTask("Diagnosticando projeto") {
        val current = mutableProject.value ?: run { notify("Selecione um projeto", true); return@launchTask }
        mutableIssues.value = withContext(Dispatchers.IO) { container.analyzer.analyze(current.root) }
        notify("Diagnóstico concluído: ${mutableIssues.value.size} achados")
    }

    fun applyFix(issue: CodeIssue) = launchTask("Aplicando correção segura") {
        val current = mutableProject.value ?: return@launchTask
        withContext(Dispatchers.IO) {
            container.analyzer.applySafeFix(current.root, issue)
            mutableIssues.value = container.analyzer.analyze(current.root)
        }
        if (mutableFile.value == issue.file) selectFile(issue.file)
        notify("Espaços finais corrigidos em ${issue.file}")
    }

    fun testProject() {
        val current = mutableProject.value ?: run { notify("Selecione um projeto", true); return }
        if (editorDirty.value) { notify("Salve o arquivo antes de testar", true); return }
        mutableTesting.value = true
        viewModelScope.launch {
            try {
                mutableTestResult.value = container.toolchain.test(current.root)
                notify(if (mutableTestResult.value?.successful == true) "Auto-teste concluído" else "Auto-teste encontrou falhas", mutableTestResult.value?.successful != true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { notify(failure.message ?: "Não foi possível testar", true) }
            finally { mutableTesting.value = false }
        }
    }

    fun installToolchain() = launchTask("Instalando toolchain") {
        container.toolchain.install()
        notify("Toolchain validado no armazenamento privado")
    }

    fun build() {
        val current = mutableProject.value ?: run { notify("Selecione um projeto", true); return }
        if (editorDirty.value) { notify("Salve o arquivo antes de compilar", true); return }
        mutableBuilding.value = true
        viewModelScope.launch {
            try {
                val result = container.toolchain.build(current.root)
                mutableBuild.value = result
                notify(if (result.successful) "APK gerado com sucesso" else "Build falhou; abra o diagnóstico e o log", !result.successful)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { notify(failure.message ?: "Build falhou", true) }
            finally { mutableBuilding.value = false }
        }
    }

    fun cancelBuild() { container.toolchain.cancel() }
    fun apkIntent(): Intent? = mutableBuild.value?.apk?.let(container.toolchain::installIntent)

    fun proposeCodex(instruction: String) {
        val clean = instruction.trim()
        val current = mutableProject.value ?: run { notify("Selecione um projeto", true); return }
        val path = mutableFile.value ?: run { notify("Abra um arquivo para o Codex", true); return }
        val modelPath = settings.value.activeModelPath
        if (clean.length < 4) { notify("Descreva a alteração desejada", true); return }
        if (modelPath.isBlank() || !File(modelPath).isFile) { notify("O Codex usa o mesmo modelo GGUF local; importe um modelo primeiro", true); return }
        if (mutableGenerating.value) { notify("Aguarde a inferência atual terminar", true); return }
        val before = mutableEditor.value
        val operation = container.activities.begin(ActivityKind.BUILD, "Codex no Studio", "Gerando proposta para $path")
        mutableGenerating.value = true
        viewModelScope.launch {
            try {
                val answer = StringBuilder()
                container.llm.generate(
                    modelPath = modelPath,
                    system = "Você é um editor Android rigoroso. Retorne SOMENTE o arquivo completo revisado, sem explicações. Preserve o que não precisa mudar.",
                    context = "Projeto: ${current.name}\nArquivo: $path\nConteúdo atual:\n$before",
                    user = clean,
                    onDelta = { delta -> synchronized(answer) { answer.append(delta) } },
                )
                val proposed = extractCode(answer.toString())
                require(proposed.isNotBlank()) { "O modelo não retornou um arquivo utilizável" }
                mutableCodex.value = CodexProposal(path, before, proposed)
                container.activities.complete(operation, "Proposta pronta para revisão; arquivo ainda não alterado")
                notify("Revise a proposta. Nada foi gravado no projeto.")
            } catch (cancelled: CancellationException) {
                container.activities.fail(operation, "Proposta cancelada"); throw cancelled
            } catch (failure: Throwable) {
                container.activities.fail(operation, failure.message ?: "Falha no Codex")
                notify(failure.message ?: "Falha no Codex", true)
            } finally { mutableGenerating.value = false }
        }
    }

    fun applyCodex() {
        val proposal = mutableCodex.value ?: return
        if (proposal.path != mutableFile.value) { notify("Abra o arquivo da proposta antes de aplicar", true); return }
        editEditor(proposal.after)
        mutableCodex.value = null
        notify("Proposta aplicada somente ao editor. Revise e salve para gravar.")
    }

    fun discardCodex() { mutableCodex.value = null }

    fun exportProject(uri: Uri) = launchTask("Exportando projeto") {
        val current = mutableProject.value ?: return@launchTask
        withContext(Dispatchers.IO) {
            container.appContext.contentResolver.openOutputStream(uri)?.use { container.projects.export(current.root, it) }
                ?: error("Não foi possível criar o ZIP")
        }
        notify("Projeto exportado em ZIP")
    }

    fun importProject(uri: Uri) = launchTask("Importando projeto") {
        val imported = withContext(Dispatchers.IO) {
            container.appContext.contentResolver.openInputStream(uri)?.use { container.projects.import(it) }
                ?: error("Não foi possível abrir o ZIP")
        }
        mutableProjects.value = container.projects.listProjects()
        selectProject(imported)
        notify("Projeto ${imported.name} importado")
    }

    private fun refreshProjectsSilently() {
        mutableProjects.value = container.projects.listProjects()
        mutableProject.value = mutableProjects.value.firstOrNull { it.root == mutableProject.value?.root }
    }

    private fun extractCode(raw: String): String {
        val fenced = Regex("```(?:[A-Za-z0-9_+-]+)?\\s*([\\s\\S]*?)```").find(raw)
        return (fenced?.groupValues?.get(1) ?: raw).trim().plus("\n")
    }

    override fun onCleared() {
        container.llm.cancel()
        container.toolchain.cancel()
        container.llm.close()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
        }
    }
}
