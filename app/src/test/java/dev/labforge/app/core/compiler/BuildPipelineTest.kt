package dev.labforge.app.core.compiler

import dev.labforge.app.core.term.ShellResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BuildPipelineTest {
    private val request = BuildRequest("/data/data/com.termux/files/home/my project", ":mobile:app")
    private val manifest = "GIRASSOL_APK\t${"a".repeat(64)}\t1024\t${request.project}/mobile/app/build/outputs/apk/debug/app-debug.apk"
    private fun result(command: String, code: Int = 0, output: String = "ok") = ShellResult(command, code, output, 25, false)

    @Test fun stopsBeforeAssemblyWhenTestsFailAndPersistsReport() = runTest {
        var calls = 0
        var saved: BuildReport? = null
        val report = BuildPipeline.run(request, { calls++; result(it, if (calls == 2) 1 else 0, "test failure") }, { saved = it })
        assertFalse(report.ok); assertEquals(2, calls)
        assertEquals(StageState.FAILED, saved!!.stages[1].state)
        assertEquals(StageState.WAITING, saved!!.stages[3].state)
        assertTrue(saved!!.artifacts.isEmpty())
        val file = Files.createTempDirectory("build-report").toFile().resolve("report.json")
        try { BuildPipeline.save(file, report); assertEquals(report, BuildReport.read(file)) } finally { file.parentFile!!.deleteRecursively() }
    }
    @Test fun successRequiresValidatedMetadataAndRejectsUnrelatedArtifacts() = runTest {
        val report = BuildPipeline.run(request, { result(it, output = if (it.contains("GIRASSOL_APK")) manifest else "ok") }, {})
        assertTrue(report.ok); assertEquals(1024L, report.artifacts.single().bytes)
        for (bad in listOf("no metadata", manifest.replace("1024", "0"), manifest.replace("/debug/", "/release/"), manifest.replace("a".repeat(64), "bad"))) {
            val failed = BuildPipeline.run(request, { result(it, output = bad) }, {})
            assertFalse(failed.ok); assertEquals(StageState.FAILED, failed.stages.last().state)
        }
    }
    @Test fun cancellationIsCheckpointedAndPropagated() = runTest {
        var saved: BuildReport? = null
        try { BuildPipeline.run(request, { throw CancellationException("stop") }, { saved = it }); fail() }
        catch (_: CancellationException) { }
        assertEquals(StageState.INTERRUPTED, saved!!.stages.first().state)
        assertFalse(saved!!.ok)
    }
    @Test fun timeoutAndTransportFailureCannotPass() = runTest {
        val timeout = BuildPipeline.run(request, { ShellResult(it, 0, "timed out", 10, true) }, {})
        assertFalse(timeout.ok); assertEquals(StageState.FAILED, timeout.stages.first().state)
        val exception = BuildPipeline.run(request, { error("Termux missing") }, {})
        assertEquals("Termux missing", exception.stages.first().output)
    }
    @Test fun plansRespectVariantOfflineWorkersAndStrictLintGate() {
        val stages = BuildPipeline.plan(request.copy(variant = BuildVariant.RELEASE, offline = true, workers = 1))
        assertTrue(stages[1].command.contains(":mobile:app:testReleaseUnitTest"))
        assertTrue(stages[2].command.contains("severity=\"(Error|Fatal)\""))
        assertTrue(stages[2].command.contains("mobile/app/build/reports/lint-results-release.xml"))
        assertTrue(stages[3].command.contains("--offline")); assertTrue(stages[3].command.contains("--max-workers=1"))
        assertTrue(stages.last().command.contains("apksigner verify"))
        assertTrue(stages.last().command.contains("unzip -t"))
        try { request.copy(module = ":app; echo injected"); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun restartMarksUnfinishedStageInterruptedInsteadOfRunningForever() {
        val file = Files.createTempDirectory("build-restore").toFile().resolve("report.json")
        try {
            BuildPipeline.save(file, BuildReport(request, 10, BuildPipeline.plan(request).mapIndexed { i, s -> if (i == 0) s.copy(state = StageState.RUNNING) else s }))
            val restored = BuildReport.read(file)
            assertEquals(StageState.INTERRUPTED, restored.stages.first().state)
            assertFalse(restored.ok)
        } finally { file.parentFile!!.deleteRecursively() }
    }
}
