package dev.labforge.app.ui.world

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.world.ActionType
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.WorldSceneTransition
import kotlin.math.sin

/** Native Compose equivalent of a perspective zoom-into-portal page variant. */
@Composable
fun CinematicSceneTransition(
    transition: WorldSceneTransition,
    onFinished: (Long) -> Unit,
) {
    val progress = remember(transition.id) { Animatable(0f) }
    LaunchedEffect(transition.id) {
        progress.animateTo(1f, tween(1_450, easing = FastOutSlowInEasing))
        onFinished(transition.id)
    }
    val value = progress.value
    val envelope = sin(Math.PI * value).toFloat().coerceIn(0f, 1f)
    val accent = if (transition.to == ActionType.SLEEP) Lab.Violet else Lab.Green

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = envelope * 0.92f }
            .background(Color(0xFF05070C).copy(alpha = 0.42f * envelope)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.43f)
            val portalRadius = size.minDimension * (0.10f + value * 0.62f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.32f * envelope), Color.Transparent),
                    center = center,
                    radius = portalRadius,
                ),
                radius = portalRadius,
                center = center,
            )
            repeat(3) { ring ->
                val ringProgress = (value + ring * 0.12f).coerceAtMost(1f)
                drawArc(
                    color = accent.copy(alpha = envelope * (0.72f - ring * 0.16f)),
                    startAngle = -90f + ring * 42f + value * 120f,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(center.x - portalRadius * 0.54f, center.y - portalRadius * 0.54f),
                    size = androidx.compose.ui.geometry.Size(portalRadius * 1.08f, portalRadius * 1.08f),
                    style = Stroke(width = (5f - ring) * envelope + 0.5f, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            Modifier
                .graphicsLayer {
                    val zoom = 0.72f + value * 0.48f
                    scaleX = zoom
                    scaleY = zoom
                    rotationY = (1f - value) * -24f
                    rotationX = (1f - value) * 12f
                    cameraDistance = 18f * density
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Lab.BgElev.copy(alpha = 0.88f))
                .border(1.dp, accent.copy(alpha = 0.48f), RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (transition.to == ActionType.SLEEP) "MODO SONO" else "MODO TRABALHO",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text("${transition.fromLocation} → ${transition.targetLocation}", color = Lab.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("foco do habitante · câmera em transição", color = Lab.Muted, fontSize = 10.sp)
        }
    }
}
