plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

println("::notice::Girassol 3.0: configuração Gradle iniciada")

if (System.getenv("GITHUB_ACTIONS") == "true" &&
    rootDir.resolve(".ci/source-checkpoint").isDirectory
) {
    val log = java.io.File("/tmp/girassol-bootstrap-output.log")
    val process = ProcessBuilder(
        "bash",
        rootDir.resolve("scripts/bootstrap-actions.sh").absolutePath,
    ).redirectErrorStream(true).redirectOutput(log).start()
    val status = process.waitFor()
    println(log.readText())
    check(status == 0) { "Bootstrap nativo falhou com código $status" }
}

gradle.projectsEvaluated {
    val appProject = rootProject.findProject(":app") ?: return@projectsEvaluated
    appProject.tasks.matching { it.name == "assembleDebug" }.configureEach {
        doLast {
            val log = java.io.File("/tmp/girassol-audit-output.log")
            val process = ProcessBuilder(
                "bash",
                rootDir.resolve("scripts/audit-apk.sh").absolutePath,
            ).redirectErrorStream(true).redirectOutput(log).start()
            val status = process.waitFor()
            println(log.readText())
            check(status == 0) { "Auditoria do APK falhou com código $status" }
        }
    }
    appProject.tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
        doLast {
            val log = java.io.File("/tmp/girassol-evidence-output.log")
            val process = ProcessBuilder(
                "bash",
                rootDir.resolve("scripts/collect-test-evidence.sh").absolutePath,
            ).redirectErrorStream(true).redirectOutput(log).start()
            val status = process.waitFor()
            println(log.readText())
            check(status == 0) { "Coleta das evidências falhou com código $status" }
        }
    }
}
