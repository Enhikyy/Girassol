package app.girassol.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.girassol.core.diagnostics.Severity
import app.girassol.core.studio.ToolchainStatus
import app.girassol.ui.components.ScreenHeader
import app.girassol.ui.components.SectionCard
import app.girassol.vm.AppViewModel

@Composable
fun StudioScreen(model: AppViewModel) {
    val projects by model.projects.collectAsState()
    val project by model.project.collectAsState()
    val files by model.files.collectAsState()
    val selectedFile by model.selectedFile.collectAsState()
    val editor by model.editor.collectAsState()
    val dirty by model.editorDirty.collectAsState()
    val issues by model.issues.collectAsState()
    val toolchain by model.toolchain.collectAsState()
    val building by model.building.collectAsState()
    val build by model.buildResult.collectAsState()
    val testing by model.testing.collectAsState()
    val testResult by model.testResult.collectAsState()
    val codex by model.codex.collectAsState()
    val generating by model.generating.collectAsState()
    val busy by model.busy.collectAsState()
    val context = LocalContext.current
    var createOpen by remember { mutableStateOf(false) }
    var projectsOpen by remember { mutableStateOf(false) }
    var filesOpen by remember { mutableStateOf(false) }
    var codexInstruction by rememberSaveable { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.exportProject(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importProject(uri)
    }
    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeader("Studio · Android nativo", "Projete, revise e compile.", "Workspace local com editor, Codex revisável, diagnóstico e toolchain privado.") {
            Row {
                IconButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) { Icon(Icons.Default.Folder, "Importar ZIP") }
                IconButton(onClick = { createOpen = true }) { Icon(Icons.Default.Add, "Novo projeto") }
            }
        } }
        item {
            SectionCard("Workspace", subtitle = "Projetos Compose ou Views criados do zero, sem reutilizar implementações externas.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { projectsOpen = true }) { Text(project?.name ?: "Selecionar projeto") }
                    Button(onClick = { createOpen = true }) { Icon(Icons.Default.Add, null); Text(" Novo") }
                }
                if (project != null) {
                    Text("${project?.fileCount} arquivos · ${project?.root?.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { exportLauncher.launch("${project?.name}.zip") }) { Text("Exportar projeto em ZIP") }
                } else if (projects.isEmpty()) Text("Nenhum projeto ainda. Crie um projeto para abrir o editor.")
            }
        }
        if (project != null) {
            item {
                SectionCard("Editor de código", subtitle = selectedFile ?: "Selecione um arquivo", icon = Icons.Default.Code) {
                    OutlinedButton(onClick = { filesOpen = true }, enabled = files.isNotEmpty()) { Text("Abrir arquivo · ${files.size}") }
                    if (selectedFile != null) {
                        OutlinedTextField(
                            value = editor,
                            onValueChange = model::editEditor,
                            modifier = Modifier.fillMaxWidth().height(390.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = model::saveFile, enabled = dirty && busy == null) { Icon(Icons.Default.Save, null); Text(" Salvar") }
                            OutlinedButton(onClick = model::diagnose, enabled = busy == null) { Text("Diagnosticar") }
                        }
                        if (dirty) Text("Alterações somente no editor; salve para gravar e compilar.", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            item {
                SectionCard("Modo Codex", subtitle = "Usa o GGUF ativo. A proposta não modifica o arquivo antes da sua revisão.") {
                    OutlinedTextField(value = codexInstruction, onValueChange = { codexInstruction = it.take(4_000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Alteração desejada") }, placeholder = { Text("Ex.: adicione uma tela inicial responsiva") }, minLines = 3)
                    Button(onClick = { model.proposeCodex(codexInstruction) }, enabled = !generating && selectedFile != null && codexInstruction.trim().length >= 4) { Text(if (generating) "Gerando…" else "Gerar proposta local") }
                    codex?.let { proposal ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Proposta para ${proposal.path}", style = MaterialTheme.typography.titleMedium)
                                Text("${proposal.before.lines().size} → ${proposal.after.lines().size} linhas. Revise o conteúdo abaixo; aplicar envia ao editor, não ao disco.")
                                SelectionContainer(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                                    Text(proposal.after, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = model::applyCodex) { Text("Aplicar ao editor") }
                                    OutlinedButton(onClick = model::discardCodex) { Text("Descartar") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard("Consultório · diagnóstico", subtitle = "Regras determinísticas para achados comuns. Apenas espaços finais têm correção automática.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = model::diagnose, enabled = busy == null) { Text("Analisar código") }
                        OutlinedButton(onClick = model::testProject, enabled = toolchain.status == ToolchainStatus.READY && !testing && !building && !dirty) { Text(if (testing) "Testando…" else "Auto-teste real") }
                    }
                    testResult?.let { result ->
                        Text(if (result.successful) "Testes concluídos" else "Testes falharam", color = if (result.successful) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                        SelectionContainer(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            Text(result.output, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                    if (issues.isEmpty()) Text("Nenhum achado na análise atual.")
                    issues.forEach { issue ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${issue.severity} · ${issue.code}", style = MaterialTheme.typography.labelLarge, color = if (issue.severity == Severity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Text("${issue.file}:${issue.line} — ${issue.message}")
                            if (issue.autoFix) TextButton(onClick = { model.applyFix(issue) }) { Text("Aplicar correção segura") }
                        }
                        HorizontalDivider()
                    }
                }
            }
            item {
                SectionCard("Build Android real", subtitle = toolchain.detail, icon = Icons.Default.Build) {
                    when (toolchain.status) {
                        ToolchainStatus.NOT_INSTALLED, ToolchainStatus.ERROR -> Button(onClick = model::installToolchain, enabled = busy == null) { Text("Instalar toolchain privado") }
                        ToolchainStatus.INSTALLING -> Text("Instalação em andamento. Os componentes são verificados antes do uso.")
                        ToolchainStatus.READY -> Text("JDK, Gradle, SDK 35 e AAPT2 prontos.", color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("Requer ARM64 e aproximadamente 3 GB livres. O processo pode demorar e baixa componentes de fontes pinadas por SHA-256.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = model::build, enabled = toolchain.status == ToolchainStatus.READY && !building && !testing && !dirty) { Icon(Icons.Default.PlayArrow, null); Text(if (building) " Compilando…" else " assembleDebug") }
                        if (building) OutlinedButton(onClick = model::cancelBuild) { Icon(Icons.Default.Close, null); Text(" Cancelar") }
                    }
                    build?.let { result ->
                        Text(if (result.successful) "BUILD SUCCESSFUL" else "BUILD FAILED", style = MaterialTheme.typography.titleMedium, color = if (result.successful) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                        if (result.successful && result.apk != null) Button(onClick = {
                            val intent = if (context.packageManager.canRequestPackageInstalls()) model.apkIntent()
                            else Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            if (intent != null) installLauncher.launch(intent)
                        }) { Icon(Icons.Default.Android, null); Text(" Instalar APK com confirmação") }
                        SelectionContainer(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                            Text(result.output.takeLast(80_000), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                    if (toolchain.logs.isNotEmpty()) {
                        Text("Log do toolchain", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            Text(toolchain.logs.takeLast(35).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        var name by rememberSaveable { mutableStateOf("") }
        var packageName by rememberSaveable { mutableStateOf("com.exemplo.app") }
        var compose by rememberSaveable { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text("Novo projeto Android") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(48) }, label = { Text("Nome") }, singleLine = true)
                    OutlinedTextField(value = packageName, onValueChange = { packageName = it.take(100) }, label = { Text("Pacote") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = compose, onCheckedChange = { compose = it })
                        Text("Interface com Jetpack Compose")
                    }
                }
            },
            confirmButton = { Button(onClick = { model.createProject(name, packageName, compose); createOpen = false }, enabled = name.trim().length >= 3) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text("Cancelar") } },
        )
    }
    if (projectsOpen) {
        AlertDialog(
            onDismissRequest = { projectsOpen = false },
            title = { Text("Projetos no workspace") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(projects, key = { it.root.absolutePath }) { item ->
                        Row(Modifier.fillMaxWidth().clickable { model.selectProject(item); projectsOpen = false }.padding(14.dp)) {
                            Column { Text(item.name, style = MaterialTheme.typography.titleMedium); Text("${item.fileCount} arquivos", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { projectsOpen = false }) { Text("Fechar") } },
        )
    }
    if (filesOpen) {
        AlertDialog(
            onDismissRequest = { filesOpen = false },
            title = { Text("Arquivos do projeto") },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(files, key = { it }) { path ->
                        Text(path, modifier = Modifier.fillMaxWidth().clickable { model.selectFile(path); filesOpen = false }.padding(12.dp), color = if (path == selectedFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { filesOpen = false }) { Text("Fechar") } },
        )
    }
}
