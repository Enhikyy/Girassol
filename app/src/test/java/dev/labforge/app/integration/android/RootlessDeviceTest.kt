package dev.labforge.app.integration.android

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.LabForgeApp
import dev.labforge.app.core.agent.AndroidControlTool
import dev.labforge.app.core.agent.hierarchyContainsText
import dev.labforge.app.core.sleep.SleepResearch
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RootlessDeviceTest {
    @Test @Config(sdk = [26])
    fun screenshotsOnOlderAndroidReportTheMinimumSupportedVersion() = runTest {
        val controller = Robolectric.buildService(GirassolAccessibilityService::class.java).create()
        try {
            try {
                controller.get().screenshot(java.io.File("unused.png"))
                fail("Android 8 não oferece captura por Acessibilidade")
            } catch (error: IllegalStateException) {
                assertTrue(error.message!!.contains("Android 11"))
            }
        } finally { controller.destroy() }
    }

    @Test fun sleepScheduleInitializesWorkManagerAndPersistsUniqueWork() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        SleepResearch.schedule(app, "Android local")
        val manager = WorkManager.getInstance(app)
        try {
            val scheduled = manager.getWorkInfosForUniqueWork(SleepResearch.WORK_NAME).get(10, TimeUnit.SECONDS)
            assertEquals(1, scheduled.size)
            assertTrue(scheduled.single().constraints.requiresCharging())
            assertTrue(scheduled.single().constraints.requiresDeviceIdle())
        } finally {
            manager.cancelUniqueWork(SleepResearch.WORK_NAME).result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test fun termuxResultsIncludeStderrAndDoNotTreatPluginErrorsAsSuccess() {
        val ok = TermuxBridge.output("build", Bundle().apply {
            putInt("exitCode", 2); putString("stdout", "out"); putString("stderr", "err")
        }, 100)
        assertEquals(2, ok.exitCode)
        assertEquals("out\nerr", ok.output)
        val failed = TermuxBridge.output("build", Bundle().apply {
            putInt("err", 1); putInt("exitCode", 0); putString("errmsg", "disabled")
        }, 100)
        assertFalse(failed.ok)
        assertFalse(TermuxBridge.output("build", Bundle(), 0).ok)
    }

    @Test fun termuxTimeoutDoesNotReportSuccessWhenNoResultArrives() = runTest {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        shadowOf(app).grantPermissions(TermuxBridge.PERMISSION)
        val result = TermuxBridge.run(app, TermuxBridge.HOME, "sleep 60", 1)
        assertTrue(result.timedOut)
        assertEquals(124, result.exitCode)
        assertTrue(result.output.contains("pode continuar"))
    }

    @Test fun cancellingCallerPropagatesInsteadOfReturningAnExternalCommandResult() = runTest {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        shadowOf(app).grantPermissions(TermuxBridge.PERMISSION)
        try {
            withTimeout(50) { TermuxBridge.run(app, TermuxBridge.HOME, "sleep 60", 5) }
            fail("Cancelamento externo deveria se propagar")
        } catch (_: TimeoutCancellationException) { /* Expected caller cancellation. */ }
    }

    @Test fun missingTermuxAuthorizationDoesNotStartACommand() = runTest {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        shadowOf(app).denyPermissions(TermuxBridge.PERMISSION)
        try {
            TermuxBridge.run(app, TermuxBridge.HOME, "echo test")
            fail("Permissão deveria ser exigida")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("Autorize"))
        }
        assertNull(shadowOf(app).nextStartedService)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun termuxRunsThroughItsServiceAndReceivesThePendingIntentResult() = runTest {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        shadowOf(app).grantPermissions(TermuxBridge.PERMISSION)
        var result: dev.labforge.app.core.term.ShellResult? = null
        val job = launch { result = TermuxBridge.run(app, TermuxBridge.HOME, "printf 'ação'", 5) }
        runCurrent()
        val sent = shadowOf(app).nextStartedService
        assertEquals("com.termux.app.RunCommandService", sent.component!!.className)
        assertEquals("com.termux.RUN_COMMAND", sent.action)
        assertArrayEquals(arrayOf("-lc", "printf 'ação'"), sent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS"))
        assertTrue(sent.getBooleanExtra("com.termux.RUN_COMMAND_BACKGROUND", false))
        @Suppress("DEPRECATION")
        val callback = sent.getParcelableExtra<PendingIntent>("com.termux.RUN_COMMAND_PENDING_INTENT")!!
        callback.send(app, 0, Intent().putExtra("result", Bundle().apply {
            putInt("exitCode", 0); putString("stdout", "ação")
        }))
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        job.join()
        assertTrue(result!!.ok)
        assertEquals("ação", result!!.output)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun missingAccessibilityExplainsTheRequiredAndroidSetting() = runTest {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        var result: dev.labforge.app.core.agent.ToolResult? = null
        val job = launch { result = AndroidControlTool(app.filesDir).execute(JSONObject().put("action", "hierarchy")) }
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        job.join()
        assertFalse(result!!.ok)
        assertTrue(result!!.output.contains("Acessibilidade"))
    }

    @Test fun uiAssertionsMatchDecodedLabelsAndIgnoreResourceIds() {
        val xml = """<node text="R&amp;D 🌻" description="botão" view-id="secret"/>"""
        assertTrue(hierarchyContainsText(xml, "R&D 🌻"))
        assertTrue(hierarchyContainsText(xml, "botão"))
        assertFalse(hierarchyContainsText(xml, "secret"))
    }
}
