package dev.labforge.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.core.agent.LessonStore
import dev.labforge.app.core.model.ModelStore
import dev.labforge.app.ui.BottomBar
import dev.labforge.app.ui.Routes
import dev.labforge.app.ui.screens.AgentScreen
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.ui.theme.LabForgeTheme
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.LabViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w375dp-h812dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun capture(name: String) {
        System.getenv("GIRASSOL_UX_CAPTURE")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun bottomNavigationExposesSelectionAndHandlesRouteWithoutTab() {
        var route by mutableStateOf(Routes.HOME)
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg) { BottomBar(route) { route = it } } } }
        compose.mainClock.advanceTimeBy(300)
        compose.onNode(hasText("Início") and tab).assertIsSelected().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNode(hasText("Agentes") and tab).assertIsNotSelected().performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNode(hasText("Agentes") and tab).assertIsSelected()
        assertEquals(Routes.AGENT, route)
        capture("navegacao")
        compose.runOnIdle { route = Routes.LLAMATIK }
        compose.mainClock.advanceTimeBy(300)
        listOf("Início", "Modelos", "Lab", "Agentes", "Ajustes").forEach { compose.onNode(hasText(it) and tab).assertIsNotSelected() }
    }
    @Test fun chatDraftSurvivesSwitchingSectionsAndFieldKeepsItsLabel() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val agent = AgentViewModel(app); val lab = LabViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg, contentColor = Lab.Text) { AgentScreen(agent, lab) } } }
        compose.mainClock.advanceTimeBy(300)
        compose.onNode(hasText("Chat") and tab).assertIsSelected()
        compose.onNode(hasSetTextAction()).performTextReplacement("Um rascunho para continuar")
        compose.onNodeWithText("Sua mensagem").assertExists()
        compose.onNode(hasText("Terminal") and tab).performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNode(hasText("Terminal") and tab).assertIsSelected()
        compose.onNode(hasText("Chat") and tab).performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Um rascunho para continuar").assertExists()
        compose.onNodeWithText("Sua mensagem").assertExists()
        compose.onNodeWithContentDescription("Enviar tarefa").assertIsEnabled().assertHeightIsAtLeast(48.dp)
        capture("chat-rascunho")
    }
    @Test fun notebookDeleteHasLabelAndFullTouchTarget() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val store = LessonStore(ModelStore(app).agentDir)
        val lesson = store.save("Lição de teste", "Exemplo local")
        try {
            val agent = AgentViewModel(app); val lab = LabViewModel(app)
            compose.mainClock.autoAdvance = false
            compose.setContent { LabForgeTheme { Surface(color = Lab.Bg, contentColor = Lab.Text) { AgentScreen(agent, lab) } } }
            compose.mainClock.advanceTimeBy(300)
            compose.onNode(hasText("Caderno") and tab).performClick()
            compose.mainClock.advanceTimeBy(300)
            compose.waitUntil(timeoutMillis = 10000) { agent.lessonList.any { it.id == lesson.id } }
            compose.onNodeWithContentDescription("Excluir lição: Lição de teste").assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
            compose.waitUntil(timeoutMillis = 10000) { store.all().none { it.id == lesson.id } }
        } finally { store.delete(lesson.id) }
    }
    @Test @Config(sdk = [33], qualifiers = "w812dp-h375dp-land")
    fun navigationWorksInLandscape() {
        var route by mutableStateOf(Routes.HOME)
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg) { BottomBar(route) { route = it } } } }
        compose.mainClock.advanceTimeBy(300)
        compose.onNode(hasText("Lab") and tab).assertHeightIsAtLeast(48.dp).performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNode(hasText("Lab") and tab).assertIsSelected()
        assertEquals(Routes.LAB, route)
        capture("paisagem")
    }
}
