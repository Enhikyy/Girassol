package dev.labforge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.ui.screens.EvolutionPane
import dev.labforge.app.ui.theme.LabForgeTheme
import dev.labforge.app.vm.EvolutionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class EvolutionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nativeEvolutionPanelShowsEvidenceAndRefreshAction() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val vm = EvolutionViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { EvolutionPane(vm) } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Da prática à evidência").assertIsDisplayed()
        compose.onNodeWithText("Atualizar evidências").assertIsDisplayed()
    }
}
