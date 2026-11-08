package dev.labforge.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.ui.components.*
import dev.labforge.app.ui.screens.HomeScreen
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
@Config(sdk = [33], qualifiers = "w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioVisualTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        System.getenv("GIRASSOL_STUDIO_CAPTURE")?.let { folder ->
            File(folder).mkdirs()
            File(folder, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun actualHomeOpensStudyChatAndCompilerWithoutChangingRoutes() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val lab = LabViewModel(app); val agent = AgentViewModel(app)
        var destination = ""; var tab = -1
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg, contentColor = Lab.Text) {
            HomeScreen(lab, agent, { destination = it }, { tab = it })
        } } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Começar conversa").performClick()
        assertEquals("agent", destination)
        compose.onNodeWithText("Explorar um tema").performClick()
        assertEquals(3, tab)
        capture("inicio")
        // Direct semantic scrolling avoids animated scroll with the paused sunflower clock.
        compose.onNodeWithText("Compilar APK").assertExists().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertEquals(5, tab)
    }
    @Test fun welcomeFillsComposerAndSendStopUseTheirOwnActions() {
        var input by mutableStateOf("")
        var sent = ""; var stopped = false; var running by mutableStateOf(false)
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg, contentColor = Lab.Text) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) { ConversationWelcome { input = it } }
                ConversationComposer(input, running, { input = it }, { sent = input }, { stopped = true })
            }
        } } }
        compose.onNodeWithContentDescription("Enviar tarefa").assertIsNotEnabled()
        compose.onNodeWithText("Criar e testar").performScrollTo().performClick()
        assertTrue(input.contains("JSONL"))
        compose.onNodeWithContentDescription("Enviar tarefa").performClick()
        assertEquals(input, sent)
        capture("chat")
        compose.runOnIdle { running = true }
        compose.onNodeWithContentDescription("Parar execução").performClick()
        assertTrue(stopped)
    }
    @Test fun activityCanExpandAndRevealUntruncatedResult() {
        val body = "Resultado real observado. ".repeat(300) + "FINAL COMPLETO"
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg, contentColor = Lab.Text) {
            Column(Modifier.padding(20.dp)) { ExecutionCard("Gradle", body, "Falhou", "assembleDebug", failed = true) }
        } } }
        compose.onNodeWithContentDescription("Expandir Gradle").performClick()
        compose.onNodeWithText(body).assertExists()
        compose.onNodeWithText("assembleDebug").assertIsDisplayed()
        capture("atividade")
        compose.onNodeWithContentDescription("Recolher Gradle").performClick()
        compose.onNodeWithText("assembleDebug").assertDoesNotExist()
    }
    @Test @Config(sdk = [33], qualifiers = "w1000dp-h850dp")
    fun wideWorkspaceShowsFourActionsAndLargeFontStacksCards() {
        var scale by mutableStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent { LabForgeTheme { Surface(color = Lab.Bg, contentColor = Lab.Text) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    StudioHero({}, {}); Spacer(Modifier.height(24.dp)); WorkspaceGrid { }
                }
            }
        } } }
        compose.mainClock.advanceTimeBy(100)
        val chat = compose.onNodeWithText("Conversar").fetchSemanticsNode().boundsInRoot
        val build = compose.onNodeWithText("Compilar APK").fetchSemanticsNode().boundsInRoot
        assertEquals(chat.top, build.top, 1f)
        capture("inicio-amplo")
        compose.runOnIdle { scale = 1.6f }
        compose.mainClock.advanceTimeBy(100)
        val stacked = compose.onNodeWithText("Compilar APK").getUnclippedBoundsInRoot()
        assertTrue(stacked.top > compose.onNodeWithText("Conversar").getUnclippedBoundsInRoot().top)
    }
}
