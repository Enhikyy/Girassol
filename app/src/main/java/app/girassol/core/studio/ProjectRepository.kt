package app.girassol.core.studio

import app.girassol.core.data.SafeFiles
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AndroidProject(val name: String, val root: File, val modifiedAt: Long, val fileCount: Int)

class ProjectRepository(private val workspace: File) {
    init { workspace.mkdirs() }

    fun listProjects(): List<AndroidProject> = workspace.listFiles().orEmpty().filter { it.isDirectory && File(it, "app/src/main/AndroidManifest.xml").isFile }
        .map { AndroidProject(it.name, it, it.walkTopDown().filter(File::isFile).maxOfOrNull(File::lastModified) ?: it.lastModified(), listFiles(it).size) }
        .sortedByDescending { it.modifiedAt }

    fun createProject(name: String, packageName: String, compose: Boolean = true): AndroidProject {
        val safeName = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").take(48)
        require(safeName.length >= 3) { "Nome do projeto inválido" }
        require(packageName.matches(Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))) { "Use um pacote como com.exemplo.app" }
        val root = SafeFiles.resolve(workspace, safeName)
        require(!root.exists()) { "Já existe um projeto com esse nome" }
        val sourcePath = packageName.replace('.', '/')
        val files = linkedMapOf(
            "settings.gradle.kts" to """
                pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
                dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
                rootProject.name = "${safeName.replace("\"", "")}"; include(":app")
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins { id("com.android.application") version "8.10.1" apply false; id("org.jetbrains.kotlin.android") version "2.2.21" apply false${if (compose) "; id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.2.21\" apply false" else ""} }
            """.trimIndent(),
            "gradle.properties" to "org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n",
            "app/build.gradle.kts" to appGradle(packageName, compose),
            "app/src/main/AndroidManifest.xml" to """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:theme="@style/Theme.Generated" android:label="@string/app_name"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>
            """.trimIndent(),
            "app/src/main/res/values/strings.xml" to "<resources><string name=\"app_name\">${safeName.replace("&", "and").replace("<", "")}</string></resources>",
            "app/src/main/res/values/styles.xml" to "<resources><style name=\"Theme.Generated\" parent=\"android:style/Theme.Material.NoActionBar\"/></resources>",
            "app/src/main/java/$sourcePath/MainActivity.kt" to mainActivity(packageName, safeName, compose),
            "README.md" to "# $safeName\n\nProjeto Android criado no Girassol Studio. Compile localmente com o toolchain configurado.\n",
        )
        files.forEach { (path, content) -> SafeFiles.resolve(root, path).apply { parentFile?.mkdirs(); writeText(content) } }
        return listProjects().first { it.root == root }
    }

    fun listFiles(project: File): List<String> {
        val root = safeProject(project)
        val ignored = setOf("build", ".gradle", ".git", ".idea", ".cxx")
        return root.walkTopDown().onEnter { it == root || it.name !in ignored }.filter { it.isFile }
            .take(2_001).toList().also { require(it.size <= 2_000) { "Projeto possui arquivos demais para indexar" } }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted()
    }

    fun read(project: File, path: String): String {
        val file = SafeFiles.resolve(safeProject(project), path)
        require(file.isFile && file.length() <= 4 * 1024 * 1024) { "Arquivo ausente ou maior que 4 MiB" }
        return file.readText()
    }

    fun write(project: File, path: String, content: String) {
        require(content.length <= 4 * 1024 * 1024) { "Conteúdo maior que 4 MiB" }
        SafeFiles.resolve(safeProject(project), path).apply { parentFile?.mkdirs(); writeText(content) }
    }

    fun deleteProject(project: File) = safeProject(project).deleteRecursively()

    fun export(project: File, output: OutputStream) {
        val root = safeProject(project)
        ZipOutputStream(output.buffered()).use { zip ->
            listFiles(root).forEach { path ->
                val file = SafeFiles.resolve(root, path)
                zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    fun import(input: InputStream): AndroidProject {
        val staging = SafeFiles.resolve(workspace, ".import-${System.nanoTime()}")
        var count = 0
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= 2_000) { "ZIP possui entradas demais" }
                    val name = entry.name.removeSuffix("/")
                    require(name.isNotBlank() && !name.startsWith('/') && !name.contains('\\')) { "Entrada ZIP inválida" }
                    val target = SafeFiles.resolve(staging, name)
                    if (entry.isDirectory) target.mkdirs() else {
                        require(entry.size < 0 || entry.size <= 16 * 1024 * 1024) { "Arquivo ZIP excede 16 MiB" }
                        target.parentFile?.mkdirs()
                        totalBytes += target.outputStream().buffered().use { output -> SafeFiles.copyBounded(zip, output, 16 * 1024 * 1024) }
                        require(totalBytes <= 256L * 1024 * 1024) { "Projeto ZIP excede 256 MiB descompactados" }
                    }
                    zip.closeEntry()
                }
            }
            val nested = staging.walkTopDown().firstOrNull { it.isDirectory && File(it, "app/src/main/AndroidManifest.xml").isFile }
                ?: error("ZIP não contém um projeto Android válido")
            val finalRoot = SafeFiles.resolve(workspace, "Importado-${System.currentTimeMillis()}")
            if (nested.canonicalFile == staging.canonicalFile) {
                require(staging.renameTo(finalRoot)) { "Não foi possível concluir a importação" }
            } else {
                require(nested.renameTo(finalRoot)) { "Não foi possível concluir a importação" }
                staging.deleteRecursively()
            }
            return listProjects().first { it.root == finalRoot }
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }

    private fun safeProject(project: File): File {
        val base = workspace.canonicalFile; val target = project.canonicalFile
        require(target.parentFile == base && target.isDirectory) { "Projeto fora do workspace" }
        return target
    }

    private fun appGradle(packageName: String, compose: Boolean) = """
        plugins { id("com.android.application"); id("org.jetbrains.kotlin.android")${if (compose) "; id(\"org.jetbrains.kotlin.plugin.compose\")" else ""} }
        android { namespace = "$packageName"; compileSdk = 35; defaultConfig { applicationId = "$packageName"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }; buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }; buildFeatures { compose = $compose }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
        dependencies { implementation("androidx.core:core-ktx:1.16.0"); ${if (compose) "implementation(platform(\"androidx.compose:compose-bom:2025.05.01\")); implementation(\"androidx.activity:activity-compose:1.10.1\"); implementation(\"androidx.compose.ui:ui\"); implementation(\"androidx.compose.material3:material3\")" else "implementation(\"androidx.appcompat:appcompat:1.7.1\")"} }
    """.trimIndent()

    private fun mainActivity(packageName: String, name: String, compose: Boolean) = if (compose) """
        package $packageName
        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.Text
        class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { Text("Olá, $name!") } } } }
    """.trimIndent() else """
        package $packageName
        import android.app.Activity
        import android.os.Bundle
        import android.widget.TextView
        class MainActivity : Activity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(TextView(this).apply { text = "Olá, $name!"; textSize = 24f }) } }
    """.trimIndent()
}
