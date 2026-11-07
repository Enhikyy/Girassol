package dev.labforge.app

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.core.research.ResearchEngine
import dev.labforge.app.ui.screens.AutodidactPane
import dev.labforge.app.ui.theme.LabForgeTheme
import dev.labforge.app.vm.AutodidactViewModel
import dev.labforge.app.vm.LabViewModel
import dev.labforge.app.vm.StudyStage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AutodidactFlowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fullAutonomyFlowRunsAndRenders() {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val study = AutodidactViewModel(app)
        study.theme = "sql e dbt para engenharia de dados"
        study.topicCount = 4
        study.perTopic = 2
        study.autoTrain = false
        study.start()

        val deadline = System.currentTimeMillis() + 180_000
        while (study.running && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(80)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue("stage=${study.stage} log=${study.log.takeLast(1200)}", study.stage == StudyStage.DONE)
        assertNotNull(study.project)
        assertNotNull(study.preset)
        assertTrue(study.bundle!!.docs.isNotEmpty())

        val lab = LabViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LabForgeTheme { AutodidactPane(lab, onTrain = {}, study = study) }
        }
        compose.mainClock.advanceTimeBy(800)
    }
}
