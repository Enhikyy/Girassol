package dev.labforge.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.ui.AppRoot
import dev.labforge.app.ui.theme.LabForgeTheme
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.CodexViewModel
import dev.labforge.app.vm.LabViewModel
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ComposeLaunchTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun viewModelsConstruct() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        assertNotNull(LabViewModel(app))
        assertNotNull(AgentViewModel(app))
        assertNotNull(CodexViewModel(app))
    }

    @Test
    fun appRootComposes() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LabForgeTheme { AppRoot() }
        }
        compose.mainClock.advanceTimeBy(400)
    }
}
