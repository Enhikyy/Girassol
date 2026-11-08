package dev.labforge.app.core.evolution

import dev.labforge.app.core.train.ChatFormat
import dev.labforge.app.core.train.DatasetBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EvolutionTest {
    @Test fun benchmarkCannotBeAppliedToAChangedModelOrContext() {
        val file = Files.createTempFile("model", ".gguf").toFile()
        try {
            file.writeText("original")
            val cfg = dev.labforge.app.core.settings.AppSettings(localModelPath = file.absolutePath)
            val report = JSONObject().put("version", 1).put("model", file.canonicalPath).put("bytes", file.length()).put("modified", file.lastModified())
                .put("adapter", cfg.localAdapterPath).put("context", cfg.localContext).put("adapterFiles", InferenceStudy.adaptersStamp(cfg.localAdapterPath))
            assertTrue(InferenceStudy.matches(report, cfg))
            assertFalse(InferenceStudy.matches(report, cfg.copy(localContext = 4096)))
            val adapter = file.parentFile!!.resolve("adapter-${file.name}.gguf").apply { writeText("one") }
            try {
                val adapted = cfg.copy(localAdapterPath = adapter.absolutePath)
                report.put("adapter", adapted.localAdapterPath).put("adapterFiles", InferenceStudy.adaptersStamp(adapted.localAdapterPath))
                assertTrue(InferenceStudy.matches(report, adapted))
                adapter.writeText("new adapter")
                assertFalse(InferenceStudy.matches(report, adapted))
            } finally { adapter.delete() }
            report.put("adapter", "").put("adapterFiles", InferenceStudy.adaptersStamp(""))
            assertTrue(InferenceStudy.matches(report, cfg))
            file.writeText("different model contents")
            assertFalse(InferenceStudy.matches(report, cfg))
        } finally { file.delete() }
    }

    @Test fun recoveryRequiresTheSameArgumentsAndSurvivesReload() {
        val dir = Files.createTempDirectory("evidence").toFile()
        try {
            val first = EvidenceJournal(dir)
            first.record("android_build", JSONObject("""{"action":"test","project":"/project"}"""), false, 2)
            assertFalse(first.record("android_build", JSONObject("""{"action":"build","project":"/project"}"""), true, 2).recovered)
            val recovered = EvidenceJournal(dir).record("android_build", JSONObject("""{"project":"/project","action":"test"}"""), true, 2)
            assertTrue(recovered.recovered)
            assertFalse(first.record("android_build", JSONObject("""{"action":"test","project":"/project"}"""), true, 2).recovered)
            assertFalse(dir.resolve("execution-evidence.json").readText().contains("/project"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun diagnosticsDoNotCountAsBuildEvidenceAndDependenciesGuidePractice() {
        val dir = Files.createTempDirectory("skills").toFile()
        try {
            val journal = EvidenceJournal(dir)
            journal.record("android_build", JSONObject().put("action", "diagnose"), true, 1)
            journal.record("write_file", JSONObject(), true, 1)
            assertFalse(SkillTree.progress(journal.all()).single { it.skill.id == "verify" }.available)
            journal.record("read_file", JSONObject(), true, 1)
            val progress = SkillTree.progress(journal.all())
            assertEquals(0, progress.single { it.skill.id == "verify" }.successes)
            assertTrue(progress.single { it.skill.id == "edit" }.available)
            assertTrue(progress.single { it.skill.id == "verify" }.available)
            assertEquals("verify", SkillTree.next(journal.all())!!.skill.id)
        } finally { dir.deleteRecursively() }
    }
    @Test fun concurrentJournalsRetainAllObservationsAndCorruptionIsNotOverwritten() {
        val dir = Files.createTempDirectory("journal").toFile()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val journals = listOf(EvidenceJournal(dir), EvidenceJournal(dir))
            (0 until 30).map { i -> pool.submit { journals[i % 2].record("read_file", JSONObject().put("i", i), true, 1) } }.forEach { it.get() }
            assertEquals(30, journals[0].all().size)
            dir.resolve("execution-evidence.json").writeText("broken")
            try { journals[1].record("read_file", JSONObject(), true, 1); fail("Corrupted journal") } catch (_: org.json.JSONException) { }
            assertEquals("broken", dir.resolve("execution-evidence.json").readText())
        } finally { pool.shutdownNow(); dir.deleteRecursively() }
    }
    @Test fun benchmarkUsesMedianRejectsPartialFailuresAndPropagatesCancellation() = runTest {
        val calls = mutableListOf<Int>()
        val trials = InferenceStudy.measure(listOf(1, 2, 4), 2) { threads ->
            calls += threads
            if (threads == 4 && calls.size > 3) error("thermal failure")
            InferenceTrial(threads, 10, 1_000_000_000 / threads.toLong())
        }
        assertEquals(listOf(1, 2, 4, 4, 2, 1), calls)
        assertEquals(2, InferenceStudy.best(trials))
        assertNull(InferenceStudy.best(listOf(InferenceTrial(1, 0, 0, "error"))))
        try { InferenceStudy.measure(listOf(1)) { throw CancellationException("cancel") }; fail() }
        catch (_: CancellationException) { }
    }
    @Test fun importsPrettyAlpacaAndShareGptWithoutCuttingExamples() {
        val dir = Files.createTempDirectory("dataset").toFile()
        try {
            val input = dir.resolve("alpaca.json").apply { writeText("""[
                {"instruction":"Olá 🌻","output":"Olá!"},
                {"conversations":[{"from":"human","value":"teste"},{"from":"gpt","value":"resposta"}]}
            ]""") }
            val (output, stats) = DatasetBuilder.build(listOf(input), ChatFormat.CHATML, dir.resolve("out.txt"))
            assertEquals(2, stats.documents)
            assertTrue(output.readText().contains("Olá 🌻"))
            assertTrue(output.readText().contains("<|im_start|>assistant\nresposta"))
            val (_, small) = DatasetBuilder.build(listOf(input), ChatFormat.CHATML, dir.resolve("small.txt"), maxOutputChars = 20)
            assertEquals(0, small.documents)
            assertEquals(2, small.skippedLines)
            assertEquals("", dir.resolve("small.txt").readText())
            val jsonl = dir.resolve("rows.jsonl").apply { writeText("bad\n{\"question\":\"q\",\"answer\":\"a\"}\n") }
            val (_, rows) = DatasetBuilder.build(listOf(jsonl), ChatFormat.RAW, dir.resolve("rows.txt"))
            assertEquals(1, rows.documents); assertEquals(1, rows.skippedLines)
            val invalid = dir.resolve("invalid.json").apply { writeText("{\"messages\":\"invalid\"}") }
            val (_, ignored) = DatasetBuilder.build(listOf(invalid), ChatFormat.CHATML, dir.resolve("invalid.txt"))
            assertEquals(1, ignored.skippedLines)
            assertEquals(0, ignored.documents)
        } finally { dir.deleteRecursively() }
    }
}
