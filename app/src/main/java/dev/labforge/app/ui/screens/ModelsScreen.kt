package dev.labforge.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.model.AssetKind
import dev.labforge.app.ui.Routes
import dev.labforge.app.ui.components.EmptyState
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel

@Composable
fun ModelsScreen(vm: LabViewModel, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAsset(uri, AssetKind.MODEL)
    }

    val filtered = remember(vm.models, query) {
        if (query.isBlank()) vm.models
        else vm.models.filter {
            it.name.contains(query, true) || it.file.name.contains(query, true) ||
                (it.meta?.architecture ?: "").contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modelos", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                Text(
                    "${vm.models.size} arquivos GGUF no cofre",
                    fontSize = 11.sp,
                    color = Lab.Muted,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Lab.Violet.copy(alpha = 0.15f))
                    .border(1.dp, Lab.Violet.copy(alpha = 0.4f), CircleShape)
                    .clickable { picker.launch(arrayOf("*/*")) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, null, tint = Lab.Violet, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Lab.Surface)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Lab.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Lab.Text),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Buscar por nome ou arquitetura", fontSize = 13.sp, color = Lab.Muted)
                    inner()
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Memory,
                title = if (vm.models.isEmpty()) "Cofre vazio" else "Nada encontrado",
                body = if (vm.models.isEmpty())
                    "Importe um arquivo .gguf do armazenamento. O parsing é local e não carrega os pesos na RAM."
                else "Tente outro termo de busca.",
                actionLabel = if (vm.models.isEmpty()) "Importar GGUF" else null,
                onAction = { picker.launch(arrayOf("*/*")) },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.file.absolutePath }) { m ->
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        onClick = { onOpen(m.file.absolutePath) },
                        padding = 14,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Lab.Violet.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Memory, null, tint = Lab.Violet, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    m.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Lab.Text,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${m.meta?.parameterLabel ?: "?"} params · ${m.meta?.architecture ?: "?"} · ${humanBytes(m.sizeBytes)}",
                                    fontSize = 10.5.sp,
                                    color = Lab.Muted,
                                    maxLines = 1,
                                )
                                if (m.error != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(m.error, fontSize = 10.sp, color = Lab.Red, maxLines = 2)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Pill(m.meta?.quantLabel ?: "?", Lab.Cyan)
                                Spacer(Modifier.height(6.dp))
                                Icon(Icons.Rounded.ChevronRight, null, tint = Lab.Muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Importar outro GGUF", Modifier.fillMaxWidth(), Icons.Rounded.Add) {
                        picker.launch(arrayOf("*/*"))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
