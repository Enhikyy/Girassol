package dev.labforge.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.labforge.app.ui.components.*
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.EvolutionViewModel
import java.util.Locale

@Composable
fun EvolutionPane(vm: EvolutionViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(4.dp))
        GlassCard {
            Text("Da prática à evidência", fontSize = 21.sp, color = Lab.Text, fontWeight = FontWeight.Bold)
            Text("Habilidades observadas nas execuções do agente, recuperação de falhas e ajustes medidos no aparelho.", color = Lab.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("Próxima prática: ${vm.nextPractice}", color = Lab.Cyan, fontSize = 13.sp)
            Text("Os pré-requisitos orientam o estudo. Contagens não certificam domínio e não bloqueiam ferramentas.", color = Lab.Muted, fontSize = 11.sp)
            GradientButton("Atualizar evidências", Modifier.fillMaxWidth()) { vm.refresh() }
        }
        SectionHeader("Árvore de habilidades", "Resultados reais · janela de 512 execuções")
        vm.skills.forEach { row ->
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.skill.title, fontWeight = FontWeight.SemiBold, color = Lab.Text, fontSize = 14.sp)
                    Pill(if (row.available) "disponível" else "pré-requisitos", if (row.available) Lab.Green else Lab.Amber)
                }
                if (row.skill.prerequisites.isNotEmpty()) Text("Depende de: ${row.skill.prerequisites.joinToString { id -> dev.labforge.app.core.evolution.SkillTree.skills.first { it.id == id }.title }}", color = Lab.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Sucessos / falhas", "${row.successes} / ${row.failures}")
                KeyValueRow("Recuperações observadas", row.recoveries.toString())
            }
        }
        SectionHeader("Otimização local", "Comparação medida de threads da CPU")
        GlassCard {
            Text("Executa o mesmo prompt duas vezes por configuração, até 64 tokens. A taxa inclui carga do modelo e processamento do prompt. Temperatura, bateria e outros apps afetam os resultados.", color = Lab.Muted, fontSize = 11.sp)
            if (vm.busy) {
                SunflowerSpinner(size = 36.dp)
                GradientButton("Cancelar medição", Modifier.fillMaxWidth()) { vm.cancel() }
            } else GradientButton("Medir modelo configurado", Modifier.fillMaxWidth()) { vm.benchmark() }
            vm.report?.let { report ->
                Spacer(Modifier.height(10.dp))
                Text("${report.optString("model").substringAfterLast('/')} · contexto ${report.optInt("context")}", color = Lab.Text, fontSize = 12.sp)
                val trials = report.optJSONArray("trials")
                if (trials != null) for (i in 0 until trials.length()) {
                    val t = trials.getJSONObject(i)
                    KeyValueRow("${t.optInt("threads")} threads · amostra ${i + 1}", if (t.isNull("error")) String.format(Locale.ROOT, "%.2f tokens/s", t.optDouble("tokensPerSecond")) else "falhou")
                }
                if (!report.isNull("recommendedThreads")) {
                    Text("Melhor mediana nesta execução: ${report.optInt("recommendedThreads")} threads", color = Lab.Green, fontSize = 12.sp)
                    if (!vm.busy) GradientButton("Aplicar recomendação", Modifier.fillMaxWidth()) { vm.applyRecommendation() }
                }
            }
            if (vm.status.isNotBlank()) Text(vm.status, color = Lab.Cyan, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
    }
}
