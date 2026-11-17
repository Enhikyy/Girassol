package com.enhikyy.girassol.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.data.Agent
import com.enhikyy.girassol.data.Health
import com.enhikyy.girassol.data.WorldState
import com.enhikyy.girassol.graph.LineChartData
import com.enhikyy.girassol.graph.LogEvent
import com.enhikyy.girassol.graph.LogLevel
import com.enhikyy.girassol.graph.ModuleStatus
import com.enhikyy.girassol.graph.PerformanceData
import com.enhikyy.girassol.graph.cognitiveSeries
import com.enhikyy.girassol.graph.growthProjection
import com.enhikyy.girassol.graph.performanceFrom
import com.enhikyy.girassol.hospital.AIHospital
import com.enhikyy.girassol.memory.SeveranceMemory
import com.enhikyy.girassol.ui.GirassolColors
import com.enhikyy.girassol.AppLogger

/** Base fixa dos cards do dashboard. */
private val cardShape = RoundedCornerShape(12.dp)

/**
 * Tela DASHBOARD: cinco cards num grid de duas colunas.
 *
 * Todos os números derivam do estado real da simulação — não há séries
 * aleatórias. A evolução cognitiva é a aptidão média, o log é gerado pelo que
 * aconteceu no mundo, e o estado dos módulos reflete o que está mesmo ligado.
 */
@Composable
fun DashboardScreen() {
    AppLogger.log("DEBUG", "GIRASSOL_DEBUG", "Tela DashboardScreen composta")
    val agents by WorldState.agents.collectAsState()
    val tick by WorldState.tick.collectAsState()
    val generation by WorldState.generation.collectAsState()
    val memories by SeveranceMemory.counts.collectAsState()
    val therapies by AIHospital.therapiesApplied.collectAsState()

    val healthy = agents.count { it.health == Health.HEALTHY }
    val healthyRatio = if (agents.isEmpty()) 0f else healthy.toFloat() / agents.size
    val avgFitness = if (agents.isEmpty()) 0f else agents.map { it.fitness }.average().toFloat()

    // Série de aptidão: um ponto por agente, ordenada — a curva mostra a
    // distribuição da população, não uma tendência inventada.
    val cognitive = remember(agents, tick) {
        cognitiveSeries(agents.map { it.fitness }.sorted())
    }
    val growth = remember(agents, tick) {
        growthProjection(agents.map { it.drives.total() }.sorted())
    }
    val performance = remember(healthyRatio, avgFitness) {
        performanceFrom(healthyRatio, avgFitness)
    }

    val events = remember(agents, tick, generation, therapies) {
        buildEvents(agents, generation, therapies)
    }
    val modules = remember(agents, memories, generation) {
        mapOf(
            "Inferência Local" to ModuleStatus.ACTIVE,
            "Fila de Prompts" to ModuleStatus.ACTIVE,
            "Serviço de Simulação" to ModuleStatus.ACTIVE,
            "GameLoop" to if (tick > 0) ModuleStatus.ACTIVE else ModuleStatus.PENDING,
            "Memória Severance" to if (memories.values.sum() > 0) ModuleStatus.ACTIVE else ModuleStatus.IDLE,
            "Módulo de Evolução" to if (generation > 0) ModuleStatus.ACTIVE else ModuleStatus.IDLE,
            "Universidade" to ModuleStatus.SCHEDULED,
            "AI Hospital" to if (agents.any { it.health != Health.HEALTHY }) ModuleStatus.ACTIVE else ModuleStatus.IDLE,
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text(
                    text = "DASHBOARD",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Tick $tick · geração $generation · ${agents.size} agentes",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        item(span = { GridItemSpan(2) }) {
            DashboardCard("Evolução Cognitiva", "Aptidão por agente") {
                LineChart(data = cognitive, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            DashboardCard("Performance", "Qualidade / Velocidade") {
                CircularBars(data = performance)
            }
        }

        item {
            DashboardCard("Log de Eventos", "Tempo real") {
                EventLog(events = events)
            }
        }

        item(span = { GridItemSpan(2) }) {
            DashboardCard("Estimativa de Crescimento", "Projeção 30 passos") {
                AreaChart(data = growth, color = MaterialTheme.colorScheme.primary)
            }
        }

        item(span = { GridItemSpan(2) }) {
            DashboardCard("Status dos Módulos", "Saúde do sistema") {
                ModuleList(modules = modules)
            }
        }
    }
}

/** Constrói o log a partir do estado observado. */
private fun buildEvents(
    agents: List<Agent>,
    generation: Int,
    therapies: Int,
): List<LogEvent> {
    val events = ArrayList<LogEvent>(24)
    if (generation > 0) {
        events += LogEvent(LogLevel.INFO, "Geração $generation iniciada pelo motor evolutivo")
    }
    agents.filter { it.health == Health.HALLUCINATING }.forEach {
        events += LogEvent(LogLevel.WARN, "${it.name}: alucinação detectada — re-grounding sugerido")
    }
    agents.filter { it.health == Health.DEGRADED }.forEach {
        events += LogEvent(LogLevel.WARN, "${it.name}: degradação de pesos (drives ${(it.drives.total() * 100).toInt()}%)")
    }
    agents.filter { it.health == Health.COLLAPSED }.forEach {
        events += LogEvent(LogLevel.ERROR, "${it.name}: colapso — energia esgotada")
    }
    if (therapies > 0) {
        events += LogEvent(LogLevel.INFO, "$therapies terapias aplicadas no AI Hospital")
    }
    if (events.isEmpty()) {
        events += LogEvent(LogLevel.INFO, "Ecossistema estável — nenhum incidente")
    }
    events += LogEvent(LogLevel.INFO, "${agents.size} agentes activos no mundo")
    return events.takeLast(20).reversed()
}

/** Card base: superfície #18181B com borda #27272A. */
@Composable
private fun DashboardCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(GirassolColors.CardBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
        HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        content()
    }
}

/** Gráfico de linha em Canvas puro, com área suave por baixo. */
@Composable
private fun LineChart(data: LineChartData, color: Color) {
    if (data.points.size < 2) {
        EmptyChart()
        return
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(130.dp),
    ) {
        val pad = 6f
        val usableWidth = size.width - pad * 2
        val usableHeight = size.height - pad * 2
        val stepX = usableWidth / (data.points.size - 1)

        // Grade horizontal discreta.
        repeat(4) { index ->
            val y = pad + usableHeight * index / 3f
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(pad, y),
                end = Offset(size.width - pad, y),
                strokeWidth = 1f,
            )
        }

        val linePath = Path()
        val areaPath = Path()
        data.points.forEachIndexed { index, point ->
            val x = pad + stepX * index
            val y = pad + usableHeight * (1f - data.normalize(point))
            if (index == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, pad + usableHeight)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(pad + stepX * (data.points.size - 1), pad + usableHeight)
        areaPath.close()

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.22f), Color.Transparent),
            ),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 2f, cap = StrokeCap.Round),
        )

        // Ponto final destacado.
        val lastX = pad + stepX * (data.points.size - 1)
        val lastY = pad + usableHeight * (1f - data.normalize(data.points.last()))
        drawCircle(color = color, radius = 3f, center = Offset(lastX, lastY))
    }
}

/** Gráfico de área com gradiente esmaecido. */
@Composable
private fun AreaChart(data: LineChartData, color: Color) {
    if (data.points.size < 2) {
        EmptyChart()
        return
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(130.dp),
    ) {
        val pad = 6f
        val usableWidth = size.width - pad * 2
        val usableHeight = size.height - pad * 2
        val stepX = usableWidth / (data.points.size - 1)

        val path = Path()
        path.moveTo(pad, pad + usableHeight)
        data.points.forEachIndexed { index, point ->
            val x = pad + stepX * index
            val y = pad + usableHeight * (1f - data.normalize(point))
            path.lineTo(x, y)
        }
        path.lineTo(pad + stepX * (data.points.size - 1), pad + usableHeight)
        path.close()

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.02f)),
            ),
        )
        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(pad, pad + usableHeight),
            end = Offset(size.width - pad, pad + usableHeight),
            strokeWidth = 1f,
        )
    }
}

@Composable
private fun EmptyChart() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "à espera de dados",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
    }
}

/** Duas barras circulares animadas com mola. */
@Composable
private fun CircularBars(data: PerformanceData) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CircularBar("QUALIDADE", data.quality, GirassolColors.Success)
        CircularBar("VELOCIDADE", data.speed, GirassolColors.Info)
    }
}

@Composable
private fun CircularBar(label: String, value: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 160f),
        label = "bar_$label",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            val stroke = 6f
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(animated * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** Log estilo terminal, com erros a piscar suavemente. */
@Composable
private fun EventLog(events: List<LogEvent>) {
    val transition = rememberInfiniteTransition(label = "blink")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blinkAlpha",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(events) { event ->
                val (color, prefix) = when (event.level) {
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) to "INFO"
                    LogLevel.WARN -> GirassolColors.Warning to "WARN"
                    LogLevel.ERROR -> GirassolColors.Error.copy(alpha = blink) to "ERROR"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = prefix,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        text = event.message,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 11.sp,
                        color = color.copy(alpha = color.alpha * 0.85f),
                    )
                }
            }
        }
    }
}

/** Lista de módulos com um ponto de estado colorido. */
@Composable
private fun ModuleList(modules: Map<String, ModuleStatus>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        modules.forEach { (name, status) ->
            val color = when (status) {
                ModuleStatus.ACTIVE -> GirassolColors.Success
                ModuleStatus.SCHEDULED -> GirassolColors.Info
                ModuleStatus.IDLE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                ModuleStatus.PENDING -> GirassolColors.Warning
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                    Text(
                        text = name,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
                Text(
                    text = status.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
            }
        }
    }
}
