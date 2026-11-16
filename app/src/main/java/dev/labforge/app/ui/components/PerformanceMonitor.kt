package dev.labforge.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab
import kotlin.math.cos
import kotlin.math.sin

private const val GAUGE_MAX_TPS = 120.0

/** Compose-native radial gauge; values come exclusively from AgentViewModel token usage. */
@Composable
fun PerformanceGauge(
    tokensPerSecond: Double,
    running: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
) {
    val safeTps = tokensPerSecond.takeIf { it.isFinite() && it > 0 } ?: 0.0
    val progress by animateFloatAsState(
        targetValue = (safeTps / GAUGE_MAX_TPS).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "throughput",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.075f
            val inset = stroke * 1.2f
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            drawArc(
                color = Lab.SurfaceHigh,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Lab.Violet, Lab.Cyan, Lab.Green)),
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (progress > 0.005f) {
                val angle = Math.toRadians((135.0 + 270.0 * progress))
                val radius = arcSize.width / 2f
                val center = Offset(inset + radius, inset + radius)
                val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                val diamond = Path().apply {
                    moveTo(tip.x, tip.y - stroke * 0.8f)
                    lineTo(tip.x + stroke * 0.8f, tip.y)
                    lineTo(tip.x, tip.y + stroke * 0.8f)
                    lineTo(tip.x - stroke * 0.8f, tip.y)
                    close()
                }
                drawPath(diamond, Color.White.copy(alpha = 0.9f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (safeTps <= 0) "—" else if (safeTps >= 100) "%.0f".format(safeTps) else "%.1f".format(safeTps),
                color = if (running) Lab.Green else Lab.Text,
                fontSize = (size.value * 0.18f).coerceIn(8f, 17f).sp,
                fontWeight = FontWeight.Bold,
            )
            Text("t/s", color = Lab.Muted, fontSize = (size.value * 0.13f).coerceIn(7f, 11f).sp)
        }
    }
}

@Composable
fun PerformanceMonitorDetails(tokensPerSecond: Double, running: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Lab.Surface.copy(alpha = 0.9f))
            .border(1.dp, Lab.Cyan.copy(alpha = 0.18f), RoundedCornerShape(17.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerformanceGauge(tokensPerSecond, running, size = 78.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Throughput de inferência", color = Lab.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                if (running) "Atualizado quando o provedor reporta uso de tokens." else "Última taxa medida na sessão do agente.",
                color = Lab.Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text("GPU/NPU · utilização não exposta por fonte autorizada", color = Lab.Amber, fontSize = 9.5.sp)
            Text("Escala radial 0–120 t/s · sem estimativas de hardware", color = Lab.Muted, fontSize = 9.sp)
        }
    }
}
