package dev.labforge.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab

@Composable
fun LabProgress(value: Float, color: Color = Lab.Violet, height: Int = 8) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(Lab.SurfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(color, Lab.Cyan),
                    ),
                ),
        )
    }
}

@Composable
fun SelectRow(
    label: String,
    current: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    emptyText: String = "nenhum disponível",
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Lab.Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Lab.Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                    .clickable(enabled = options.isNotEmpty()) { open = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current.ifBlank { emptyText },
                    fontSize = 12.sp,
                    color = if (options.isEmpty()) Lab.Muted else Lab.Text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ExpandMore, null, tint = Lab.Muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .background(Lab.SurfaceHigh),
            ) {
                options.forEachIndexed { i, opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                fontSize = 12.sp,
                                color = if (i == options.indexOf(current)) Lab.Violet else Lab.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(i)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = Lab.Text, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, color = Lab.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Lab.Violet,
                uncheckedThumbColor = Lab.Muted,
                uncheckedTrackColor = Lab.SurfaceHigh,
                uncheckedBorderColor = Lab.Outline,
            ),
        )
    }
}

@Composable
fun Stepper(
    label: String,
    value: Int,
    options: List<Int>,
    modifier: Modifier = Modifier,
    suffix: String = "",
    onSelect: (Int) -> Unit,
) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = Lab.Muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            options.forEach { opt ->
                val active = opt == value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Lab.Violet.copy(alpha = 0.20f) else Lab.Surface)
                        .border(
                            1.dp,
                            if (active) Lab.Violet.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(opt) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$opt$suffix",
                        fontSize = 12.sp,
                        color = if (active) Color.White else Lab.Muted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
