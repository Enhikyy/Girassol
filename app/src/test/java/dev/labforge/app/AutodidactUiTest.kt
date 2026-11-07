package dev.labforge.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.ui.screens.LabScreen
import dev.labforge.app.ui.theme.LabForgeTheme
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.LabViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class AutodidactUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun autodidactPaneComposes() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val lab = LabViewModel(app)
        val agent = AgentViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LabForgeTheme { LabScreen(lab, agent, initialTab = 3) }
        }
        compose.mainClock.advanceTimeBy(500)
    }
}
