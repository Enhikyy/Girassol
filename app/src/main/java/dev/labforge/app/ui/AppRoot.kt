package dev.labforge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.ui.components.AnimatedAuroraBackground
import dev.labforge.app.ui.screens.AgentScreen
import dev.labforge.app.ui.screens.CodexScreen
import dev.labforge.app.ui.screens.HomeScreen
import dev.labforge.app.ui.screens.LabScreen
import dev.labforge.app.ui.screens.LlamatikScreen
import dev.labforge.app.ui.screens.ModelDetailScreen
import dev.labforge.app.ui.screens.ModelsScreen
import dev.labforge.app.ui.screens.SettingsScreen
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.CodexViewModel
import dev.labforge.app.vm.LabViewModel

object Routes {
    const val HOME = "home"
    const val MODELS = "models"
    const val MODEL = "model"
    const val LAB = "lab"
    const val CODEX = "codex"
    const val AGENT = "agent"
    const val LLAMATIK = "llamatik"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Rounded.Home),
    Tab(Routes.MODELS, "Modelos", Icons.Rounded.Widgets),
    Tab(Routes.LAB, "Lab", Icons.Rounded.Science),
    Tab(Routes.CODEX, "Codex", Icons.Rounded.Code),
    Tab(Routes.AGENT, "Chat", Icons.Rounded.SmartToy),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)

@Composable
fun AppRoot() {
    val vm: LabViewModel = viewModel()
    val agentVm: AgentViewModel = viewModel()
    val codexVm: CodexViewModel = viewModel()

    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var arg by rememberSaveable { mutableStateOf("") }
    var labTab by rememberSaveable { mutableStateOf(0) }

    fun go(r: String, a: String = "") {
        if (r.startsWith(Routes.MODEL + "/")) {
            route = Routes.MODEL
            arg = r.removePrefix(Routes.MODEL + "/")
        } else {
            route = r
            arg = a
        }
    }

    BackHandler(enabled = route != Routes.HOME) {
        if (route == Routes.MODEL) go(Routes.MODELS) else go(Routes.HOME)
    }

    AnimatedAuroraBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    // `arg` must be part of the key: navigating model A -> model B keeps
                    // `route` == "model", so keying on route alone would not recompose.
                    targetState = route to arg,
                    transitionSpec = {
                        (slideInHorizontally(tween(320)) { it / 7 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it / 9 } + fadeOut(tween(200)))
                    },
                    label = "screens",
                ) { (current, _) ->
                    when (current) {
                        Routes.HOME -> HomeScreen(vm, agentVm, { r -> go(r) }) { labTab = it; go(Routes.LAB) }
                        Routes.MODELS -> ModelsScreen(vm) { p -> go(Routes.MODEL, p) }
                        Routes.MODEL -> ModelDetailScreen(vm, arg, onBack = { go(Routes.MODELS) }) {
                            labTab = it
                            go(Routes.LAB)
                        }
                        Routes.LAB -> LabScreen(vm, agentVm, initialTab = labTab)
                        Routes.CODEX -> CodexScreen(codexVm) { go(Routes.AGENT) }
                        Routes.AGENT -> AgentScreen(agentVm, vm) { go(Routes.CODEX) }
                        Routes.LLAMATIK -> LlamatikScreen { go(Routes.SETTINGS) }
                        Routes.SETTINGS -> SettingsScreen(vm) { go(Routes.LLAMATIK) }
                        else -> HomeScreen(vm, agentVm, { r -> go(r) }) { labTab = it; go(Routes.LAB) }
                    }
                }
            }
            if (vm.jobRunning) {
                JobStrip(
                    title = vm.jobTitle,
                    progress = vm.jobProgress,
                    eta = dev.labforge.app.core.Perf.fmtEta(vm.jobStartedAt, vm.jobProgress),
                ) {
                    labTab = if (vm.jobKind == "quantize") 0 else 1
                    go(Routes.LAB)
                }
            }
            if (route != Routes.MODEL) {
                BottomBar(route) { r ->
                    if (r == Routes.LAB) labTab = 0
                    go(r)
                }
            }
        }
    }
}

@Composable
private fun JobStrip(title: String, progress: Float, eta: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Lab.Surface)
            .border(1.dp, Lab.Violet.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Lab.Green),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 10.sp, color = Lab.Text, maxLines = 1, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Lab.SurfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .background(Lab.Violet),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("~$eta", fontSize = 9.sp, color = Lab.Muted)
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF01B1E14))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val active = current == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(tab.route) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) Lab.Violet else Lab.Muted,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        fontSize = 9.sp,
                        color = if (active) Lab.Text else Lab.Muted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .size(if (active) 14.dp else 0.dp, 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) Lab.Violet else Color.Transparent),
                    )
                }
            }
        }
    }
}
