package dev.labforge.app

import dev.labforge.app.core.studio.AndroidProjectScaffold
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AndroidProjectScaffoldTest {
    @Test fun createsPortableAndroidProjectFiles() {
        val root=createTempDirectory(prefix="girassol-studio-").toFile()
        try {
            val files=AndroidProjectScaffold.create(root)
            assertEquals(6,files.size)
            assertTrue(File(root,"settings.gradle.kts").isFile)
            assertTrue(File(root,"app/build.gradle.kts").readText().contains("com.android.application"))
            assertTrue(File(root,"app/src/main/AndroidManifest.xml").readText().contains(".MainActivity"))
            assertTrue(root.walkTopDown().any{it.name=="MainActivity.kt"})
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsInvalidPackageNames() {
        val root=createTempDirectory(prefix="girassol-studio-invalid-").toFile()
        try {
            val error=runCatching{AndroidProjectScaffold.create(root,"bad package")}
            assertTrue(error.isFailure)
        } finally { root.deleteRecursively() }
    }
}
