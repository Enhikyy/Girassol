package app.girassol

import app.girassol.core.studio.ProjectRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun createsEditsExportsAndImportsAndroidProject() {
        val repository = ProjectRepository(folder.newFolder("workspace"))
        val project = repository.createProject("Teste Local", "com.exemplo.teste")
        val main = project.root.toPath().resolve("app/src/main/java/com/exemplo/teste/MainActivity.kt").toFile()
        assertTrue(main.isFile)
        val path = "app/src/main/java/com/exemplo/teste/MainActivity.kt"
        repository.write(project.root, path, repository.read(project.root, path).replace("Olá", "Oi"))
        assertTrue(repository.read(project.root, path).contains("Oi, Teste Local!"))
        val zip = ByteArrayOutputStream()
        repository.export(project.root, zip)
        val imported = repository.import(ByteArrayInputStream(zip.toByteArray()))
        assertTrue(imported.root.isDirectory)
        assertTrue(repository.read(imported.root, path).contains("Oi, Teste Local!"))
        assertEquals(2, repository.listProjects().size)
    }
}
