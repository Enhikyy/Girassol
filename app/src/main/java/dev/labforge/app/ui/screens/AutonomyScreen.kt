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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.core.autonomy.ThermalGate
import dev.labforge.app.core.device.DeviceActionRecord
import dev.labforge.app.core.device.DeviceBridge
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GhostButton
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.KeyValueRow
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.PulsingDot
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AutonomyViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Control room for the "body": accessibility state, night cycle, tool switch and the
 * action journal. Everything here is local; nothing is uploaded.
 */
@Composable
fun AutonomyScreen(onBack: () -> Unit) {
    val vm: AutonomyViewModel = viewModel()
    val context = LocalContext.current
    val state = vm.state

    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(4000)
            vm.refresh()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Lab.Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Lab.Muted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Autonomia", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
                Text("olhos, mãos e metabolismo do agente", fontSize = 12.sp, color = Lab.Muted)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ------------------------------------------------------------ status
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(if (state.accessibility) Lab.Green else Lab.Red, 8.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.accessibility) "Acessibilidade ativa" else "Acessibilidade desligada",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lab.Text,
                    )
                    Text(
                        if (state.accessibility) "O agente consegue ler a tela e agir."
                        else "O agente só trabalha dentro do próprio workspace.",
                        fontSize = 12.sp,
                        color = Lab.Muted,
                    )
                }
                Pill(if (state.accessibility) "pronta" else "off", if (state.accessibility) Lab.Green else Lab.Red)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "O Android exige que você habilite o serviço manualmente. Abra as configurações, " +
                    "procure “Girassol — Autonomia” e ative. O app nunca ativa isso sozinho.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Lab.Muted,
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                text = if (state.accessibility) "Revisar permissão" else "Abrir configurações",
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                modifier = Modifier.fillMaxWidth(),
            ) { DeviceBridge.openAccessibilitySettings(context) }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Ferramentas do agente", "9 ações nativas")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TouchApp, null, tint = Lab.Cyan, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Permitir device_* no ReAct", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text)
                    Text(
                        if (state.accessibility) "Disponível quando o serviço está ativo."
                        else "Ative a acessibilidade para as ferramentas funcionarem.",
                        fontSize = 12.sp, color = Lab.Muted,
                    )
                }
                Switch(checked = state.toolsEnabled, onCheckedChange = vm::setToolsEnabled)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill("ui", Lab.Cyan); Pill("tap", Lab.Violet); Pill("type", Lab.Green); Pill("swipe", Lab.Amber)
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill("key", Lab.Pink); Pill("open", Lab.Indigo); Pill("wait", Lab.Cyan)
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill("screenshot", Lab.Muted); Pill("script", Lab.Green)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Ciclo noturno", "consolidação quando o aparelho está ocioso")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = Lab.Indigo, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sono e consolidação", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text)
                    Text("WorkManager · ocioso + carregando", fontSize = 12.sp, color = Lab.Muted)
                }
                Switch(checked = state.nightEnabled, onCheckedChange = vm::setNightEnabled)
            }
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Estado", state.workLabel)
            KeyValueRow("Última execução", formatTime(state.lastRun.at))
            KeyValueRow("Térmica", ThermalGate.label(state.thermal))
            KeyValueRow(
                "Bateria",
                when {
                    state.battery < 0 -> "?"
                    state.charging -> "${state.battery}% · carregando"
                    else -> "${state.battery}%"
                },
            )
            KeyValueRow("RAM livre", "%.2f GB".format(state.ramGb))
            state.lastRun.message.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (state.lastRun.ok) Lab.Green else Lab.Amber,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    text = "Executar agora",
                    icon = Icons.Rounded.PlayArrow,
                    loading = state.busy,
                    modifier = Modifier.weight(1f),
                ) { vm.runNow() }
                GhostButton("Atualizar") { vm.refresh() }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "O ciclo noturno consolida memória e revisões. Treino de LoRA não roda aqui: " +
                    "exige modelo e dataset escolhidos, além de sessão em primeiro plano.",
                fontSize = 12.sp, lineHeight = 16.sp, color = Lab.Muted,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Diário de ações", "evidência local, sem telemetria") {
            Text(
                "limpar",
                fontSize = 12.sp,
                color = Lab.Muted,
                modifier = Modifier.clickable { vm.clearJournal() },
            )
        }
        Spacer(Modifier.height(10.dp))
        GlassCard {
            if (state.journal.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = Lab.Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Nenhuma ação registrada. Peça ao agente algo como “abrir as configurações e tocar em Wi-Fi”.",
                        fontSize = 12.sp, lineHeight = 16.sp, color = Lab.Muted,
                    )
                }
            } else {
                state.journal.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    JournalRow(entry)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("Limites", "o que o Android não permite")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            LimitLine("Habilitar acessibilidade exige o usuário; não existe API para o app se autorizar.")
            LimitLine("WorkManager é melhor-esforço: Doze pode atrasar o ciclo por horas.")
            LimitLine("Automação por acessibilidade não é aceita no Google Play; distribua por sideload.")
            LimitLine("O app não troca o próprio APK: assinatura não é contornável sem root.")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = Lab.Green, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "Ações ficam no diário e nas assinaturas de tela antes/depois.",
                    fontSize = 12.sp, color = Lab.Green,
                )
            }
        }

        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun JournalRow(entry: DeviceActionRecord) {
    Row(verticalAlignment = Alignment.Top) {
        PulsingDot(if (entry.ok) Lab.Green else Lab.Red, 6.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    entry.action,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Lab.Text,
                )
                Spacer(Modifier.width(6.dp))
                Text(formatClock(entry.at), fontSize = 12.sp, color = Lab.Muted)
                Spacer(Modifier.width(6.dp))
                Text("${entry.before}→${entry.after}", fontSize = 12.sp, color = Lab.Muted)
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, fontSize = 12.sp, lineHeight = 16.sp, color = Lab.Muted)
            }
        }
    }
}

@Composable
private fun LimitLine(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", fontSize = 12.sp, color = Lab.Amber)
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = Lab.Muted)
    }
}

private fun formatTime(at: Long): String =
    if (at <= 0L) "nunca" else SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(Date(at))

private fun formatClock(at: Long): String =
    if (at <= 0L) "--:--" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))
