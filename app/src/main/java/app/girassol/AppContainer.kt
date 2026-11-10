package app.girassol

import android.content.Context
import app.girassol.core.activity.ActivityHub
import app.girassol.core.ai.LocalLlmEngine
import app.girassol.core.ai.ModelStore
import app.girassol.core.chat.ChatRepository
import app.girassol.core.data.SettingsRepository
import app.girassol.core.diagnostics.CodeAnalyzer
import app.girassol.core.learning.MemoryEngine
import app.girassol.core.research.ResearchEngine
import app.girassol.core.studio.ProjectRepository
import app.girassol.core.studio.ToolchainManager
import java.io.File

/** Dependency graph owned by the process. Every surface observes the same engines. */
class AppContainer(context: Context) {
    internal val appContext = context.applicationContext
    private val stateDirectory = File(appContext.filesDir, "state").apply { mkdirs() }

    val activities = ActivityHub(File(stateDirectory, "activity-history.json"))
    val settings = SettingsRepository(appContext)
    val models = ModelStore(appContext)
    val llm = LocalLlmEngine()
    val chat = ChatRepository(File(stateDirectory, "chat.json"))
    val research = ResearchEngine(File(appContext.filesDir, "research"), activities)
    val memory = MemoryEngine(File(stateDirectory, "memory.json"), activities)
    val projects = ProjectRepository(File(appContext.filesDir, "workspace"))
    val analyzer = CodeAnalyzer(projects, activities)
    val toolchain = ToolchainManager(appContext, activities)
}
