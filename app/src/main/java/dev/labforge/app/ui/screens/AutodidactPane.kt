package dev.labforge.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.core.AppSelection
import dev.labforge.app.ui.components.AnimatedBars
import dev.labforge.app.ui.components.Console
import dev.labforge.app.ui.components.DonutChart
import dev.labforge.app.ui.components.DonutSegment
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.LabField
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.ProgressRing
import dev.labforge.app.ui.components.PulsingDot
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.StatTile
import dev.labforge.app.ui.components.SunflowerSpinner
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AutodidactViewModel
import dev.labforge.app.vm.LabViewModel
import dev.labforge.app.vm.StudyStage

@Composable
fun AutodidactPane(
    vm: LabViewModel,
    onTrain: () -> Unit,
    study: AutodidactViewModel = viewModel(),
) {
    var theme by remember { mutableStateOf(study.theme) }
    val ctx = LocalContext.current
    val scroll = rememberScrollState()

    LaunchedEffect(study.project) {
        study.project?.let {
            AppSelection.datasetPath = it.txt?.absolutePath
            runCatching { vm.refresh() }
        }
    }

    val stages = listOf(
        StudyStage.RESEARCH to "Pesquisa",
        StudyStage.SYNTHESIS to "Síntese",
        StudyStage.CURRICULUM to "Currículo",
        StudyStage.DATASET to "Dataset",
        StudyStage.TUNING to "Ajuste",
    )
    val currentIndex = stages.indexOfFirst { it.first == study.stage }
    val doneCount = when (study.stage) {
        StudyStage.DONE -> 5
        StudyStage.ERROR, StudyStage.IDLE -> 0
        else -> currentIndex.coerceAtLeast(0)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------- hero
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Lab.Cyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Lab.Cyan, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pesquisa autônoma", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                    Text(
                        "7 fontes + Brave · RAG local · currículo · dataset · auto-tuning",
                        fontSize = 11.sp, color = Lab.Muted, lineHeight = 14.sp,
                    )
                }
                if (study.llmReady) Pill("LLM", Lab.Violet, filled = true) else Pill("extrativo", Lab.Amber)
            }
            Spacer(Modifier.height(14.dp))
            LabField(
                label = "Tema para se especializar",
                value = theme,
                placeholder = "ex.: engenharia de dados com SQL e dbt",
                onValueChange = { theme = it; study.theme = it },
            )
            Spacer(Modifier.height(12.dp))
            Stepper("Tópicos", study.topicCount, listOf(4, 6, 8, 10), Modifier.fillMaxWidth()) { study.topicCount = it }
            Spacer(Modifier.height(8.dp))
            Stepper("Exemplos/tópico", study.perTopic, listOf(2, 4, 6, 8), Modifier.fillMaxWidth()) { study.perTopic = it }
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Treinar LoRA automaticamente ao concluir",
                subtitle = "usa o modelo selecionado e o preset calculado",
                checked = study.autoTrain,
                onCheckedChange = { study.autoTrain = it },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    if (study.running) "Parar" else "Pesquisar e montar",
                    Modifier.weight(1f),
                    if (study.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    enabled = study.running || theme.isNotBlank(),
                ) {
                    if (study.running) study.stop() else {
                        study.theme = theme
                        study.start()
                    }
                }
            }
        }

        // ---------------------------------------------------------- stages
        if (study.stage != StudyStage.IDLE) {
            Spacer(Modifier.height(16.dp))
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    stages.forEachIndexed { i, (stage, label) ->
                        val done = i < doneCount || study.stage == StudyStage.DONE
                        val active = stage == study.stage
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            done -> Lab.Green.copy(alpha = 0.18f)
                                            active -> Lab.Violet.copy(alpha = 0.22f)
                                            else -> Lab.SurfaceHigh
                                        },
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            done -> Lab.Green.copy(alpha = 0.55f)
                                            active -> Lab.Violet.copy(alpha = 0.65f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        },
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = Lab.Green, modifier = Modifier.size(15.dp))
                                } else if (active) {
                                    SunflowerSpinner(20.dp, 8)
                                } else {
                                    Text("${i + 1}", fontSize = 11.sp, color = Lab.Muted)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 11.sp, color = if (done || active) Lab.Text else Lab.Muted, maxLines = 1)
                        }
                        if (i < stages.lastIndex) {
                            Box(
                                Modifier
                                    .padding(bottom = 14.dp)
                                    .width(10.dp)
                                    .height(1.dp)
                                    .background(if (i < doneCount) Lab.Green.copy(alpha = 0.5f) else Lab.Outline),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        progress = study.progress,
                        size = 76.dp,
                        stroke = 7.dp,
                        color = when (study.stage) {
                            StudyStage.ERROR -> Lab.Red
                            StudyStage.DONE -> Lab.Green
                            else -> Lab.Violet
                        },
                        caption = study.stage.label,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(study.status, fontSize = 11.sp, color = Lab.Text, lineHeight = 16.sp)
                        if (study.live.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(study.live.takeLast(120), fontSize = 11.sp, color = Lab.Muted, maxLines = 2)
                        }
                        if (study.running) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingDot(Lab.Green, 6.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("trabalhando em segundo plano", fontSize = 11.sp, color = Lab.Muted)
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- sources
        val b = study.bundle
        if (b != null && b.docs.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionHeader("Fontes coletadas", "${b.docs.size} resultados · ${b.sourceCounts.size} provedores")
            Spacer(Modifier.height(8.dp))
            GlassCard {
                val colors = listOf(Lab.Violet, Lab.Cyan, Lab.Green, Lab.Pink, Lab.Amber, Lab.Indigo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        segments = b.sourceCounts.entries.mapIndexed { i, e ->
                            DonutSegment(e.key, e.value.toFloat(), colors[i % colors.size])
                        },
                        size = 92.dp,
                        stroke = 12.dp,
                        centerLabel = b.docs.size.toString(),
                        centerCaption = "fontes",
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        b.sourceCounts.entries.take(6).forEachIndexed { i, e ->
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(colors[i % colors.size]))
                                Spacer(Modifier.width(6.dp))
                                Text(e.key, fontSize = 11.sp, color = Lab.Text, modifier = Modifier.weight(1f))
                                Text("${e.value}", fontSize = 11.sp, color = Lab.Muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AnimatedBars(
                    b.sourceCounts.values.map { it.toFloat() },
                    Modifier.fillMaxWidth().height(30.dp),
                    Lab.Cyan,
                )
                Spacer(Modifier.height(12.dp))
                b.docs.take(8).forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
                                }
                            }
                            .padding(4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.title, fontSize = 11.sp, color = Lab.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${d.source} · ${d.domain}" + (d.year?.let { " · $it" } ?: ""),
                                fontSize = 11.sp, color = Lab.Cyan,
                            )
                            Text(d.snippet.take(180), fontSize = 11.sp, color = Lab.Muted, maxLines = 2, lineHeight = 13.sp)
                        }
                        Icon(Icons.Rounded.OpenInNew, null, tint = Lab.Muted, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }

        // ---------------------------------------------------------- preset
        study.preset?.let { p ->
            Spacer(Modifier.height(18.dp))
            SectionHeader("Parâmetros redefinidos", "calculados a partir do corpus e do seu aparelho")
            Spacer(Modifier.height(8.dp))
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("rank", p.rank.toString(), Lab.Violet, Modifier.weight(1f))
                    StatTile("epochs", p.epochs.toString(), Lab.Cyan, Modifier.weight(1f))
                    StatTile("ctx", p.ctx.toString(), Lab.Green, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("lr", "%.1e".format(p.lr), Lab.Amber, Modifier.weight(1f))
                    StatTile("tokens", dev.labforge.app.ui.components.humanNumber(p.estimatedTokens), Lab.Pink, Modifier.weight(1f))
                    StatTile("ETA", dev.labforge.app.core.Perf.fmtDuration(p.estimatedMinutes * 60.0), Lab.Indigo, Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Text(p.notes, fontSize = 11.sp, color = Lab.Muted, lineHeight = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("alvos: ${p.targets}", Lab.Violet)
                    if (study.project != null) Pill("dataset salvo", Lab.Green)
                }
            }
        }

        // ---------------------------------------------------------- curriculum
        if (study.topics.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionHeader("Currículo", "${study.topics.count { it.done }} de ${study.topics.size} concluídos")
            Spacer(Modifier.height(8.dp))
            study.topics.forEachIndexed { i, t ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300, delayMillis = i * 60)) + slideInVertically(tween(300, delayMillis = i * 60)) { it / 3 },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (t.done) Lab.Cyan.copy(alpha = 0.07f) else Lab.Surface.copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                if (t.done) Lab.Cyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (t.done) Lab.Cyan.copy(alpha = 0.2f) else Lab.SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t.done) Icon(Icons.Rounded.CheckCircle, null, tint = Lab.Cyan, modifier = Modifier.size(13.dp))
                            else Text("${i + 1}", fontSize = 11.sp, color = Lab.Muted)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(t.title, fontSize = 11.sp, color = Lab.Text, modifier = Modifier.weight(1f))
                        if (t.done) Pill("${t.examples}", Lab.Cyan)
                    }
                }
            }
        }

        // ---------------------------------------------------------- result
        study.project?.let { p ->
            Spacer(Modifier.height(18.dp))
            GlassCard {
                Text("Material pronto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("exemplos", p.examples.toString(), Lab.Cyan, Modifier.weight(1f))
                    StatTile("tópicos", p.topics.size.toString(), Lab.Violet, Modifier.weight(1f))
                    StatTile("fontes", p.sources.size.toString(), Lab.Green, Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Text(p.txt?.name ?: p.jsonl?.name ?: "", fontSize = 11.sp, color = Lab.Muted)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Lab.Violet)
                        .clickable {
                            AppSelection.datasetPath = (p.txt ?: p.jsonl)?.absolutePath
                            AppSelection.preset = p.preset
                            onTrain()
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Treinar LoRA com este material", fontSize = 14.sp, color = Lab.OnPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (study.history.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionHeader("Pesquisas salvas", "toque para recarregar")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                study.history.take(3).forEach { h ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Lab.Surface)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .clickable { study.openHistory(h); theme = h.theme }
                            .padding(9.dp),
                    ) {
                        Column {
                            Text(h.theme.take(28), fontSize = 11.sp, color = Lab.Text, maxLines = 2)
                            Text("${h.docs.size} fontes", fontSize = 11.sp, color = Lab.Muted)
                        }
                    }
                }
            }
        }

        if (study.log.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Console(study.log, maxHeight = 220)
        }

        Spacer(Modifier.height(30.dp))
    }
}
