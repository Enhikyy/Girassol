package dev.labforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.train.TrainingCheckpoint
import dev.labforge.app.ui.theme.Lab
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Saved training checkpoints plus an explicit, non-fabricated merge capability state. */
@Composable
fun LoraMergingTimeline(checkpoints: List<TrainingCheckpoint>, modifier: Modifier = Modifier) {
    val lora = checkpoints.filter { it.kind == "lora" }.take(8)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Lab.Surface.copy(alpha = 0.84f))
            .border(1.dp, Lab.Violet.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text("Linha do tempo LoRA", color = Lab.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nenhuma fusão de pesos foi executada pelo motor atual. Os itens abaixo são checkpoints de treino, não merges.",
            color = Lab.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (lora.isEmpty()) {
            Text("Nenhum checkpoint LoRA salvo e registrado até agora.", color = Lab.Muted, fontSize = 10.sp)
        } else {
            lora.forEach { checkpoint -> CheckpointRow(checkpoint) }
        }
    }
}

@Composable
private fun CheckpointRow(checkpoint: TrainingCheckpoint) {
    val output = File(checkpoint.outputPath)
    val locale = LocalLocale.current.platformLocale
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (output.isFile) Lab.Green else Lab.Amber))
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(1.dp).height(48.dp).background(Lab.Outline))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(Lab.SurfaceHigh.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(checkpoint.outputName, color = Lab.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", locale).format(Date(checkpoint.savedAt)),
                color = Lab.Muted,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    checkpoint.finalLoss?.let { "loss final %.4f".format(it) } ?: "loss final não reportada",
                    color = if (checkpoint.finalLoss != null) Lab.Cyan else Lab.Muted,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(if (output.isFile) "arquivo salvo" else "arquivo ausente", color = if (output.isFile) Lab.Green else Lab.Amber, fontSize = 9.sp)
            }
            Text("Perda de divergência · não medida", color = Lab.Muted, fontSize = 9.sp)
        }
    }
}
