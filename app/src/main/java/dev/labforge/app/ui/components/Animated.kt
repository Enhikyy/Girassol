package dev.labforge.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab
import dev.chrisbanes.haze.hazeSource
import kotlin.math.min

@Composable
fun AnimatedAuroraBackground(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    val c = t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c",
    ).value
    val haze = LocalHazeState.current
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(if (haze != null) Modifier.hazeSource(haze) else Modifier)
                .background(Brush.verticalGradient(if (Lab.pureBlack) listOf(Color.Black, Color.Black, Color.Black) else listOf(Color(0xFF0B0E08), Lab.Bg, Color(0xFF080A06)))),
        ) {
            if (Lab.pureBlack) return@Canvas
            val c4 = Offset(size.width * (0.20f + 0.60f * c), size.height * (0.62f + 0.10f * b))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Lab.Violet.copy(alpha = 0.07f + 0.04f * c), Color.Transparent),
                    center = c4,
                    radius = size.width * (0.55f + 0.15f * a),
                ),
                radius = size.width * (0.55f + 0.15f * a),
                center = c4,
            )
            val c1 = Offset(size.width * (0.05f + 0.18f * a), size.height * (0.02f + 0.10f * b))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Lab.Violet.copy(alpha = 0.20f + 0.06f * a), Color.Transparent),
                    center = c1,
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = c1,
            )
            val c2 = Offset(size.width * (1.02f - 0.16f * b), size.height * (0.28f + 0.14f * a))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Lab.Cyan.copy(alpha = 0.10f + 0.05f * b), Color.Transparent),
                    center = c2,
                    radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = c2,
            )
            val c3 = Offset(size.width * 0.55f, size.height * (1.05f - 0.08f * a))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Lab.Pink.copy(alpha = 0.05f), Color.Transparent),
                    center = c3,
                    radius = size.width * 0.7f,
                ),
                radius = size.width * 0.7f,
                center = c3,
            )
        }
        content()
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 8.dp,
    color: Color = Lab.Violet,
    track: Color = Lab.SurfaceHigh,
    label: String? = null,
    caption: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color, Lab.Cyan, color)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label ?: "${(animated * 100).toInt()}%",
                fontSize = (size.value * 0.20f).coerceIn(12f, 26f).sp,
                fontWeight = FontWeight.Bold,
                color = Lab.Text,
            )
            if (caption != null) {
                Text(caption, fontSize = 12.sp, color = Lab.Muted)
            }
        }
    }
}

data class DonutSegment(val label: String, val value: Float, val color: Color)

@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 14.dp,
    centerLabel: String? = null,
    centerCaption: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "donut",
    )
    val total = segments.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            var start = -90f
            segments.forEach { s ->
                val sweep = 360f * (s.value / total) * animated
                drawArc(
                    color = s.color,
                    startAngle = start,
                    sweepAngle = (sweep - 3f).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - sw, this.size.height - sw),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (centerLabel != null) {
                Text(centerLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
            }
            if (centerCaption != null) {
                Text(centerCaption, fontSize = 12.sp, color = Lab.Muted)
            }
        }
    }
}

@Composable
fun AnimatedBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Lab.Violet,
    highlightMax: Boolean = true,
) {
    val maxV = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val animated = values.mapIndexed { i, v ->
        animateFloatAsState(
            targetValue = (v / maxV).coerceIn(0.02f, 1f),
            animationSpec = tween(650, delayMillis = min(i * 18, 500)),
            label = "bar$i",
        )
    }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val anim = animated[i].value
            val h = anim * size.height
            val isMax = highlightMax && v == maxV
            drawRoundRect(
                color = if (isMax) Lab.Cyan else color.copy(alpha = 0.55f + 0.45f * (v / maxV)),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w.coerceAtLeast(1f), h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color = Lab.Green, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "dot")
    val scale by t.animateFloat(
        0.75f, 1.25f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "s",
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = 0.65f + 0.35f * (scale - 0.75f) / 0.5f }
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun AnimatedNumber(value: Int, modifier: Modifier = Modifier, color: Color = Lab.Text, size: Int = 20) {
    val animated by animateIntAsState(value, tween(700, easing = FastOutSlowInEasing), label = "num")
    Text(
        animated.toString(),
        modifier = modifier,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -1f, 2f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "x",
    )
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(
            Brush.linearGradient(
                colors = listOf(Lab.Surface, Lab.SurfaceHigh, Lab.Surface),
                start = Offset(x * 600f, 0f),
                end = Offset(x * 600f + 240f, 240f),
            ),
        ),
    )
}

/** Small rotating sunflower used while a job/research runs. */
@Composable
fun SunflowerSpinner(size: Dp = 44.dp, petals: Int = 24) {
    val t = rememberInfiniteTransition(label = "sun")
    val angle by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rot",
    )
    val bloom by t.animateFloat(
        0.94f, 1.04f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bloom",
    )
    Box(
        Modifier.size(size).graphicsLayer {
            rotationZ = angle
            scaleX = bloom
            scaleY = bloom
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val base = this.size.minDimension
            val count = petals.coerceIn(6, 36)
            val petalWidth = base * if (count >= 20) 0.075f else 0.11f
            repeat(count) { index ->
                val alternate = if (index % 2 == 0) 1f else 0.84f
                val length = base * 0.43f * alternate
                val color = if (index % 3 == 0) Lab.Amber else if (index % 3 == 1) Lab.Violet else Lab.Pink
                rotate(degrees = index * 360f / count, pivot = center) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.68f))),
                        topLeft = Offset(center.x - petalWidth / 2f, center.y - length),
                        size = Size(petalWidth, length * 0.56f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(petalWidth),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(size * 0.32f)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFE082), Color(0xFF7A4A18), Color(0xFF34200B)))),
        )
    }
}

@Composable
fun MiniMetric(label: String, value: String, accent: Color = Lab.Cyan) {
    Column(Modifier.padding(2.dp)) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Text(label, fontSize = 12.sp, color = Lab.Muted)
    }
}

@Suppress("unused")
@Composable
private fun PreviewBadge(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Lab.Muted)
}
