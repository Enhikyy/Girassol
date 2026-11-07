package dev.labforge.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab
import kotlin.math.max
import kotlin.math.min

@Composable
fun AuroraBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0C07), Lab.Bg, Color(0xFF080A05)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Lab.Violet.copy(alpha = 0.17f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.04f),
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.12f, size.height * 0.04f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Lab.Cyan.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width * 1.02f, size.height * 0.30f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 1.02f, size.height * 0.30f),
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A1D12), Color(0xFF0F110B)),
                    start = Offset.Zero,
                    end = Offset(900f, 700f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = Lab.Text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Lab.Muted)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color = Lab.Violet, filled: Boolean = false) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (filled) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, tween(120), label = "press")
    val alpha by animateColorAsState(
        if (enabled && !loading) Color.Transparent else Color.Black.copy(alpha = 0.35f),
        label = "btn",
    )
    Row(
        modifier
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Lab.Violet, Lab.Indigo))
                else Brush.horizontalGradient(listOf(Lab.SurfaceHigh, Lab.SurfaceHigh)),
            )
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.16f else 0.05f), shape)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled && !loading,
            ) { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(alpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (loading) "Processando…" else text,
                    color = if (enabled) Color.White else Lab.Muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                if (icon != null && !loading) {
                    Spacer(Modifier.width(8.dp))
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
                if (icon == null && !loading) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowForward, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Lab.Outline, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (enabled) Lab.Text else Lab.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatTile(label: String, value: String, accent: Color = Lab.Violet, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Lab.Surface.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = Lab.Muted, maxLines = 1)
    }
}

@Composable
fun MiniBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Lab.Violet,
    highlightMax: Boolean = true,
) {
    val data = remember(values) { values }
    Canvas(modifier) {
        if (data.isEmpty()) return@Canvas
        val maxV = data.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (data.size - 1)) / data.size
        data.forEachIndexed { i, v ->
            val h = (v / maxV).coerceIn(0.02f, 1f) * size.height
            val isMax = highlightMax && v == maxV
            drawRoundRect(
                color = if (isMax) Lab.Cyan else color.copy(alpha = 0.55f + 0.45f * (v / maxV)),
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(max(1f, w), h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Lab.Cyan,
    showDots: Boolean = true,
) {
    Canvas(modifier) {
        if (points.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.18f),
                start = Offset(0f, size.height * 0.6f),
                end = Offset(size.width, size.height * 0.6f),
                strokeWidth = 1.dp.toPx(),
            )
            return@Canvas
        }
        val minV = points.min()
        val maxV = points.max()
        val span = (maxV - minV).takeIf { it > 1e-6f } ?: 1f
        fun x(i: Int) = i.toFloat() / (points.size - 1) * size.width
        fun y(v: Float) = size.height - ((v - minV) / span) * (size.height * 0.82f) - size.height * 0.06f

        val line = Path()
        points.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }

        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), endY = size.height),
        )
        drawPath(line, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

        if (showDots) {
            points.forEachIndexed { i, v ->
                if (i == points.lastIndex || i == 0) {
                    drawCircle(color, 3.dp.toPx(), Offset(x(i), y(v)))
                }
            }
        }
    }
}

@Composable
fun Console(
    text: String,
    modifier: Modifier = Modifier,
    maxHeight: Int = 260,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) {
        runCatching { scroll.animateScrollTo(scroll.maxValue) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF08080D))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
    ) {
        Text(
            text.ifBlank { "aguardando…" },
            modifier = Modifier
                .heightIn(max = maxHeight.dp)
                .verticalScroll(scroll)
                .padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = Color(0xFFB9E7C9),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Lab.Violet.copy(alpha = 0.10f))
                .border(1.dp, Lab.Violet.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Lab.Violet, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Lab.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.sp, color = Lab.Muted, textAlign = TextAlign.Center, lineHeight = 18.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            GradientButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Lab.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Lab.Violet.copy(alpha = 0.22f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) Lab.Violet.copy(alpha = 0.45f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) Color.White else Lab.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun LabField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secret: Boolean = false,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Lab.Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = Lab.Muted.copy(alpha = 0.55f)) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Lab.Text),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lab.Violet.copy(alpha = 0.65f),
                unfocusedBorderColor = Lab.Outline,
                cursorColor = Lab.Violet,
                focusedContainerColor = Color(0xFF0D0D16),
                unfocusedContainerColor = Color(0xFF0D0D16),
            ),
        )
    }
}

@Composable
fun KeyValueRow(key: String, value: String, accent: Color = Lab.Text) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(key, fontSize = 11.5.sp, color = Lab.Muted, modifier = Modifier.weight(0.42f))
        Text(
            value,
            fontSize = 11.5.sp,
            color = accent,
            modifier = Modifier.weight(0.58f),
            fontFamily = FontFamily.Monospace,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HorizontalChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

fun humanBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "${bytes} B" else String.format("%.2f %s", v, units[i])
}

fun humanNumber(v: Long): String = when {
    v >= 1_000_000_000 -> String.format("%.2fB", v / 1e9)
    v >= 1_000_000 -> String.format("%.1fM", v / 1e6)
    v >= 1_000 -> String.format("%.1fK", v / 1e3)
    else -> v.toString()
}

fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
