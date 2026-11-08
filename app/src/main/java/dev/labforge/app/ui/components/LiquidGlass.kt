package dev.labforge.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.labforge.app.ui.theme.Lab
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/** Backdrop the glass surfaces blur. Null (previews/tests) falls back to a solid tint. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

object LiquidGlassDefaults {
    val Style = HazeStyle(
        backgroundColor = Lab.Bg,
        tint = HazeTint(Color(0xFF1B1E14).copy(alpha = 0.52f)),
        blurRadius = 26.dp,
        noiseFactor = 0.05f,
        fallbackTint = HazeTint(Color(0xF01B1E14)),
    )
}

/**
 * Liquid-glass surface: translucent body, specular top highlight and a light-catching
 * rim that fades from white (top-left) to [glow] (bottom-right).
 */
fun Modifier.liquidGlass(shape: Shape, glow: Color = Lab.Violet, intensity: Float = 1f): Modifier = this
    .clip(shape)
    .drawWithCache {
        val body = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.09f * intensity), Color.White.copy(alpha = 0.02f), glow.copy(alpha = 0.06f * intensity)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val specular = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.13f * intensity),
            0.42f to Color.Transparent,
            endY = size.height,
        )
        onDrawBehind {
            drawRect(body)
            drawRect(specular)
        }
    }
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.30f * intensity), Color.White.copy(alpha = 0.04f), glow.copy(alpha = 0.32f * intensity)),
        ),
        shape,
    )

/** Real backdrop blur (API 31+) of whatever is registered as haze source, plus the glass rim. */
fun Modifier.glassBackdrop(shape: Shape, glow: Color = Lab.Violet): Modifier = composed {
    val state = LocalHazeState.current
    this
        .clip(shape)
        .then(if (state != null) Modifier.hazeEffect(state, LiquidGlassDefaults.Style) else Modifier.background(Color(0xF01B1E14)))
        .liquidGlass(shape, glow)
}

/** Periodic diagonal light sweep, like light rolling across curved glass. */
fun Modifier.glassSheen(periodMs: Int = 5600, alpha: Float = 0.12f): Modifier = composed {
    val t = rememberInfiniteTransition(label = "sheen")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMs
                0f at 0 using FastOutSlowInEasing
                1f at (periodMs * 0.32f).toInt()
                1f at periodMs
            },
        ),
        label = "p",
    )
    drawWithContent {
        drawContent()
        if (p in 0.001f..0.999f) {
            val x = (-0.5f + 2f * p) * size.width
            val band = size.width * 0.28f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent),
                    start = Offset(x - band, 0f),
                    end = Offset(x + band, size.height),
                ),
            )
        }
    }
}

/** Springy squash when pressed. */
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.965f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** One-shot spring entrance: rise, fade and settle. */
fun Modifier.springEntrance(delayMs: Long = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        p.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 210f))
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 26.dp.toPx()
        val s = 0.965f + 0.035f * v
        scaleX = s
        scaleY = s
    }
}

/**
 * Equal-width tab row with a liquid indicator: the leading edge springs fast and the
 * trailing edge lags, so the pill stretches toward the new tab and squashes back.
 */
@Composable
fun LiquidTabs(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    inset: Dp = 0.dp,
    glow: Color = Lab.Violet,
    item: @Composable RowScope.(index: Int, active: Boolean) -> Unit,
) {
    val target = selected.coerceIn(0, max(0, count - 1)).toFloat()
    val lead by animateFloatAsState(target, spring(dampingRatio = 0.62f, stiffness = 520f), label = "lead")
    val lag by animateFloatAsState(target, spring(dampingRatio = 0.86f, stiffness = 140f), label = "lag")
    Row(
        modifier.drawBehind {
            if (selected !in 0 until count) return@drawBehind
            val w = size.width / count
            val ins = inset.toPx()
            val l = min(lead, lag) * w + ins
            val r = (max(lead, lag) + 1f) * w - ins
            val stretch = ((r - l) / (w - 2 * ins) - 1f).coerceIn(0f, 1f)
            val h = size.height * (1f - 0.14f * stretch)
            val top = (size.height - h) / 2f
            val cr = CornerRadius(min(corner.toPx(), h / 2f))
            val sz = Size(r - l, h)
            val tl = Offset(l, top)
            drawRoundRect(
                Brush.verticalGradient(listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0.12f)), startY = top, endY = top + h),
                tl, sz, cr,
            )
            drawRoundRect(
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.5f to Color.Transparent, startY = top, endY = top + h),
                tl, sz, cr,
            )
            drawRoundRect(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), glow.copy(alpha = 0.55f)), start = tl, end = Offset(r, top + h)),
                tl, sz, cr, style = Stroke(1.dp.toPx()),
            )
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) item(i, i == selected)
    }
}

@Composable
internal fun rememberBreath(periodMs: Int = 7000): Float {
    val t = rememberInfiniteTransition(label = "breath")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(periodMs, easing = LinearEasing), RepeatMode.Reverse), label = "v")
    return v
}
