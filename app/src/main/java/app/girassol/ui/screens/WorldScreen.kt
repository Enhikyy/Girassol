package app.girassol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.girassol.ui.components.ScreenHeader
import app.girassol.vm.AppViewModel
import app.girassol.world.WorldView
import app.girassol.world.WorldZones

@Composable
fun WorldScreen(model: AppViewModel) {
    val snapshot by model.activities.collectAsState()
    var selectedZone by rememberSaveable { mutableStateOf("atrium") }
    val zone = WorldZones.byId(selectedZone)
    val context = LocalContext.current
    val worldView = remember(context) { WorldView(context, model.activityHub) }
    LaunchedEffect(worldView, selectedZone) { worldView.focusZone(selectedZone) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            ScreenHeader("Mundo · OpenGL ES 3.0", "Campus operacional", "Arraste para explorar, faça pinça para aproximar e toque em uma área para focar.")
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { worldView },
                update = { },
            )
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(14.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp,
            ) {
                snapshot.primary?.let { operation ->
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(operation.title, style = MaterialTheme.typography.titleMedium)
                        Text(operation.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        LinearProgressIndicator(progress = { operation.progress }, modifier = Modifier.fillMaxWidth())
                    }
                } ?: Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                    Text("Em repouso no Átrio · nenhuma operação ativa", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(WorldZones.all) { item ->
                FilterChip(
                    selected = selectedZone == item.id,
                    onClick = { selectedZone = item.id },
                    label = { Text(item.title) },
                    leadingIcon = { Box(Modifier.size(9.dp).background(Color(item.color), CircleShape)) },
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(zone.color), CircleShape))
                Column {
                    Text(zone.title, style = MaterialTheme.typography.labelLarge)
                    Text(zone.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
