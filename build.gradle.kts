plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

println("::notice::Girassol 3.0: configuração Gradle iniciada")

if (System.getenv("GITHUB_ACTIONS") == "true" &&
    rootDir.resolve(".ci/source-checkpoint").isDirectory
) {
    exec {
        commandLine("bash", rootDir.resolve("scripts/bootstrap-actions.sh").absolutePath)
    }
}

gradle.projectsEvaluated {
    val appProject = rootProject.findProject(":app") ?: return@projectsEvaluated
    appProject.tasks.matching { it.name == "assembleDebug" }.configureEach {
        doLast {
            exec {
                commandLine("bash", rootDir.resolve("scripts/audit-apk.sh").absolutePath)
            }
        }
    }
    appProject.tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
        doLast {
            exec {
                commandLine("bash", rootDir.resolve("scripts/collect-test-evidence.sh").absolutePath)
            }
        }
    }
}
