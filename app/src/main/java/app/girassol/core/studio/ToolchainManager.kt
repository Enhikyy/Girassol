package app.girassol.core.studio

import android.content.Context
import android.content.Intent
import android.system.Os
import androidx.core.content.FileProvider
import app.girassol.core.activity.ActivityHub
import app.girassol.core.activity.ActivityKind
import app.girassol.core.data.SafeFiles
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.json.JSONObject

enum class ToolchainStatus { NOT_INSTALLED, INSTALLING, READY, ERROR }
data class BuildResult(val successful: Boolean, val apk: File?, val output: String)
data class ToolchainUiState(val status: ToolchainStatus = ToolchainStatus.NOT_INSTALLED, val detail: String = "Toolchain local não instalado", val logs: List<String> = emptyList())

/**
 * Private, non-root Android build environment. It installs the official Termux
 * bootstrap inside this app's sandbox, then JDK/Gradle/SDK and an ARM64 AAPT2.
 * No Termux application, external server or fake build progress is required.
 */
class ToolchainManager(private val context: Context, private val activities: ActivityHub) {
    private val root = File(context.filesDir, "toolchain")
    private val prefix = File(root, "usr")
    private val home = File(root, "home")
    private val sdk = File(home, "android-sdk")
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val activeProcess = AtomicReference<Process?>()
    private val mutableState = MutableStateFlow(ToolchainUiState())
    val state: StateFlow<ToolchainUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val ready = File(prefix, "bin/java").canExecute() && File(prefix, "bin/gradle").isFile &&
            File(sdk, "platforms/android-35/android.jar").isFile && File(sdk, "build-tools/35.0.0/aapt2").canExecute()
        mutableState.value = if (ready) ToolchainUiState(ToolchainStatus.READY, "JDK, Gradle, SDK 35 e AAPT2 ARM64 prontos", mutableState.value.logs)
        else if (mutableState.value.status != ToolchainStatus.INSTALLING) ToolchainUiState(ToolchainStatus.NOT_INSTALLED, "Instalação inicial necessária · ARM64 · aproximadamente 3 GB livres", mutableState.value.logs)
        else mutableState.value
    }

    suspend fun install() = withContext(Dispatchers.IO) {
        check(android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "O toolchain AAPT2 fornecido exige um aparelho ARM64" }
        check(context.filesDir.usableSpace > 3L * 1024 * 1024 * 1024) { "Reserve pelo menos 3 GB de armazenamento livre" }
        val operation = activities.begin(ActivityKind.BUILD, "Preparando o Studio", "Instalando toolchain privado")
        mutableState.value = ToolchainUiState(ToolchainStatus.INSTALLING, "Baixando ambiente base")
        try {
            root.mkdirs(); home.mkdirs()
            val bootstrap = download(BOOTSTRAP_URL, BOOTSTRAP_SHA256, "bootstrap-aarch64.zip")
            log("Bootstrap verificado; extraindo ambiente privado")
            activities.progress(operation, 0.15f, "Extraindo ambiente base")
            extractBootstrap(bootstrap, root); bootstrap.delete()
            File(prefix, "etc/apt/sources.list").apply { parentFile?.mkdirs(); if (!exists()) writeText("deb https://packages-cf.termux.dev/apt/termux-main stable main\n") }
            activities.progress(operation, 0.3f, "Instalando JDK e Gradle")
            mutableState.value = mutableState.value.copy(detail = "Instalando JDK, Gradle e utilitários")
            runShell("apt-get update -y && apt-get install -y openjdk-21 aapt2 d8 apksigner git curl unzip zip", 30 * 60_000L)
            installGradle()

            activities.progress(operation, 0.58f, "Instalando Android SDK 35")
            mutableState.value = mutableState.value.copy(detail = "Instalando Android SDK 35")
            installSdk()

            activities.progress(operation, 0.82f, "Configurando AAPT2 nativo ARM64")
            installAapt2()
            val gradleHome = File(home, ".gradle").apply { mkdirs() }
            File(gradleHome, "gradle.properties").writeText("android.aapt2FromMavenOverride=${File(sdk, "build-tools/35.0.0/aapt2").absolutePath}\norg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8\norg.gradle.daemon=false\n")
            activities.progress(operation, 0.96f, "Validando ferramentas")
            doctor()
            refresh()
            check(mutableState.value.status == ToolchainStatus.READY) { "A verificação final do toolchain falhou" }
            activities.complete(operation, "Toolchain local validado")
        } catch (failure: Throwable) {
            mutableState.value = mutableState.value.copy(status = ToolchainStatus.ERROR, detail = failure.message ?: "Falha na instalação")
            activities.fail(operation, failure.message ?: "Falha ao preparar toolchain")
            throw failure
        }
    }

    suspend fun build(project: File): BuildResult = withContext(Dispatchers.IO) {
        refresh()
        check(mutableState.value.status == ToolchainStatus.READY) { "Instale e valide o toolchain antes de compilar" }
        val operation = activities.begin(ActivityKind.BUILD, "Compilando ${project.name}", "Gradle :app:assembleDebug")
        val output = StringBuilder()
        try {
            File(project, "local.properties").writeText("sdk.dir=${sdk.absolutePath}\n")
            val command = "gradle :app:assembleDebug --no-daemon --stacktrace --console=plain"
            val process = startShell(command, project)
            val reader = Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line); log(line)
                        if (line.contains("FAILED", true) || line.contains("BUILD SUCCESSFUL", true)) {
                            activities.progress(operation, 0.85f, line.take(240))
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val finished = process.waitFor(30, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly(); reader.join(2_000)
                error("Build excedeu 30 minutos e foi interrompido")
            }
            reader.join(5_000)
            val exit = process.exitValue()
            val apk = File(project, "app/build/outputs/apk/debug").listFiles().orEmpty().filter { it.extension == "apk" }.maxByOrNull { it.lastModified() }
            val success = exit == 0 && apk?.isFile == true
            val text = output.toString().takeLast(160_000)
            if (success) activities.complete(operation, "APK gerado: ${apk?.name}")
            else {
                val detail = parseBuildError(text)
                activities.fail(operation, detail)
                val diagnosis = activities.begin(ActivityKind.DIAGNOSIS, "Investigando falha de build", detail)
                activities.fail(diagnosis, "Revise o log e execute o diagnóstico do projeto")
            }
            BuildResult(success, if (success) apk else null, text)
        } catch (failure: Throwable) {
            activities.fail(operation, failure.message ?: "Build interrompido")
            throw failure
        } finally { activeProcess.set(null) }
    }

    suspend fun test(project: File): BuildResult = withContext(Dispatchers.IO) {
        refresh()
        check(mutableState.value.status == ToolchainStatus.READY) { "Instale e valide o toolchain antes de testar" }
        val operation = activities.begin(ActivityKind.DIAGNOSIS, "Auto-teste no Consultório", "Gradle :app:testDebugUnitTest")
        try {
            File(project, "local.properties").writeText("sdk.dir=${sdk.absolutePath}\n")
            val output = runShell("gradle :app:testDebugUnitTest --no-daemon --stacktrace --console=plain", 20 * 60_000L, project)
            activities.complete(operation, "Testes unitários concluídos")
            BuildResult(true, null, output)
        } catch (failure: Throwable) {
            val detail = failure.message ?: "Falha nos testes"
            activities.fail(operation, detail)
            BuildResult(false, null, detail)
        } finally { activeProcess.set(null) }
    }

    fun cancel() {
        activeProcess.get()?.destroy()
        Thread.sleep(150)
        activeProcess.get()?.takeIf { it.isAlive }?.destroyForcibly()
    }

    fun installIntent(apk: File): Intent {
        require(apk.isFile && apk.extension == "apk") { "APK não encontrado" }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun installGradle() {
        val archive = download(GRADLE_URL, GRADLE_SHA256, "gradle-8.11.1-bin.zip")
        val distribution = File(root, "gradle-8.11.1")
        extractPlainZip(archive, distribution, stripTopDirectory = true)
        archive.delete()
        val launcher = File(distribution, "bin/gradle")
        check(launcher.isFile) { "Gradle 8.11.1 ausente após extração" }
        launcher.setExecutable(true)
        val command = File(prefix, "bin/gradle")
        command.writeText("#!${File(prefix, "bin/bash").absolutePath}\nexec '${launcher.absolutePath}' \"$@\"\n")
        command.setExecutable(true)
    }

    private fun installSdk() {
        val archive = download(CMDLINE_URL, CMDLINE_SHA256, "android-commandline.zip")
        val tools = File(sdk, "cmdline-tools/latest").apply { mkdirs() }
        extractPlainZip(archive, tools, stripTopDirectory = true); archive.delete()
        val sdkmanager = File(tools, "bin/sdkmanager")
        check(sdkmanager.isFile) { "sdkmanager ausente após extração" }
        File(sdk, "licenses").mkdirs()
        File(sdk, "licenses/android-sdk-license").writeText("24333f8a63b6825ea9c5514f83c2829b004d1fee\n84831b9409646a918e30573bab4c9c91346d8abd\n")
        val escaped = sdkmanager.absolutePath.replace("'", "'\\''")
        runShell("yes | bash '$escaped' --sdk_root='${sdk.absolutePath}' 'platforms;android-35' 'build-tools;35.0.0'", 20 * 60_000L)
    }

    private fun installAapt2() {
        val binary = download(AAPT2_URL, AAPT2_SHA256, "aapt2-aarch64")
        val target = File(sdk, "build-tools/35.0.0/aapt2")
        binary.copyTo(target, overwrite = true); target.setExecutable(true)
        val standalone = File(prefix, "bin/aapt2-aosp")
        binary.copyTo(standalone, overwrite = true); standalone.setExecutable(true); binary.delete()
        val version = runShell("'${target.absolutePath}' version", 30_000L)
        log("AAPT2: $version")
    }

    private fun doctor() {
        runShell("java -version && gradle --version && aapt2-aosp version", 60_000L)
    }

    private fun download(url: String, sha256: String, name: String): File {
        val target = File(context.cacheDir, name)
        log("Baixando $name")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download falhou: HTTP ${response.code}" }
            response.body?.byteStream()?.use { input -> target.outputStream().buffered().use { output -> SafeFiles.copyBounded(input, output, 2L * 1024 * 1024 * 1024) } }
                ?: error("Resposta vazia")
        }
        val actual = sha256(target)
        check(actual.equals(sha256, true)) { "Integridade de $name não confere" }
        return target
    }

    private fun extractBootstrap(archive: File, destination: File) {
        val rootPath = destination.canonicalPath
        ZipArchiveInputStream(archive.inputStream().buffered()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextZipEntry ?: break
                require(++count <= 20_000) { "Bootstrap possui entradas demais" }
                val target = File(destination, entry.name).canonicalFile
                require(target.path == rootPath || target.path.startsWith(rootPath + File.separator)) { "Entrada fora do ambiente privado" }
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isUnixSymlink -> {
                        val linkTarget = zip.readBytes().toString(Charsets.UTF_8)
                        require(!linkTarget.startsWith('/')) { "Link absoluto não permitido no bootstrap" }
                        val resolved = File(target.parentFile, linkTarget).canonicalFile
                        require(resolved.path.startsWith(rootPath + File.separator)) { "Link sai do ambiente privado" }
                        target.parentFile?.mkdirs(); target.delete(); Os.symlink(linkTarget, target.path)
                    }
                    else -> {
                        target.parentFile?.mkdirs(); target.outputStream().buffered().use { output -> zip.copyTo(output) }
                        if (entry.unixMode > 0) Os.chmod(target.path, entry.unixMode and 511)
                    }
                }
            }
        }
        File(prefix, "bin/bash").setExecutable(true)
    }

    private fun extractPlainZip(archive: File, destination: File, stripTopDirectory: Boolean) {
        java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = if (stripTopDirectory) entry.name.substringAfter('/', "") else entry.name
                if (name.isBlank()) { zip.closeEntry(); continue }
                val target = SafeFiles.resolve(destination, name)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs(); target.outputStream().buffered().use { output -> SafeFiles.copyBounded(zip, output, 512L * 1024 * 1024) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun runShell(command: String, timeoutMs: Long, directory: File = root): String {
        val process = startShell(command, directory)
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.appendLine(it); log(it) } }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) { process.destroyForcibly(); reader.join(2_000); error("Comando excedeu o tempo limite") }
        reader.join(5_000)
        val text = output.toString()
        check(process.exitValue() == 0) { "Comando falhou (${process.exitValue()}): ${text.takeLast(1800)}" }
        return text.takeLast(4000)
    }

    private fun startShell(command: String, directory: File): Process {
        val builder = ProcessBuilder(File(prefix, "bin/bash").absolutePath, "-c", command)
            .directory(directory).redirectErrorStream(true)
        builder.environment().putAll(environment())
        return builder.start().also(activeProcess::set)
    }

    private fun environment(): MutableMap<String, String> {
        val javaHome = File(prefix, "lib/jvm").listFiles().orEmpty().firstOrNull { File(it, "bin/java").isFile }
        val map = HashMap<String, String>(System.getenv())
        map["HOME"] = home.absolutePath; map["PREFIX"] = prefix.absolutePath; map["TERMUX_PREFIX"] = prefix.absolutePath
        map["PATH"] = "${prefix.absolutePath}/bin:${sdk.absolutePath}/cmdline-tools/latest/bin:${System.getenv("PATH")}"
        map["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
        map["ANDROID_HOME"] = sdk.absolutePath; map["ANDROID_SDK_ROOT"] = sdk.absolutePath
        map["ANDROID_DATA"] = "/data"; map["ANDROID_ROOT"] = "/system"; map["LANG"] = "en_US.UTF-8"
        map["GRADLE_USER_HOME"] = File(home, ".gradle").absolutePath
        if (javaHome != null) map["JAVA_HOME"] = javaHome.absolutePath
        return map
    }

    private fun log(line: String) {
        val lines = (mutableState.value.logs + line).takeLast(500)
        mutableState.value = mutableState.value.copy(logs = lines)
    }

    private fun parseBuildError(output: String): String = output.lines().filter { it.contains("error:", true) || it.contains("FAILURE:", true) || it.contains("What went wrong", true) }.take(4).joinToString(" | ").ifBlank { "Gradle falhou; consulte o log completo" }.take(900)
    private fun sha256(file: File): String { val digest = MessageDigest.getInstance("SHA-256"); file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }; return digest.digest().joinToString("") { "%02x".format(it) } }

    private companion object {
        const val BOOTSTRAP_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.11.01-r1%2Bapt.android-7/bootstrap-aarch64.zip"
        const val BOOTSTRAP_SHA256 = "5a1429b6c68ee3bdc17b5fd6e47ba42933ec924cd7283fe74992574e1d5badae"
        const val GRADLE_URL = "https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip"
        const val GRADLE_SHA256 = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
        const val CMDLINE_URL = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        const val CMDLINE_SHA256 = "2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258"
        const val AAPT2_URL = "https://raw.githubusercontent.com/poordevcode/termux-android-studio/76652ef44d86baa1c42957da5e7e5bbaaf95956d/prebuilt/aapt2-aarch64"
        const val AAPT2_SHA256 = "b859b41f107939c743fd96065d125345d6829fcd4edc9bb6aabbbb63df93e4a0"
    }
}
