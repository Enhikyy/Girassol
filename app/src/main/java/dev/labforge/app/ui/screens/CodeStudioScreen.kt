package dev.labforge.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.geometry.Mesh
import dev.labforge.app.core.geometry.MeshIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.*

@Composable
fun CodeStudioScreen() {
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    val root= remember { File(context.filesDir,"studio-projects").apply{mkdirs()} }
    val project= remember { File(root,"GirassolWorkspace").apply{mkdirs()} }
    var source by remember { mutableStateOf("// Girassol Native Studio\\n// Edite seu código localmente.\\n") }
    var fileName by remember { mutableStateOf("Main.kt") }
    var status by remember { mutableStateOf("Workspace local pronto") }
    var mode by remember { mutableIntStateOf(0) }
    var mesh by remember { mutableStateOf<Mesh?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(project) {
        val f=File(project,fileName)
        if(f.exists()) source=f.readText()
    }
    val importCode= rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            runCatching {
                val name=Uri.decode(uri.lastPathSegment ?: "Imported.kt").substringAfterLast('/')
                val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()} ?: error("Não foi possível ler o arquivo")}
                fileName=name; source=text
                withContext(Dispatchers.IO){File(project,name).writeText(text)}
                status="Arquivo $name importado para o workspace"
            }.onFailure{status="Importação falhou: ${it.message}"}
        }
    }
    val exportCode=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri!=null) scope.launch { runCatching {
            withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(source)} ?: error("Destino indisponível")}
            status="Arquivo exportado"
        }.onFailure{status="Exportação falhou: ${it.message}"} }
    }
    val importMesh=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch { busy=true
            runCatching {
                val temp=withContext(Dispatchers.IO) {
                    val name=Uri.decode(uri.lastPathSegment ?: "model.obj").substringAfterLast('/')
                    val f=File(context.cacheDir,name)
                    context.contentResolver.openInputStream(uri)?.use { input->f.outputStream().use{input.copyTo(it)} } ?: error("Não foi possível ler a malha")
                    f
                }
                mesh=withContext(Dispatchers.Default){MeshIO.read(temp).normalized()}
                status="Malha carregada: ${mesh?.vertices?.size} vértices · ${mesh?.triangles?.size} triângulos"
            }.onFailure{status="Falha ao abrir 3D: ${it.message}"}
            busy=false
        }
    }
    val exportMesh=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/obj")) { uri ->
        val m=mesh
        if(uri!=null && m!=null) scope.launch { runCatching {
            withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(MeshIO.writeObj(m))} ?: error("Destino indisponível")}
            status="Malha exportada como OBJ"
        }.onFailure{status="Exportação 3D falhou: ${it.message}"} }
    }
    val exportProject=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri!=null) scope.launch { runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { raw->ZipOutputStream(raw).use{zip->
                    project.walkTopDown().filter{it.isFile}.forEach{f->
                        zip.putNextEntry(ZipEntry(f.relativeTo(project).invariantSeparatorsPath))
                        f.inputStream().use{it.copyTo(zip)}
                        zip.closeEntry()
                    }
                }} ?: error("Destino indisponível")
            }
            status="Projeto ZIP exportado"
        }.onFailure{status="ZIP falhou: ${it.message}"} }
    }

    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Native Studio",style=MaterialTheme.typography.headlineSmall)
                Text("Código, projetos e geometria 3D no dispositivo",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick={mode=0}){Icon(Icons.Rounded.Code,"Código")}
            IconButton(onClick={mode=1}){Icon(Icons.Rounded.ViewInAr,"3D")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=mode==0,onClick={mode=0},label={Text("Código")},leadingIcon={Icon(Icons.Rounded.Code,null)})
            FilterChip(selected=mode==1,onClick={mode=1},label={Text("3D Studio")},leadingIcon={Icon(Icons.Rounded.ViewInAr,null)})
        }
        if(mode==0) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={importCode.launch(arrayOf("text/*","application/json","application/xml"))}){Icon(Icons.Rounded.FolderOpen,null);Spacer(Modifier.width(5.dp));Text("Abrir")}
                OutlinedButton(onClick={exportCode.launch(fileName)}){Icon(Icons.Rounded.Save,null);Spacer(Modifier.width(5.dp));Text("Exportar")}
                OutlinedButton(onClick={exportProject.launch("GirassolWorkspace.zip")}){Icon(Icons.Rounded.Archive,null);Spacer(Modifier.width(5.dp));Text("Projeto ZIP")}
            }
            OutlinedTextField(value=fileName,onValueChange={fileName=it},label={Text("Arquivo")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=source,onValueChange={source=it},modifier=Modifier.weight(1f).fillMaxWidth(),label={Text("Editor de código")},textStyle=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace),minLines=12)
            Button(onClick={
                scope.launch { runCatching {
                    withContext(Dispatchers.IO){require(fileName.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && !fileName.contains("..")); File(project,fileName).writeText(source)}
                    status="Salvo em armazenamento privado do app"
                }.onFailure{status="Falha ao salvar: ${it.message}"} }
            },modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Save,null);Spacer(Modifier.width(8.dp));Text("Salvar no projeto")}
            OutlinedButton(onClick={
                val sdk=System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
                status=if(sdk!=null && File(sdk).exists()) "SDK detectado em $sdk; falta validar Gradle/AGP e toolchain local." else "Build APK indisponível: este app ainda não inclui Android SDK, build-tools, JDK e Gradle offline. Editor/exportação funcionam; não vou simular uma compilação."
            },modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.Build,null);Spacer(Modifier.width(8.dp));Text("Verificar toolchain para APK")}
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={importMesh.launch(arrayOf("model/obj","model/stl","application/octet-stream"))}){Icon(Icons.Rounded.FileOpen,null);Spacer(Modifier.width(5.dp));Text("Abrir OBJ/STL")}
                OutlinedButton(onClick={exportMesh.launch("mesh.obj")},enabled=mesh!=null){Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(5.dp));Text("Exportar OBJ")}
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f),RoundedCornerShape(22.dp)).padding(8.dp),contentAlignment=Alignment.Center) {
                val m=mesh
                if(m==null) Text("Importe um arquivo OBJ ou STL para visualizar a malha",color=MaterialTheme.colorScheme.onSurfaceVariant)
                else MeshPreview(m,rotation,Modifier.fillMaxSize())
            }
            Slider(value=rotation,onValueChange={rotation=it},valueRange=0f..(2f*PI.toFloat()))
            mesh?.let{Text("${it.vertices.size} vértices · ${it.triangles.size} triângulos",style=MaterialTheme.typography.labelMedium)}
        }
        Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MeshPreview(mesh:Mesh,rotation:Float,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val ca=cos(rotation); val sa=sin(rotation)
        val projected=mesh.vertices.map { v ->
            val x=v.x*ca-v.z*sa; val z=v.x*sa+v.z*ca
            val y=v.y
            val depth=(z+4.5f).coerceAtLeast(.5f)
            Offset(size.width*.5f+x/depth*size.minDimension*.42f,size.height*.5f-y/depth*size.minDimension*.42f)
        }
        val color=MaterialTheme.colorScheme.primary
        mesh.triangles.forEach { t ->
            val a=projected[t.a];val b=projected[t.b];val c=projected[t.c]
            drawLine(color.copy(alpha=.72f),a,b,strokeWidth=1.25f,cap=StrokeCap.Round)
            drawLine(color.copy(alpha=.72f),b,c,strokeWidth=1.25f,cap=StrokeCap.Round)
            drawLine(color.copy(alpha=.72f),c,a,strokeWidth=1.25f,cap=StrokeCap.Round)
        }
    }
}
