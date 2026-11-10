package app.girassol

import app.girassol.core.data.SafeFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun rejectsTraversalAndAbsolutePaths() {
        val root = folder.newFolder("workspace")
        assertThrows(IllegalArgumentException::class.java) { SafeFiles.resolve(root, "../secret") }
        assertThrows(IllegalArgumentException::class.java) { SafeFiles.resolve(root, "/etc/passwd") }
        val valid = SafeFiles.resolve(root, "app/src/Main.kt")
        assertEquals(root.canonicalFile, valid.parentFile?.parentFile?.parentFile?.canonicalFile)
    }

    @Test fun boundedCopyRejectsOverflow() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            SafeFiles.copyBounded(ByteArrayInputStream(ByteArray(32)), output, 31)
        }
    }
}
