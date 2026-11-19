package com.enhikyy.girassol.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.DebugFlags
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Health
import com.enhikyy.girassol.data.WorldState
import com.enhikyy.girassol.memory.Realm
import com.enhikyy.girassol.memory.SeveranceMemory
import com.enhikyy.girassol.theme.currentPalette
import com.enhikyy.girassol.ui.*

/**
 * Tela MUNDO.
 *
 * O mundo é desenhado em Canvas: geofences dos dois reinos, agentes em
 * movimento e o agente selecionado destacado. Sobre ele, um card de
 * vidro com os drives e o comutador INNIE/OUTIE.
 */
@Composable
fun MundoScreen() {
    val agents by WorldState.agents.collectAsState()
    val commons by WorldState.commons.collectAsState()
    val selectedId by WorldState.selectedId.collectAsState()
    val selected = agents.firstOrNull { it.id == selectedId } ?: agents.firstOrNull()

    val haze = rememberGirassolHaze()
    val palette = currentPalette()

    // Log fora da composição: uma vez por entrada na tela, não a cada frame.
    LaunchedEffect(Unit) {
        AppLogger.log(
            "INFO",
            AppLogger.TAG,
            "Tela MUNDO composta | agentes=${agents.size} | " +
                "kodak=${DebugFlags.enableKodakOverlay} | sdk=${android.os.Build.VERSION.SDK_INT} | " +
                "haze=${DebugFlags.enableHaze} | animações=${DebugFlags.enableMundoAnimations}",
        )
    }

    Box(Modifier.fillMaxSize()) {
        // Viewport do mundo: gradiente noturno + geofences + agentes.
        // O filtro Kodak envolve SÓ esta camada — lê o que está debaixo dele.
        // A intensidade vem do grão do film stock escolhido (0 no modo OLED).
        KodakFilm(
            enabled = DebugFlags.enableKodakOverlay,
            intensity = palette.grain,
        ) {
            Box(Modifier.fillMaxSize().girassolHazeSource(haze)) {
                WorldCanvas(
                    agents = agents,
                    selectedId = selected?.id,
                    onSelect = { WorldState.select(it) },
                )
            }
        }

        // Card do agente selecionado, no canto inferior esquerdo.
        selected?.let { agent ->
            Box(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                AgentCard(
                    haze = haze,
                    agent = agent,
                    onInnieChange = { WorldState.setInnie(agent.id, it) },
                )
            }
        }

        // Cabeçalho: geração e reino atual.
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            WorldHeader(agents = agents)
        }
    }
}

/** Desenho do mundo em Canvas: geofences, agentes e rasto do selecionado. */
@Composable
private fun WorldCanvas(
    agents: List<Agent>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val worldExtent = 260f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val scale = minOf(width, height) / worldExtent

        // Converte coordenadas do mundo para pixéis do canvas.
        fun screenX(worldX: Float) = centerX + worldX * scale
        fun screenY(worldZ: Float) = centerY + worldZ * scale

        // Fundo calculado fora do Canvas: sempre visível, mesmo com animações desativadas.
        val backgroundBrush = remember(width, height) {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF12202B),
                    Color(0xFF080D12),
                ),
                center = Offset(width / 2f, height / 2f),
                radius = maxOf(width, height) * 0.7f,
            )
        }
        val animationsEnabled = DebugFlags.enableMundoAnimations

        // Registo do primeiro frame: fora do Canvas, uma única vez.
        LaunchedEffect(Unit) {
            AppLogger.log(
                "INFO",
                AppLogger.TAG,
                "MUNDO primeiro frame: ${constraints.maxWidth}x${constraints.maxHeight}px " +
                    "| animações=$animationsEnabled",
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .clickable { }
        ) {
            // Fundo do mundo: sempre desenhado.
            drawRect(brush = backgroundBrush, size = size)

            // Chave de isolamento: desativa geofences e agentes se necessário.
            if (animationsEnabled) {
                // Geofences dos dois reinos.
                SeveranceMemory.geofences.forEach { fence ->
                    val color = when (fence.realm) {
                        Realm.INNIE -> GirassolColors.Innie
                        Realm.OUTIE -> GirassolColors.Outie
                    }
                    drawCircle(
                        color = color.copy(alpha = 0.10f),
                        radius = fence.radius * scale,
                        center = Offset(screenX(fence.x), screenY(fence.z)),
                    )
                    drawCircle(
                        color = color.copy(alpha = 0.35f),
                        radius = fence.radius * scale,
                        center = Offset(screenX(fence.x), screenY(fence.z)),
                        style = Stroke(width = 1.5f),
                    )
                }

                // Agentes.
                agents.forEach { agent ->
                    val isSelected = agent.id == selectedId
                    val radius = if (isSelected) 9f else 6f
                    val tint = when (agent.health) {
                        Health.HEALTHY ->
                            if (agent.isInnie) GirassolColors.Innie else GirassolColors.Outie
                        Health.HALLUCINATING -> GirassolColors.Warning
                        Health.DEGRADED -> GirassolColors.Warning
                        Health.COLLAPSED -> GirassolColors.Error
                    }
                    val position = Offset(screenX(agent.x), screenY(agent.z))

                    if (isSelected) {
                        drawCircle(
                            color = tint.copy(alpha = 0.20f),
                            radius = radius * 3f,
                            center = position,
                        )
                    }
                    drawCircle(color = tint, radius = radius, center = position)
                    drawCircle(
                        color = Color.White.copy(alpha = if (isSelected) 0.9f else 0.35f),
                        radius = radius,
                        center = position,
                        style = Stroke(width = if (isSelected) 2f else 1f),
                    )
                }
            }
        }

        // Camada de toque por agente, para selecionar.
        agents.forEach { agent ->
            val targetX = (centerX + agent.x * scale).toInt()
            val targetY = (centerY + agent.z * scale).toInt()
            Box(
                Modifier
                    .size(44.dp)
                    .padding(0.dp)
                    .offsetInPx(targetX, targetY)
                    .clickable { onSelect(agent.id) }
            )
        }
    }
}

/** Posiciona um Bloco em coordenadas absolutas em pixéis. */
private fun Modifier.offsetInPx(x: Int, y: Int): Modifier =
    this.offset { IntOffset(x - 22, y - 22) }

/** Cabeçalho compacto: população, geração e distribuição dos reinos. */
@Composable
private fun WorldHeader(agents: List<Agent>) {
    val generation by WorldState.generation.collectAsState()
    val innie = agents.count { it.isInnie }

    GirassolSurface(
        shape = RoundedCornerShape(10.dp),
        tint = Color(0xFF18181B).copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderStat("POPULAÇÃO", agents.size.toString())
            HeaderStat("GERAÇÃO", generation.toString())
            HeaderStat("INNIE", "$innie/${agents.size - innie}")
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.45f),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Card de vidro com nome, drives e comutador INNIE/OUTIE. */
@Composable
private fun AgentCard(
    haze: dev.chrisbanes.haze.HazeState,
    agent: Agent,
    onInnieChange: (Boolean) -> Unit,
) {
    GirassolGlass(
        state = haze,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(250.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = agent.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${agent.role} · ${agent.lastIntention}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (agent.isInnie) GirassolColors.Innie else GirassolColors.Outie),
                )
            }

            DriveBar("ENERGIA", agent.drives.energy, GirassolColors.Success)
            DriveBar("SOCIAL", agent.drives.social, GirassolColors.Outie)
            DriveBar("CONHECIMENTO", agent.drives.knowledge, GirassolColors.Warning)
            DriveBar("FOCO", agent.drives.focus, MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (agent.isInnie) "INNIE" else "OUTIE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (agent.isInnie) GirassolColors.Innie else GirassolColors.Outie,
                )
                Switch(
                    checked = agent.isInnie,
                    onCheckedChange = onInnieChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GirassolColors.Innie,
                        uncheckedThumbColor = GirassolColors.Outie,
                    ),
                )
            }
        }
    }
}

/** Barra fina de drive, animada com mola. */
@Composable
private fun DriveBar(label: String, value: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f),
        label = "drive_$label",
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
            Text(
                text = "${(animated * 100).toInt()}%",
                fontSize = 8.sp,
                color = color,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun CommonsMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Lab.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Lab.TextSecondary)
    }
}
