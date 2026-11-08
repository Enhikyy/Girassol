package dev.labforge.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.core.compiler.*
import dev.labforge.app.ui.screens.BuildContent
import dev.labforge.app.ui.theme.LabForgeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuildUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun compactBuildFormStartsAndShowsHonestEmptyState() {
        var started = false
        compose.setContent { LabForgeTheme { Box(Modifier.width(360.dp)) {
            BuildContent("/project", ":app", BuildVariant.DEBUG, false, null, false, false, "", {}, {}, {}, {}, { started = true }, {})
        } } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Central de compilação").assertIsDisplayed()
        System.getenv("GIRASSOL_UI_CAPTURE")?.let { path ->
            File(path).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Validar e compilar").performScrollTo().performClick()
        assertTrue(started)
        compose.onNodeWithText("Tudo pronto para começar").performScrollTo().assertIsDisplayed()
    }
    @Test fun failureDetailsRemainSelectableAndLaterStagesAreWaiting() {
        val request = BuildRequest("/project")
        val stages = BuildPipeline.plan(request).mapIndexed { i, s -> s.copy(state = if (i == 0) StageState.FAILED else StageState.WAITING,
            output = if (i == 0) "SDK ausente" else "") }
        compose.setContent { LabForgeTheme { BuildContent("/project", ":app", BuildVariant.DEBUG, false, BuildReport(request, 1, stages), false, false, "", {}, {}, {}, {}, {}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("1. Diagnóstico").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SDK ausente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Configure ANDROID_HOME e instale a plataforma/build-tools exigidas pelo projeto.").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(sdk = [33], qualifiers = "w1000dp-h800dp")
    fun wideLayoutPlacesResultsBesideConfiguration() {
        val request = BuildRequest("/project")
        compose.setContent { LabForgeTheme { BuildContent("/project", ":app", BuildVariant.DEBUG, false,
            BuildReport(request, 1, BuildPipeline.plan(request)), false, false, "", {}, {}, {}, {}, {}, {}) } }
        val form = compose.onNodeWithText("Projeto Android").fetchSemanticsNode().boundsInRoot
        val stage = compose.onNodeWithText("1. Diagnóstico").fetchSemanticsNode().boundsInRoot
        assertTrue("Results should be in the right pane", stage.left > form.right)
        System.getenv("GIRASSOL_UI_CAPTURE")?.let { path ->
            File(path.removeSuffix(".png") + "-wide.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

}
