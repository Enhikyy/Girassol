package dev.labforge.app

import dev.labforge.app.core.studio.WorkspaceFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class WorkspaceFilesTest {
    @Test fun rejectsTraversalAndAbsoluteWorkspacePaths() {
        val root = createTempDirectory(prefix = "girassol-workspace-").toFile()
        try {
            assertTrue(runCatching { WorkspaceFiles.resolve(root, "../outside.txt") }.isFailure)
            assertTrue(runCatching { WorkspaceFiles.resolve(root, "/outside.txt") }.isFailure)
            assertTrue(runCatching { WorkspaceFiles.resolve(root, "C:/outside.txt") }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun importsNestedProjectZipInsideWorkspace() {
        val root = createTempDirectory(prefix = "girassol-zip-").toFile()
        try {
            val bytes = zipOf("app/src/MainActivity.kt" to "class MainActivity")
            val imported = WorkspaceFiles.importZip(ByteArrayInputStream(bytes), root)
            assertTrue(imported.canonicalPath.startsWith(root.canonicalPath + File.separator))
            assertEquals("class MainActivity", File(imported, "app/src/MainActivity.kt").readText())
            assertEquals("app/src/MainActivity.kt", WorkspaceFiles.findSourceFile(imported))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsZipSlipAndCleansPartialExtraction() {
        val root = createTempDirectory(prefix = "girassol-zip-slip-").toFile()
        try {
            val bytes = zipOf("../escape.txt" to "no")
            assertTrue(runCatching { WorkspaceFiles.importZip(ByteArrayInputStream(bytes), root) }.isFailure)
            assertFalse(File(root, "escape.txt").exists())
            assertFalse(File(root, "imports").listFiles()?.any { it.exists() } == true)
        } finally { root.deleteRecursively() }
    }


    @Test fun fileIndexSkipsBuildAndGitInternals() {
        val root = kotlin.io.path.createTempDirectory(prefix = "girassol-file-index-").toFile()
        try {
            File(root, "src/Main.kt").apply { parentFile?.mkdirs(); writeText("main") }
            File(root, ".git/config").apply { parentFile?.mkdirs(); writeText("secret") }
            File(root, "build/intermediates/classes.bin").apply { parentFile?.mkdirs(); writeText("build") }
            val files=WorkspaceFiles.listFiles(root)
            assertEquals(listOf("src/Main.kt"),files)
        } finally { root.deleteRecursively() }
    }

    @Test fun boundedCopyStopsBeforeWritingPastLimit() {
        val output = ByteArrayOutputStream()
        val result = runCatching { WorkspaceFiles.copyBounded(ByteArrayInputStream(ByteArray(128)), output, 64) }
        assertTrue(result.isFailure)
        assertEquals(0,output.size())
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
