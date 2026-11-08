package dev.labforge.app

import dev.labforge.app.core.log.DiagnosticLogStore
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DiagnosticLogStoreTest {
    @Test
    fun writesStructuredRecordsAndReturnsNewestTail() {
        val directory = Files.createTempDirectory("girassol-log-").toFile()
        try {
            val log = DiagnosticLogStore(directory)
            log.append("INFO", "CHAT", "started")
            log.append("ERROR", "NATIVE", "decode failed")
            val tail = log.tail(1)
            assertTrue(tail.contains("ERROR"))
            assertTrue(tail.contains("NATIVE"))
            assertTrue(tail.contains("decode failed"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rotatesFilesWithoutDroppingNewestEntry() {
        val directory = Files.createTempDirectory("girassol-log-rotate-").toFile()
        try {
            val log = DiagnosticLogStore(directory, maxBytes = 100)
            repeat(8) { log.append("INFO", "TEST", "entry-" + it + "-" + "x".repeat(48)) }
            assertTrue(File(directory, "girassol.log.1").exists())
            assertTrue(log.tail(100).contains("entry-7"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
