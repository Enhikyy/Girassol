package app.girassol

import app.girassol.core.activity.ActivityHub
import app.girassol.core.activity.ActivityKind
import app.girassol.core.diagnostics.CodeAnalyzer
import app.girassol.core.studio.ProjectRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CodeAnalyzerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun findsAndFixesOnlyDeterministicWhitespace() {
        val projects = ProjectRepository(folder.newFolder("workspace"))
        val hub = ActivityHub(File(folder.root, "history.json"))
        val project = projects.createProject("Diagnostico", "com.exemplo.diagnostico")
        val path = "app/src/main/java/com/exemplo/diagnostico/MainActivity.kt"
        projects.write(
            project.root,
            path,
            "class MainActivity {   \n    // TODO revisar\n    val literal = \"{ chaves em texto }\"\n}\n"
        )
        val analyzer = CodeAnalyzer(projects, hub)
        val issues = analyzer.analyze(project.root)
        val trailing = issues.single { it.code == "TRAILING_SPACE" }
        assertTrue(issues.any { it.code == "PENDING" })
        assertFalse(issues.any { it.code == "BRACES" })
        analyzer.applySafeFix(project.root, trailing)
        assertFalse(projects.read(project.root, path).lines()[0].endsWith(" "))
        assertTrue(projects.read(project.root, path).contains("TODO"))
        assertEquals(ActivityKind.DIAGNOSIS, hub.state.value.recentEvents.last().kind)
    }
}
