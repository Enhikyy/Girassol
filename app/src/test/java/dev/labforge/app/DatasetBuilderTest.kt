package dev.labforge.app

import dev.labforge.app.core.train.ChatFormat
import dev.labforge.app.core.train.DatasetBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatasetBuilderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun plainTextIsCopiedWithoutChangingItsContents() {
        val source = temporaryFolder.newFile("corpus.txt")
        val output = FileCompat.child(temporaryFolder.root, "dataset.txt")
        val original = "Treino local offline.\n".repeat(120_000)
        source.writeText(original)

        val (built, stats) = DatasetBuilder.build(listOf(source), ChatFormat.RAW, output)

        assertEquals(output.canonicalFile, built.canonicalFile)
        assertEquals(original, built.readText())
        assertEquals(original.length.toLong(), stats.characters)
        assertEquals(1, stats.documents)
        assertEquals(0, stats.skippedLines)
    }

    @Test
    fun oversizedJsonlRecordIsSkippedAndFollowingRecordsStillImport() {
        val source = temporaryFolder.newFile("corpus.jsonl")
        val output = FileCompat.child(temporaryFolder.root, "dataset.txt")
        val tooLarge = "x".repeat(1_048_600)
        source.writeText(
            """{"instruction":"primeira","output":"ok"}""" + "\n" +
                """{"instruction":"ignorar","output":"$tooLarge"}""" + "\n" +
                """{"instruction":"terceira","output":"continua"}""" + "\n",
        )

        val (built, stats) = DatasetBuilder.build(listOf(source), ChatFormat.CHATML, output)
        val result = built.readText()

        assertTrue(result.contains("primeira"))
        assertTrue(result.contains("terceira"))
        assertFalse(result.contains("ignorar"))
        assertEquals(2, stats.documents)
        assertEquals(1, stats.skippedLines)
        assertEquals("instruction", stats.detectedFormat)
    }

    @Test
    fun failedImportDoesNotReplacePreviousOutput() {
        val output = temporaryFolder.newFile("dataset.txt")
        output.writeText("dataset válido anterior")
        val missing = FileCompat.child(temporaryFolder.root, "missing.txt")

        val error = runCatching {
            DatasetBuilder.build(listOf(missing), ChatFormat.RAW, output)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("dataset válido anterior", output.readText())
        assertFalse(FileCompat.child(temporaryFolder.root, "dataset.txt.tmp").exists())
    }
}

/** Keeps test file construction readable without relying on Android Context. */
private object FileCompat {
    fun child(parent: java.io.File, name: String) = java.io.File(parent, name)
}
