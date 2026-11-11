package dev.labforge.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.labforge.app.core.world.ActionType
import dev.labforge.app.core.world.AgentState
import dev.labforge.app.core.world.BuildingState
import dev.labforge.app.core.world.CompanyDayMetric
import dev.labforge.app.core.world.CompanyState
import dev.labforge.app.core.world.CompanyStrategy
import dev.labforge.app.core.world.EconomyDayMetric
import dev.labforge.app.core.world.HappeningType
import dev.labforge.app.core.world.ProductStage
import dev.labforge.app.core.world.WorldEvent
import dev.labforge.app.core.world.WorldState
import dev.labforge.app.core.world.activeCompanies
import dev.labforge.app.core.world.agent
import dev.labforge.app.core.world.building
import dev.labforge.app.core.world.company
import dev.labforge.app.core.world.day
import dev.labforge.app.core.world.displayName
import dev.labforge.app.core.world.minuteOfDay
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.ui.world.WorldSceneView
import dev.labforge.app.vm.WorldDialogueMessage
import dev.labforge.app.vm.WorldSelectionKind
import dev.labforge.app.vm.WorldViewModel
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private enum class WorldPanel { RESIDENTS, COMPANIES, PLACES, ECONOMY, EVENTS }

@Composable
fun WorldScreen(vm: WorldViewModel) {
    val state by vm.world.collectAsState()
    val status by vm.status.collectAsState()
    var scene by remember { mutableStateOf<WorldSceneView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var fps by remember { mutableStateOf(0) }
    var renderedAgents by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf<WorldPanel?>(null) }
    var happeningDialog by remember { mutableStateOf(false) }
    var companyDialog by remember { mutableStateOf(false) }
    var dialogueOpen by remember { mutableStateOf(false) }

    LaunchedEffect(vm.focusRequestId) {
        val target = vm.focusTargetId
        if (target != null) scene?.focusOn(target)
    }
    LaunchedEffect(vm.notice) {
        if (vm.notice != null) {
            delay(4_000)
            vm.clearNotice()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scene?.shutdown()
            scene = null
            vm.save()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF071014))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WorldSceneView(context).apply {
                    onEntitySelected = { id -> vm.selectFromScene(id); panel = null }
                    onReady = { ready = true; renderError = null }
                    onFailure = { renderError = it; ready = false }
                    onVisibleAgents = vm::setVisibleAgents
                    onFrameStats = { value, agents -> fps = value; renderedAgents = agents }
                    setWorldState(state)
                    setSelected(vm.selectedId)
                    onResume()
                    scene = this
                }
            },
            update = { view ->
                view.setWorldState(state)
                view.setSelected(vm.selectedId)
            },
        )

        if (!ready) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF071014)).padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (renderError == null) {
                    CircularProgressIndicator(color = Lab.Violet)
                    Spacer(Modifier.height(16.dp))
                    Text("Construindo Aurora", color = Lab.Text, fontWeight = FontWeight.SemiBold)
                    Text("Inicializando geometria, iluminação e estado persistente", color = Lab.Muted, fontSize = 12.sp)
                } else {
                    Icon(Icons.Rounded.Public, null, tint = Lab.Red, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Renderização 3D indisponível", color = Lab.Text, fontWeight = FontWeight.SemiBold)
                    Text(renderError ?: "", color = Lab.Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("A simulação e os painéis continuam acessíveis. A validação visual exige um dispositivo com OpenGL ES 3.", color = Lab.Muted, fontSize = 12.sp)
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            WorldTopBar(
                state = state,
                fps = fps,
                renderedAgents = renderedAgents,
                onPlayback = { vm.setPlayback(!state.paused, state.timeScale) },
                onSpeed = {
                    val next = when (state.timeScale) { 1 -> 4; 4 -> 16; else -> 1 }
                    vm.setPlayback(state.paused, next)
                },
                onHappening = { happeningDialog = true },
                onHistory = { panel = WorldPanel.EVENTS; vm.clearSelection() },
            )
            if (status.loading) {
                Spacer(Modifier.height(6.dp))
                WorldStatusChip("Recuperando snapshot e compensando tempo…", Lab.Amber)
            } else if (status.compensatedMinutes > 0L) {
                Spacer(Modifier.height(6.dp))
                WorldStatusChip("${status.compensatedMinutes} min simulados na retomada", Lab.Cyan)
            }
            status.warning?.let {
                Spacer(Modifier.height(6.dp))
                WorldStatusChip(it, Lab.Red)
            }
            if (state.integration.activeJobKind.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Lab.Surface.copy(alpha = 0.92f)).padding(11.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Lab.Cyan))
                        Spacer(Modifier.width(7.dp))
                        Text("Núcleo de Inferência · ${state.integration.activeJobTitle}", color = Lab.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.integration.activeJobProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
                        color = Lab.Cyan, trackColor = Lab.SurfaceHigh,
                    )
                }
            }
        }

        when {
            panel != null -> WorldPanelSheet(
                panel = panel!!,
                state = state,
                onClose = { panel = null },
                onAgent = { vm.selectAgent(it); panel = null },
                onCompany = { vm.selectCompany(it); panel = null },
                onBuilding = { vm.selectBuilding(it); panel = null },
                onFoundCompany = { companyDialog = true },
                onParameters = vm::setParameters,
            )
            vm.selectedId != null -> SelectionSheet(
                vm = vm,
                state = state,
                onClose = vm::clearSelection,
                onFocus = vm::focusOn,
                onCompany = vm::selectCompany,
                onDialogue = { vm.openDialogue(it); dialogueOpen = true },
            )
            else -> WorldBottomDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                onPanel = { panel = it; vm.clearSelection() },
            )
        }

        vm.notice?.let { message ->
            Text(
                message,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 92.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Lab.SurfaceHigh).padding(13.dp),
                color = Lab.Text, fontSize = 12.sp,
            )
        }
    }

    if (happeningDialog) {
        HappeningDialog(onDismiss = { happeningDialog = false }) { type, description ->
            vm.applyHappening(type, description)
            happeningDialog = false
        }
    }
    if (companyDialog) {
        FoundCompanyDialog(onDismiss = { companyDialog = false }) { name, strategy ->
            vm.foundCompany(name, strategy)
            companyDialog = false
        }
    }
    if (dialogueOpen && vm.dialogueAgentId != null) {
        DialogueDialog(
            vm = vm,
            agentName = state.agent(vm.dialogueAgentId!!)?.name ?: "Habitante",
            onDismiss = { dialogueOpen = false; vm.stopDialogue() },
        )
    }
}

@Composable
private fun WorldTopBar(
    state: WorldState,
    fps: Int,
    renderedAgents: Int,
    onPlayback: () -> Unit,
    onSpeed: () -> Unit,
    onHappening: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Lab.Surface.copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Public, "Mundo", tint = Lab.Violet, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("MUNDO · ${state.name}", color = Lab.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Dia ${state.day + 1} · ${formatWorldTime(state.minuteOfDay)} · ${if (state.paused) "pausado" else "${state.timeScale}×"}",
                    color = if (state.paused) Lab.Amber else Lab.Muted, fontSize = 10.sp)
            }
            WorldIconButton(if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, "Pausar ou retomar", onPlayback)
            Text(
                "${state.timeScale}×",
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onSpeed).padding(horizontal = 9.dp, vertical = 10.dp),
                color = Lab.Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            WorldIconButton(Icons.Rounded.Event, "Criar acontecimento", onHappening)
            WorldIconButton(Icons.Rounded.History, "Histórico do mundo", onHistory)
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${state.agents.size} habitantes", color = Lab.Muted, fontSize = 10.sp)
            Text("${state.activeCompanies.size} empresas", color = Lab.Muted, fontSize = 10.sp)
            Text("${state.buildings.size} locais", color = Lab.Muted, fontSize = 10.sp)
            if (fps > 0) Text("$fps fps · $renderedAgents visíveis", color = Lab.Cyan, fontSize = 10.sp)
        }
    }
}

@Composable
private fun WorldStatusChip(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lab.Surface.copy(alpha = 0.94f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = Lab.Text, fontSize = 10.sp, maxLines = 2)
    }
}

@Composable
private fun WorldIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Lab.Muted, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun WorldBottomDock(modifier: Modifier, onPanel: (WorldPanel) -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(23.dp))
            .background(Lab.Surface.copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(23.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DockItem("Pessoas", Icons.Rounded.Groups, Modifier.weight(1f)) { onPanel(WorldPanel.RESIDENTS) }
        DockItem("Empresas", Icons.Rounded.Business, Modifier.weight(1f)) { onPanel(WorldPanel.COMPANIES) }
        DockItem("Locais", Icons.Rounded.Apartment, Modifier.weight(1f)) { onPanel(WorldPanel.PLACES) }
        DockItem("Economia", Icons.Rounded.Timeline, Modifier.weight(1f)) { onPanel(WorldPanel.ECONOMY) }
    }
}

@Composable
private fun DockItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Lab.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Lab.Text, fontSize = 10.sp)
    }
}

@Composable
private fun WorldPanelSheet(
    panel: WorldPanel,
    state: WorldState,
    onClose: () -> Unit,
    onAgent: (String) -> Unit,
    onCompany: (String) -> Unit,
    onBuilding: (String) -> Unit,
    onFoundCompany: () -> Unit,
    onParameters: (Float, Float, Boolean) -> Unit,
) {
    val title = when (panel) {
        WorldPanel.RESIDENTS -> "Habitantes de Aurora"
        WorldPanel.COMPANIES -> "Empresas e produção"
        WorldPanel.PLACES -> "Ambientes da cidade"
        WorldPanel.ECONOMY -> "Economia simulada"
        WorldPanel.EVENTS -> "Memória do mundo"
    }
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.74f).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Lab.BgElev).border(1.dp, Lab.Outline, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .padding(18.dp),
    ) {
        PanelHeader(title, onClose)
        when (panel) {
            WorldPanel.RESIDENTS -> ResidentsPanel(state, onAgent)
            WorldPanel.COMPANIES -> CompaniesPanel(state, onCompany, onFoundCompany)
            WorldPanel.PLACES -> PlacesPanel(state, onBuilding)
            WorldPanel.ECONOMY -> EconomyPanel(state, onParameters)
            WorldPanel.EVENTS -> EventsPanel(state)
        }
    }
}

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Lab.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        WorldIconButton(Icons.Rounded.Close, "Fechar painel", onClose)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ResidentsPanel(state: WorldState, onAgent: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Buscar por nome, profissão ou atividade") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
    )
    Spacer(Modifier.height(10.dp))
    val filtered = state.agents.filter { agent ->
        query.isBlank() || agent.name.contains(query, true) || agent.profession.displayName().contains(query, true) ||
            agent.action?.type?.displayName()?.contains(query, true) == true
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(filtered, key = { it.id }) { agent ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lab.Surface)
                    .clickable { onAgent(agent.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(agent.appearance.primaryColor.toULong().toLong())))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(agent.name, color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${agent.profession.displayName()} · ${agent.action?.type?.displayName() ?: "sem plano"}", color = Lab.Muted, fontSize = 11.sp)
                }
                Text(agent.emotion.name.lowercase(), color = emotionColor(agent), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CompaniesPanel(state: WorldState, onCompany: (String) -> Unit, onFoundCompany: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Reserva do usuário", color = Lab.Muted, fontSize = 10.sp)
            Text(formatMoney(state.playerCapital), color = Lab.Violet, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.clip(RoundedCornerShape(13.dp)).background(Lab.Violet).clickable(onClick = onFoundCompany).padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Lab.OnPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Fundar · ₡25 mil", color = Lab.OnPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Spacer(Modifier.height(12.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(state.companies, key = { it.id }) { company ->
            val employees = state.agents.count { it.employerId == company.id }
            val metric = company.metrics.lastOrNull()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lab.Surface)
                    .clickable { onCompany(company.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(company.name, color = if (company.bankrupt) Lab.Muted else Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${company.strategy} · $employees funcionários · ${company.products.count { it.stage == ProductStage.RELEASED }} produtos no mercado",
                        color = Lab.Muted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(company.cash), color = if (company.cash < 0) Lab.Red else Lab.Green, fontSize = 12.sp)
                    Text(if (company.bankrupt) "falida" else metric?.let { "lucro ${formatMoney(it.profit)}" } ?: "sem fechamento",
                        color = Lab.Muted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun PlacesPanel(state: WorldState, onBuilding: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items(state.buildings, key = { it.id }) { building ->
            val occupants = state.agents.count { it.currentBuildingId == building.id }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lab.Surface)
                    .clickable { onBuilding(building.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color(building.accentColor.toULong().toLong()).copy(alpha = 0.25f)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(building.name, color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${building.kind} · tecnologia ${(building.technology * 100).toInt()}%", color = Lab.Muted, fontSize = 10.sp)
                }
                Text("$occupants presentes", color = Lab.Cyan, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun EventsPanel(state: WorldState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items(state.events, key = { it.id }) { event ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Lab.Surface).padding(12.dp)) {
                Row {
                    Text("Dia ${event.simMinute / 1_440 + 1} · ${formatWorldTime((event.simMinute % 1_440).toInt())}",
                        color = Lab.Violet, fontSize = 9.sp, modifier = Modifier.weight(1f))
                    Text(event.kind.name, color = Lab.Muted, fontSize = 9.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(event.title, color = Lab.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(event.description, color = Lab.Muted, fontSize = 10.sp, lineHeight = 15.sp)
                if (event.agentIds.isNotEmpty() || event.companyIds.isNotEmpty() || event.buildingIds.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text("${event.agentIds.size} pessoas · ${event.companyIds.size} empresas · ${event.buildingIds.size} locais",
                        color = Lab.Cyan, fontSize = 9.sp)
                }
            }
        }
    }
}


@Composable
private fun EconomyPanel(state: WorldState, onParameters: (Float, Float, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val history = state.economyHistory.takeLast(45)
        if (history.isEmpty()) {
            Text("O primeiro fechamento ocorre quando o dia simulado terminar. Nenhum histórico artificial foi preenchido.",
                color = Lab.Muted, fontSize = 12.sp, lineHeight = 18.sp)
        } else {
            MetricChart(
                series = listOf(history.map { it.revenue.toFloat() }, history.map { it.expenses.toFloat() }, history.map { it.profit.toFloat() }),
                labels = listOf("Receita", "Despesas", "Lucro"),
                colors = listOf(Lab.Green, Lab.Red, Lab.Violet),
            )
            val latest = history.last()
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EconomyStat("Produtividade", "${(latest.productivity * 100).toInt()}%", Modifier.weight(1f))
                EconomyStat("Tecnologia", "${(latest.technology * 100).toInt()}%", Modifier.weight(1f))
                EconomyStat("Empregos", "${latest.employees}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            MetricChart(
                series = listOf(history.map { it.productivity * 100f }, history.map { it.technology * 100f }, history.map { it.employees.toFloat() }),
                labels = listOf("Produtividade %", "Tecnologia %", "Funcionários"),
                colors = listOf(Lab.Cyan, Lab.Violet, Lab.Pink),
            )
            Spacer(Modifier.height(8.dp))
            Text("Dias ${history.first().day}–${history.last().day}. Todos os pontos vêm de fechamentos econômicos persistidos.",
                color = Lab.Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Parâmetros permitidos", color = Lab.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("As mudanças entram nas próximas decisões e fechamentos, sem reescrever o histórico.", color = Lab.Muted, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        var demand by remember(state.parameters.demandMultiplier) { mutableStateOf(state.parameters.demandMultiplier) }
        var research by remember(state.parameters.researchMultiplier) { mutableStateOf(state.parameters.researchMultiplier) }
        ParameterSlider("Demanda de mercado", demand, "×${"%.2f".format(demand)}", { demand = it }) {
            onParameters(demand, research, state.parameters.offlineCatchUpEnabled)
        }
        ParameterSlider("Investimento em pesquisa", research, "×${"%.2f".format(research)}", { research = it }) {
            onParameters(demand, research, state.parameters.offlineCatchUpEnabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Retomada temporal", color = Lab.Text, fontSize = 12.sp)
                Text("Compensa até 7 dias; não executa em segundo plano", color = Lab.Muted, fontSize = 10.sp)
            }
            Switch(
                checked = state.parameters.offlineCatchUpEnabled,
                onCheckedChange = { onParameters(demand, research, it) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("Integração real do Girassol", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("${state.integration.knowledgeCount} lições no caderno · ${state.integration.lastKnowledgeTitle.ifBlank { "sem título sincronizado" }}",
            color = Lab.Muted, fontSize = 10.sp)
        Text("Última atividade nativa: ${state.integration.lastJobSummary.ifBlank { "nenhuma concluída" }}",
            color = Lab.Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ParameterSlider(label: String, value: Float, display: String, onChange: (Float) -> Unit, onFinish: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Lab.Text, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(display, color = Lab.Violet, fontSize = 11.sp)
    }
    Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinish, valueRange = 0.25f..3f)
}

@Composable
private fun EconomyStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Lab.Surface).padding(10.dp)) {
        Text(value, color = Lab.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Lab.Muted, fontSize = 9.sp)
    }
}

@Composable
private fun SelectionSheet(
    vm: WorldViewModel,
    state: WorldState,
    onClose: () -> Unit,
    onFocus: (String) -> Unit,
    onCompany: (String) -> Unit,
    onDialogue: (String) -> Unit,
) {
    val id = vm.selectedId ?: return
    val title = when (vm.selectionKind) {
        WorldSelectionKind.AGENT -> state.agent(id)?.name
        WorldSelectionKind.COMPANY -> state.company(id)?.name
        WorldSelectionKind.BUILDING -> state.building(id)?.name
        null -> null
    } ?: "Elemento selecionado"
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.59f).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Lab.BgElev).border(1.dp, Lab.Outline, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .padding(18.dp).verticalScroll(rememberScrollState()),
    ) {
        PanelHeader(title, onClose)
        when (vm.selectionKind) {
            WorldSelectionKind.AGENT -> state.agent(id)?.let { agent ->
                AgentDetails(agent, state, onFocus, onCompany, onDialogue)
            }
            WorldSelectionKind.COMPANY -> state.company(id)?.let { company ->
                CompanyDetails(company, state, onFocus)
            }
            WorldSelectionKind.BUILDING -> state.building(id)?.let { building ->
                BuildingDetails(building, state, onFocus, onCompany)
            }
            null -> Unit
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AgentDetails(
    agent: AgentState,
    state: WorldState,
    onFocus: (String) -> Unit,
    onCompany: (String) -> Unit,
    onDialogue: (String) -> Unit,
) {
    Text("${agent.profession.displayName()} · ${agent.emotion.name.lowercase()}", color = emotionColor(agent), fontSize = 12.sp)
    Text(agent.employerId?.let { state.company(it)?.name } ?: "Sem vínculo empregatício",
        color = Lab.Muted, fontSize = 11.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionAction("Visitar", Icons.Rounded.MyLocation) { onFocus(agent.id) }
        SelectionAction("Conversar", Icons.Rounded.Chat) { onDialogue(agent.id) }
        if (agent.employerId != null) SelectionAction("Empresa", Icons.Rounded.Business) { onCompany(agent.employerId) }
    }
    Spacer(Modifier.height(14.dp))
    Text(agent.action?.type?.displayName() ?: "Escolhendo próxima ação", color = Lab.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    agent.action?.let { action ->
        Text("${action.remainingMinutes} min restantes · destino ${state.building(action.targetBuildingId ?: "")?.name ?: "circulação"}",
            color = Lab.Muted, fontSize = 10.sp)
    }
    Spacer(Modifier.height(12.dp))
    NeedBar("Energia", agent.needs.energy, Lab.Green)
    NeedBar("Nutrição", agent.needs.nutrition, Lab.Amber)
    NeedBar("Socialização", agent.needs.social, Lab.Cyan)
    NeedBar("Motivação", agent.needs.motivation, Lab.Violet)
    NeedBar("Satisfação", agent.needs.satisfaction, Lab.Green)
    NeedBar("Estresse", agent.needs.stress, Lab.Red)
    Spacer(Modifier.height(12.dp))
    Text("Objetivos", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    agent.goals.forEach { goal ->
        Text("${goal.description} · ${(goal.progress * 100).toInt()}%", color = Lab.Muted, fontSize = 10.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text("Habilidades desenvolvidas", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    agent.skills.entries.sortedByDescending { it.value }.take(5).forEach { (skill, value) ->
        NeedBar(skill, value * 100f, Lab.Cyan)
    }
    Spacer(Modifier.height(10.dp))
    Text("Relacionamentos", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val relations = agent.relationships.entries.sortedByDescending { it.value }.take(4)
    if (relations.isEmpty()) Text("Nenhum vínculo formado ainda", color = Lab.Muted, fontSize = 10.sp)
    relations.forEach { (otherId, affinity) ->
        Text("${state.agent(otherId)?.name ?: otherId} · afinidade ${"%.2f".format(affinity)}", color = Lab.Muted, fontSize = 10.sp)
    }
    Spacer(Modifier.height(10.dp))
    Text("Memórias persistentes", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    if (agent.memories.isEmpty()) Text("As experiências concluídas serão registradas aqui.", color = Lab.Muted, fontSize = 10.sp)
    agent.memories.take(5).forEach { memory ->
        Text("Dia ${memory.simMinute / 1_440 + 1} · ${memory.text}", color = Lab.Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(4.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text("Histórico de atividades", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    agent.activityHistory.take(5).forEach { activity ->
        Text("${activity.action.displayName()} · ${activity.durationMinutes} min · ${activity.outcome}", color = Lab.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun CompanyDetails(company: CompanyState, state: WorldState, onFocus: (String) -> Unit) {
    Text("${company.strategy} · fundada no dia ${company.foundedOnDay + 1}${if (company.userOwned) " · sua empresa" else ""}",
        color = if (company.bankrupt) Lab.Red else Lab.Violet, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    SelectionAction("Visitar sede", Icons.Rounded.MyLocation) { onFocus(company.id) }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EconomyStat("Caixa", formatMoney(company.cash), Modifier.weight(1f))
        EconomyStat("Equipe", "${state.agents.count { it.employerId == company.id }}", Modifier.weight(1f))
        EconomyStat("Tecnologia", "${(company.technology * 100).toInt()}%", Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Text("Departamentos", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    company.departments.forEach { department ->
        Text("${department.name} · ${formatMoney(department.budget)} · ${department.focus}", color = Lab.Muted, fontSize = 10.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text("Produtos", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    company.products.forEach { product ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(10.dp)).background(Lab.Surface).padding(10.dp)) {
            Text("${product.name} · ${product.stage}", color = Lab.Text, fontSize = 11.sp)
            Text("desenvolvimento ${(product.development * 100).toInt()}% · qualidade ${(product.quality * 100).toInt()}% · inovação ${(product.innovation * 100).toInt()}%",
                color = Lab.Muted, fontSize = 9.sp)
            Text("${product.unitsSold} unidades · receita acumulada ${formatMoney(product.totalRevenue)}", color = Lab.Cyan, fontSize = 9.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Indicadores diários", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    if (company.metrics.isEmpty()) {
        Text("Nenhum dia econômico concluído desde a fundação.", color = Lab.Muted, fontSize = 10.sp)
    } else {
        CompanyMetricChart(company.metrics.takeLast(35))
    }
}

@Composable
private fun BuildingDetails(building: BuildingState, state: WorldState, onFocus: (String) -> Unit, onCompany: (String) -> Unit) {
    Text(building.kind.name, color = Lab.Violet, fontSize = 11.sp)
    Text("${state.agents.count { it.currentBuildingId == building.id }} habitantes presentes · condição ${(building.condition * 100).toInt()}%",
        color = Lab.Muted, fontSize = 11.sp)
    Text("Tecnologia do local: ${(building.technology * 100).toInt()}%", color = Lab.Cyan, fontSize = 11.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionAction("Visitar", Icons.Rounded.MyLocation) { onFocus(building.id) }
        building.companyId?.let { SelectionAction("Empresa", Icons.Rounded.Business) { onCompany(it) } }
    }
    Spacer(Modifier.height(12.dp))
    Text("Atividades no local", color = Lab.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val occupants = state.agents.filter { it.currentBuildingId == building.id }.take(12)
    if (occupants.isEmpty()) Text("Nenhum habitante registrou presença neste instante.", color = Lab.Muted, fontSize = 10.sp)
    occupants.forEach { agent ->
        Text("${agent.name} · ${agent.action?.type?.displayName() ?: "chegando"}", color = Lab.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SelectionAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Lab.SurfaceHigh).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Lab.Violet, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Lab.Text, fontSize = 10.sp)
    }
}

@Composable
private fun NeedBar(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = Lab.Muted, fontSize = 10.sp, modifier = Modifier.width(94.dp))
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Lab.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth((value / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
        Spacer(Modifier.width(7.dp))
        Text("${value.toInt()}", color = Lab.Text, fontSize = 9.sp, modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun CompanyMetricChart(metrics: List<CompanyDayMetric>) {
    MetricChart(
        series = listOf(metrics.map { it.revenue.toFloat() }, metrics.map { it.expenses.toFloat() }, metrics.map { it.profit.toFloat() }),
        labels = listOf("Receita", "Despesas", "Lucro"), colors = listOf(Lab.Green, Lab.Red, Lab.Violet),
    )
    Spacer(Modifier.height(6.dp))
    MetricChart(
        series = listOf(metrics.map { it.productivity * 100f }, metrics.map { it.marketShare * 100f }, metrics.map { it.technology * 100f }),
        labels = listOf("Produtividade %", "Mercado %", "Tecnologia %"), colors = listOf(Lab.Cyan, Lab.Pink, Lab.Violet),
    )
}

@Composable
private fun MetricChart(series: List<List<Float>>, labels: List<String>, colors: List<Color>) {
    val allValues = series.flatten()
    if (allValues.isEmpty()) return
    val minimum = min(0f, allValues.minOrNull() ?: 0f)
    val maximum = max(1f, allValues.maxOrNull() ?: 1f)
    val range = (maximum - minimum).coerceAtLeast(0.001f)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        series.forEachIndexed { index, _ ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(colors[index]))
                Spacer(Modifier.width(4.dp))
                Text(labels[index], color = Lab.Muted, fontSize = 9.sp)
            }
        }
    }
    Spacer(Modifier.height(5.dp))
    Canvas(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)).background(Lab.Surface).padding(8.dp)) {
        val zeroY = size.height - ((0f - minimum) / range) * size.height
        drawLine(Lab.Outline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1f)
        series.forEachIndexed { seriesIndex, values ->
            if (values.isEmpty()) return@forEachIndexed
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) size.width / 2f else index.toFloat() / (values.size - 1) * size.width
                val y = size.height - ((value - minimum) / range) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (values.size == 1) drawCircle(colors[seriesIndex], radius = 3f, center = Offset(x, y))
            }
            if (values.size > 1) drawPath(path, colors[seriesIndex], style = Stroke(width = 2.2f))
        }
    }
    Row {
        Text(formatChartValue(minimum), color = Lab.Muted, fontSize = 8.sp, modifier = Modifier.weight(1f))
        Text(formatChartValue(maximum), color = Lab.Muted, fontSize = 8.sp)
    }
}


@Composable
private fun HappeningDialog(onDismiss: () -> Unit, onApply: (HappeningType, String) -> Unit) {
    var selected by remember { mutableStateOf(HappeningType.FESTIVAL) }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar acontecimento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("O evento altera o estado persistente e pode influenciar decisões e economia.", color = Lab.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                HappeningType.entries.forEach { type ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { selected = type }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == type, onClick = { selected = type })
                        Column {
                            Text(happeningLabel(type), color = Lab.Text, fontSize = 12.sp)
                            Text(happeningEffect(type), color = Lab.Muted, fontSize = 9.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = description, onValueChange = { description = it.take(500) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    label = { Text("Descrição do acontecimento") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(selected, description) }) { Text("Aplicar à simulação") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FoundCompanyDialog(onDismiss: () -> Unit, onFound: (String, CompanyStrategy) -> Unit) {
    var name by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(CompanyStrategy.RESEARCH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fundar empresa em Aurora") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("₡25.000 serão transferidos da reserva para o caixa inicial. A sede e o primeiro produto serão criados.", color = Lab.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Nome da empresa") }, singleLine = true)
                Spacer(Modifier.height(10.dp))
                CompanyStrategy.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { strategy = option }, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = strategy == option, onClick = { strategy = option })
                        Text(strategyLabel(option), color = Lab.Text, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFound(name, strategy) }, enabled = name.trim().length >= 3) { Text("Fundar por ₡25 mil") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogueDialog(vm: WorldViewModel, agentName: String, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(agentName) },
        text = {
            Column {
                Text("Inferência real pelo motor configurado no Girassol. As duas falas são gravadas na memória do habitante.",
                    color = Lab.Muted, fontSize = 10.sp, lineHeight = 14.sp)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 270.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (vm.dialogue.isEmpty()) Text("Inicie uma conversa com o estado atual de ${agentName}.", color = Lab.Muted, fontSize = 11.sp)
                    vm.dialogue.forEach { message -> DialogueBubble(message) }
                    if (vm.busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Lab.Violet)
                            Spacer(Modifier.width(7.dp))
                            Text("Processando no endpoint configurado…", color = Lab.Muted, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(2_000) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    label = { Text("Sua mensagem") }, enabled = !vm.busy,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.sendDialogue(draft); draft = "" },
                enabled = !vm.busy && draft.isNotBlank(),
            ) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (vm.busy) "Interromper" else "Fechar") } },
    )
}

@Composable
private fun DialogueBubble(message: WorldDialogueMessage) {
    val background = if (message.fromUser) Lab.Violet.copy(alpha = 0.18f) else Lab.SurfaceHigh
    val alignment = if (message.fromUser) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Text(if (message.fromUser) "Você" else "Habitante", color = Lab.Muted, fontSize = 8.sp)
        Text(
            message.text,
            modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(12.dp)).background(background).padding(10.dp),
            color = Lab.Text, fontSize = 11.sp, lineHeight = 16.sp,
        )
    }
}

private fun emotionColor(agent: AgentState): Color = when {
    agent.needs.stress > 70f || agent.needs.satisfaction < 25f -> Lab.Red
    agent.needs.energy < 25f -> Lab.Amber
    agent.needs.satisfaction > 75f -> Lab.Green
    else -> Lab.Cyan
}

private fun formatWorldTime(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)

private fun formatMoney(value: Double): String {
    val sign = if (value < 0) "-" else ""
    val absolute = kotlin.math.abs(value)
    val number = when {
        absolute >= 1_000_000_000 -> "${"%.2f".format(absolute / 1_000_000_000)} bi"
        absolute >= 1_000_000 -> "${"%.2f".format(absolute / 1_000_000)} mi"
        absolute >= 1_000 -> "${"%.1f".format(absolute / 1_000)} mil"
        else -> "%.0f".format(absolute)
    }
    return "$sign₡$number"
}

private fun formatChartValue(value: Float): String = when {
    kotlin.math.abs(value) >= 1_000_000f -> "%.1fmi".format(value / 1_000_000f)
    kotlin.math.abs(value) >= 1_000f -> "%.1fk".format(value / 1_000f)
    else -> "%.0f".format(value)
}

private fun strategyLabel(strategy: CompanyStrategy): String = when (strategy) {
    CompanyStrategy.GROWTH -> "Crescimento e contratação"
    CompanyStrategy.RESEARCH -> "Pesquisa e inovação"
    CompanyStrategy.PROFIT -> "Eficiência e lucro"
    CompanyStrategy.SURVIVAL -> "Preservação de caixa"
}

private fun happeningLabel(type: HappeningType): String = when (type) {
    HappeningType.FESTIVAL -> "Festival na praça"
    HappeningType.BLACKOUT -> "Falha de infraestrutura"
    HappeningType.RESEARCH_GRANT -> "Fomento à pesquisa"
    HappeningType.MARKET_BOOM -> "Expansão de mercado"
}

private fun happeningEffect(type: HappeningType): String = when (type) {
    HappeningType.FESTIVAL -> "Aumenta socialização, motivação e satisfação"
    HappeningType.BLACKOUT -> "Degrada edifícios, motivação e estresse"
    HappeningType.RESEARCH_GRANT -> "Adiciona caixa e tecnologia às empresas ativas"
    HappeningType.MARKET_BOOM -> "Multiplica a demanda dos próximos fechamentos"
}
