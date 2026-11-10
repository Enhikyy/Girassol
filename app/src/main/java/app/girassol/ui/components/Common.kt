package app.girassol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.girassol.core.activity.ActivitySnapshot
import app.girassol.core.activity.WorldOperation
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenHeader(eyebrow: String, title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(eyebrow.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(5.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OperationCard(operation: WorldOperation) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(operation.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(operation.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { operation.progress }, modifier = Modifier.fillMaxWidth())
            Text("${(operation.progress * 100).toInt()}% · estado informado pela operação", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ActivityFeed(snapshot: ActivitySnapshot, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        snapshot.operations.asReversed().forEach { OperationCard(it) }
        snapshot.recentEvents.takeLast(6).asReversed().forEach { event ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(9.dp).background(if (event.successful) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.labelLarge)
                    Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(formatTime(event.at), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (snapshot.operations.isEmpty() && snapshot.recentEvents.isEmpty()) {
            Text("Nenhuma operação registrada. Pesquise, converse ou crie um projeto para começar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "${bytes / 1024} KB"
}

fun formatTime(timestamp: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
