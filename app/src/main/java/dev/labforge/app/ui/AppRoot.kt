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
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.labforge.app.ui.components.LiquidTabs
import dev.labforge.app.ui.components.LocalHazeState
import dev.labforge.app.ui.components.glassBackdrop
import dev.labforge.app.ui.components.glassSheen
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import dev.labforge.app.ui.components.Segmented
import dev.labforge.app.ui.screens.AgentScreen
import dev.labforge.app.ui.screens.CodexScreen
import dev.labforge.app.ui.screens.CodeStudioScreen
import dev.labforge.app.ui.screens.HomeScreen
import dev.labforge.app.ui.screens.LabScreen
import dev.labforge.app.ui.screens.LocalChatScreen
import dev.labforge.app.ui.screens.LlamatikScreen
import dev.labforge.app.ui.screens.ModelDetailScreen
import dev.labforge.app.ui.screens.ModelsScreen
import dev.labforge.app.ui.screens.SettingsScreen
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.CodexViewModel
import dev.labforge.app.vm.LabViewModel
import dev.labforge.app.vm.LocalChatViewModel

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val MODELS = "models"
    const val MODEL = "model"
    const val LAB = "lab"
    const val CODEX = "codex"
    const val STUDIO = "studio"
    const val AGENT = "agent"
    const val LLAMATIK = "llamatik"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", Icons.Rounded.Home),
    Tab(Routes.CHAT, "Chat", Icons.Rounded.Chat),
    Tab(Routes.MODELS, "Modelos", Icons.Rounded.Widgets),
    Tab(Routes.LAB, "Lab", Icons.Rounded.Science),
    Tab(Routes.STUDIO, "Studio", Icons.Rounded.Code),
    Tab(Routes.AGENT, "Agentes", Icons.Rounded.SmartToy),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)

@Composable
fun AppRoot() {
    val vm: LabViewModel = viewModel()
    val agentVm: AgentViewModel = viewModel()
    val codexVm: CodexViewModel = viewModel()
    val chatVm: LocalChatViewModel = viewModel()

    LaunchedEffect(vm.settings.pureBlackOled) {
        Lab.pureBlack = vm.settings.pureBlackOled
    }

    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var arg by rememberSaveable { mutableStateOf("") }
    var labTab by rememberSaveable { mutableStateOf(0) }
    var llamatikBack by rememberSaveable { mutableStateOf(Routes.HOME) }

    fun go(r: String, a: String = "") {
        if (r.startsWith(Routes.MODEL + "/")) {
            route = Routes.MODEL
            arg = r.removePrefix(Routes.MODEL + "/")
        } else {
            if (r == Routes.LLAMATIK && route != Routes.LLAMATIK) llamatikBack = route
            route = r
            arg = a
        }
    }

    BackHandler(enabled = route != Routes.HOME) {
        when (route) {
            Routes.MODEL -> go(Routes.MODELS)
            Routes.LLAMATIK -> go(llamatikBack)
            Routes.CODEX -> go(Routes.AGENT)
            else -> go(Routes.HOME)
        }
    }

    val haze = rememberHazeState()
    CompositionLocalProvider(LocalHazeState provides haze) { AnimatedAuroraBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            if (route == Routes.AGENT || route == Routes.CODEX) {
                Segmented(
                    options = listOf("Chat", "Codex"),
                    selected = if (route == Routes.CODEX) 1 else 0,
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp),
                ) { go(if (it == 1) Routes.CODEX else Routes.AGENT) }
            }
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    // `arg` must be part of the key: navigating model A -> model B keeps
                    // `route` == "model", so keying on route alone would not recompose.
                    targetState = route to arg,
                    transitionSpec = {
                        val slide = spring<IntOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        val scale = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                        (slideInHorizontally(slide) { it / 6 } + scaleIn(scale, initialScale = 0.94f) + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(slide) { -it / 10 } + scaleOut(scale, targetScale = 1.03f) + fadeOut(tween(180)))
                    },
                    label = "screens",
                ) { (current, _) ->
                    when (current) {
                        Routes.CHAT -> LocalChatScreen(
                            chatVm = chatVm,
                            labVm = vm,
                            onOpenModels = { go(Routes.MODELS) },
                            onOpenAgent = { prompt ->
                                chatVm.stop()
                                if (prompt.isNotBlank()) agentVm.send("Continue esta conversa usando as ferramentas nativas do Agente quando necessário. Preserve o contexto abaixo e priorize o pedido mais recente:\n\n" + prompt)
                                go(Routes.AGENT)
                            },
                        )
                        Routes.HOME -> HomeScreen(vm, agentVm, { r -> go(r) }) { labTab = it; go(Routes.LAB) }
                        Routes.MODELS -> ModelsScreen(vm) { p -> go(Routes.MODEL, p) }
                        Routes.MODEL -> ModelDetailScreen(vm, arg, onBack = { go(Routes.MODELS) }) {
                            labTab = it
                            go(Routes.LAB)
                        }
                        Routes.LAB -> LabScreen(vm, agentVm, initialTab = labTab)
                        Routes.CODEX -> CodexScreen(codexVm) { go(Routes.AGENT) }
                        Routes.STUDIO -> CodeStudioScreen()
                        Routes.AGENT -> AgentScreen(agentVm, vm) { go(Routes.CODEX) }
                        Routes.LLAMATIK -> LlamatikScreen { go(llamatikBack) }
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
    }}
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
            Text(title, fontSize = 12.sp, color = Lab.Text, maxLines = 1, fontWeight = FontWeight.Medium)
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
        Text("~$eta", fontSize = 12.sp, color = Lab.Muted)
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit) {
    val selected = tabs.indexOfFirst { it.route == current || (it.route == Routes.AGENT && current == Routes.CODEX) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        LiquidTabs(
            count = tabs.size,
            selected = selected,
            modifier = Modifier
                .fillMaxWidth()
                .glassBackdrop(RoundedCornerShape(26.dp))
                .glassSheen(periodMs = 9000, alpha = 0.07f)
                .padding(vertical = 6.dp, horizontal = 6.dp),
            corner = 20.dp,
            inset = 2.dp,
        ) { i, active ->
            val tab = tabs[i]
            val lift by animateFloatAsState(
                if (active) 1f else 0f,
                spring(dampingRatio = 0.42f, stiffness = 380f),
                label = "lift",
            )
            val tint by animateColorAsState(if (active) Lab.Violet else Lab.Muted, label = "tint")
            val labelColor by animateColorAsState(if (active) Lab.Text else Lab.Muted, label = "label")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(tab.route) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val s = 1f + 0.14f * lift
                            scaleX = s
                            scaleY = s
                            translationY = -2.dp.toPx() * lift
                        },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tab.label,
                    fontSize = 12.sp,
                    color = labelColor,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
