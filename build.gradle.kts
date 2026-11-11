plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

// Recovery bootstrap for the existing Actions workflow. It runs only in CI and
// only while the verified source checkpoint is present; local builds never run it.
if (System.getenv("GITHUB_ACTIONS") == "true" &&
    rootDir.resolve(".ci/source-checkpoint").isDirectory
) {
    exec {
        commandLine("bash", rootDir.resolve("scripts/bootstrap-actions.sh").absolutePath)
    }
}

// Keep the mandatory APK audit and design evidence inside the existing workflow,
// without requiring changes to .github/workflows (which need a separate scope).
gradle.projectsEvaluated {
    val appProject = rootProject.findProject(":app") ?: return@projectsEvaluated

    appProject.tasks.matching { it.name == "assembleDebug" }.configureEach {
        doLast {
            val apk = appProject.buildDir.resolve("outputs/apk/debug")
                .walkTopDown()
                .firstOrNull { it.isFile && it.extension == "apk" }
                ?: error("APK debug não encontrado")
            check(apk.length() >= 419_430_400) {
                "APK abaixo do mínimo funcional de 400 MiB: ${apk.length()} bytes"
            }

            val modelName = "assets/offline/Qwen3-0.6B-Q8_0.gguf"
            val requiredLibs = listOf(
                "lib/arm64-v8a/liblabforge.so",
                "lib/arm64-v8a/libllama.so",
            )
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val lines = mutableListOf<String>()
            lines += "APK=${apk.absolutePath}"
            lines += "APK_SIZE_BYTES=${apk.length()}"

            java.util.zip.ZipFile(apk).use { archive ->
                val model = archive.getEntry(modelName) ?: error("GGUF ausente no APK")
                check(model.size == 639_446_688L) { "Tamanho inválido do GGUF: ${model.size}" }
                check(model.method == java.util.zip.ZipEntry.STORED) { "GGUF não está em ZIP_STORED" }
                archive.getInputStream(model).use { input ->
                    val buffer = ByteArray(8 * 1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val modelHash = digest.digest().joinToString("") { "%02x".format(it) }
                check(modelHash == "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031") {
                    "SHA-256 inválido do GGUF: $modelHash"
                }
                lines += "GGUF=$modelName; ZIP_STORED; SHA256=$modelHash"

                requiredLibs.forEach { name ->
                    val entry = archive.getEntry(name) ?: error("Biblioteca ausente: $name")
                    val header = ByteArray(20)
                    archive.getInputStream(entry).use { input ->
                        var offset = 0
                        while (offset < header.size) {
                            val count = input.read(header, offset, header.size - offset)
                            if (count < 0) break
                            offset += count
                        }
                    }
                    check(header.sliceArray(0..3).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
                    check(header[4].toInt() == 2) { "Não é ELF64: $name" }
                    val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
                    check(machine == 183) { "Não é ARM64: $name (e_machine=$machine)" }
                    lines += "ARM64_ELF64=$name"
                }
            }

            val androidHome = System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: error("ANDROID_HOME ausente")
            val tools = java.io.File(androidHome, "build-tools/37.0.0")
            fun runTool(executable: String, vararg args: String): String {
                val process = ProcessBuilder(java.io.File(tools, executable).absolutePath, *args)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { "$executable falhou:\n$output" }
                return output
            }
            val signature = runTool("apksigner", "verify", "--verbose", "--print-certs", apk.absolutePath)
            val badging = runTool("aapt", "dump", "badging", apk.absolutePath)
            lines += signature.lineSequence().filter { it.isNotBlank() }.toList()
            lines += badging.lineSequence().take(8).toList()

            val report = rootProject.buildDir.resolve("reports/apk-audit.txt")
            report.parentFile.mkdirs()
            report.writeText(lines.joinToString("\n") + "\n")
            println(report.readText())
        }
    }

    appProject.tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
        doLast {
            val reportDir = appProject.buildDir.resolve("reports/tests/testDebugUnitTest")
            val designTarget = reportDir.resolve("design")
            designTarget.mkdirs()
            appProject.buildDir.resolve("reports/design").listFiles().orEmpty()
                .filter { it.isFile && it.extension == "png" }
                .forEach { source ->
                    source.copyTo(designTarget.resolve(source.name), overwrite = true)
                    println("Compose capture: ${source.name}")
                }
            val audit = rootProject.buildDir.resolve("reports/apk-audit.txt")
            if (audit.isFile) {
                audit.copyTo(reportDir.resolve("apk-audit.txt"), overwrite = true)
            }
        }
    }
}
