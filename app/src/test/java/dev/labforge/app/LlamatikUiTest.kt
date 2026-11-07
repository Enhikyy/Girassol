package dev.labforge.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.ui.screens.LlamatikScreen
import dev.labforge.app.ui.theme.LabForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LlamatikUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun llamatikScreenComposes() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { LlamatikScreen {} } }
        compose.mainClock.advanceTimeBy(500)
    }
}
