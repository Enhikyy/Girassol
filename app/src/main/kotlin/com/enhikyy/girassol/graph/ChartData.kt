package com.enhikyy.girassol.graph

/** Ponto de uma série. */
data class ChartPoint(val x: Float, val y: Float)

/** Série contínua para gráficos de linha e de área. */
data class LineChartData(
    val points: List<ChartPoint>,
    val label: String = "",
) {
    val minY: Float get() = points.minOfOrNull { it.y } ?: 0f
    val maxY: Float get() = points.maxOfOrNull { it.y } ?: 1f

    /** Normaliza a altura de um ponto para 0..1, protegendo contra séries planas. */
    fun normalize(point: ChartPoint): Float {
        val range = (maxY - minY)
        return if (range < 1e-4f) 0.5f else (point.y - minY) / range
    }
}

/** Par de valores para as barras circulares. */
data class PerformanceData(val quality: Float, val speed: Float)

/** Um evento do log estilo terminal. */
data class LogEvent(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class LogLevel { INFO, WARN, ERROR }

/** Estado de um subsistema, para o card de módulos. */
enum class ModuleStatus(val label: String) {
    ACTIVE("Ativa"),
    SCHEDULED("Agendado"),
    IDLE("Ocioso"),
    PENDING("Pendente"),
}

/** Série de evolução cognitiva: aptidão média dos agentes ao longo do tempo. */
fun cognitiveSeries(fitnessValues: List<Float>): LineChartData = LineChartData(
    points = fitnessValues.mapIndexed { index, value -> ChartPoint(index.toFloat(), value) },
    label = "Evolução Cognitiva",
)

/** Projeção de crescimento: extensão da tendência com saturação. */
fun growthProjection(history: List<Float>, horizon: Int = 30): LineChartData {
    if (history.isEmpty()) return LineChartData(emptyList(), "Crescimento")
    val first = history.first()
    val last = history.last()
    val slope = if (history.size > 1) (last - first) / (history.size - 1) else 0.02f
    val points = ArrayList<ChartPoint>(horizon)
    for (i in 0 until horizon) {
        // Curva logística: cresce depressa no início, satura perto do teto.
        val t = i.toFloat() / horizon
        val value = last + slope * i * (1f - t * 0.7f)
        points += ChartPoint(i.toFloat(), value.coerceAtLeast(0.05f))
    }
    return LineChartData(points, "Estimativa de Crescimento")
}

/** Qualidade e velocidade estimadas a partir da saúde do ecossistema. */
fun performanceFrom(healthyRatio: Float, avgFitness: Float): PerformanceData =
    PerformanceData(
        quality = healthyRatio.coerceIn(0f, 1f),
        speed = (avgFitness / 10f).coerceIn(0f, 1f),
    )
