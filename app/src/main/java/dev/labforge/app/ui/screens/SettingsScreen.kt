package dev.labforge.app.ui.screens

import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.ui.components.GlassCard
import dev.labforge.app.ui.components.GradientButton
import dev.labforge.app.ui.components.KeyValueRow
import dev.labforge.app.ui.components.LabField
import dev.labforge.app.ui.components.Pill
import dev.labforge.app.ui.components.SectionHeader
import dev.labforge.app.ui.components.humanBytes
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.LabViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: LabViewModel,
    onOpenLlamatik: () -> Unit = {},
    onOpenAutonomy: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(0.2f) }
    var maxSteps by remember { mutableStateOf(0) }
    var braveKey by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var pureBlackOled by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var boosting by remember { mutableStateOf(false) }
    var boostResult by remember { mutableStateOf<dev.labforge.app.service.MemoryBooster.Result?>(null) }

    LaunchedEffect(vm.settings) {
        endpoint = vm.settings.endpoint
        apiKey = vm.settings.apiKey
        model = vm.settings.model
        temperature = vm.settings.temperature.toFloat()
        maxSteps = vm.settings.maxSteps
        braveKey = vm.settings.braveApiKey
        githubToken = vm.settings.githubToken
        pureBlackOled = vm.settings.pureBlackOled
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
        Text("modelo do agente, motor nativo e sandbox", fontSize = 12.sp, color = Lab.Muted)

        Spacer(Modifier.height(14.dp))
        GlassCard(padding = 14) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pure Black OLED", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text)
                    Text("Fundo preto real para telas AMOLED; reduz pixels acesos em áreas escuras.", fontSize = 12.sp, color = Lab.Muted)
                }
                Switch(
                    checked = pureBlackOled,
                    onCheckedChange = { enabled ->
                        pureBlackOled = enabled
                        Lab.pureBlack = enabled
                        vm.updateSettings { it.copy(pureBlackOled = enabled) }
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Cérebro do agente", "API compatível com OpenAI")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            LabField("Endpoint", endpoint, placeholder = "https://api.openai.com/v1") { endpoint = it }
            Spacer(Modifier.height(10.dp))
            LabField("API key", apiKey, placeholder = "sk-…", secret = true) { apiKey = it }
            Spacer(Modifier.height(10.dp))
            LabField("Modelo", model, placeholder = "gpt-4o-mini / llama3.1:8b") { model = it }
            Spacer(Modifier.height(6.dp))
            Text(
                "URL final: ${LlmClient.describeNormalized(endpoint)}",
                fontSize = 12.sp,
                color = Lab.Muted,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(14.dp))
            Text("Temperatura · %.2f".format(temperature), fontSize = 12.sp, color = Lab.Muted)
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..1.2f,
                colors = SliderDefaults.colors(
                    thumbColor = Lab.Violet,
                    activeTrackColor = Lab.Violet,
                    inactiveTrackColor = Lab.SurfaceHigh,
                ),
            )
            Stepper("Máximo de passos (0 = autônomo)", maxSteps, listOf(0, 10, 15, 25), Modifier.fillMaxWidth()) { maxSteps = it }
            Spacer(Modifier.height(18.dp))
            Text("Pesquisa e GitHub", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Lab.Text)
            Text(
                "Brave Search entra no autoestudo como fonte extra. O token do GitHub libera busca de código, push, issues e PRs pelo agente. Ficam só neste aparelho.",
                fontSize = 12.sp,
                color = Lab.Muted,
            )
            Spacer(Modifier.height(10.dp))
            LabField("Brave Search API key", braveKey, placeholder = "BSA…", secret = true) { braveKey = it }
            Spacer(Modifier.height(10.dp))
            LabField("GitHub token", githubToken, placeholder = "github_pat_… / ghp_…", secret = true) { githubToken = it }
            Spacer(Modifier.height(14.dp))
            GradientButton(
                "Salvar",
                Modifier.fillMaxWidth(),
                Icons.Rounded.CheckCircle,
            ) {
                vm.updateSettings {
                    it.copy(
                        endpoint = endpoint.trim(),
                        apiKey = apiKey.trim(),
                        model = model.trim(),
                        temperature = temperature.toDouble(),
                        maxSteps = maxSteps,
                        braveApiKey = braveKey.trim(),
                        githubToken = githubToken.trim(),
                        pureBlackOled = pureBlackOled,
                    )
                }
                testResult = "ajustes salvos"
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Lab.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        testResult ?: "Dica: use um endpoint local (Ollama/LM Studio) para privacidade total.",
                        fontSize = 12.sp,
                        color = if (testResult?.contains("erro", true) == true) Lab.Red else Lab.Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (testing) "testando…" else "testar",
                        fontSize = 12.sp,
                        color = Lab.Cyan,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Lab.Cyan.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .border(1.dp, Lab.Cyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !testing) {
                        testing = true
                        testResult = null
                        scope.launch {
                            val r = runCatching {
                                LlmClient(endpoint, apiKey, model, temperature.toDouble()).testConnection()
                            }
                            testing = false
                            testResult = r.getOrElse { "erro: ${it.message}" }
                        }
                    }
                    .padding(11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (testing) "Testando conexão…" else "Testar conexão",
                    fontSize = 12.sp,
                    color = Lab.Cyan,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Motor nativo", "llama.cpp compilado para arm64")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            KeyValueRow("versão", vm.nativeVersion)
            KeyValueRow("ABI", Build.SUPPORTED_ABIS.joinToString(", "))
            KeyValueRow("RAM", "%.1f GB".format(vm.deviceRamGb))
            KeyValueRow("cores", vm.deviceCores.toString())
            KeyValueRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("LoRA nativo", Lab.Cyan)
                Pill("quantize + imatrix", Lab.Green)
                Pill("llama_opt train", Lab.Violet)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Sandbox", "tudo que o agente acessa")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = Lab.Violet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    vm.store.root.absolutePath,
                    fontSize = 12.sp,
                    color = Lab.Text,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            KeyValueRow("modelos", "${vm.models.size} · ${humanBytes(vm.models.sumOf { it.sizeBytes })}")
            KeyValueRow("imatrices", "${vm.imatrices.size} · ${humanBytes(vm.imatrices.sumOf { it.length() })}")
            KeyValueRow("datasets", "${vm.datasets.size}")
            KeyValueRow("saídas", "${vm.outputs.size} · ${humanBytes(vm.outputs.sumOf { it.length() })}")
            KeyValueRow("workspace git", vm.store.workspace().absolutePath)
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Autonomia", "acessibilidade, sono e diário de ações")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text(
                "O agente pode enxergar a tela e operar outros apps através do serviço de " +
                    "acessibilidade. Você ativa e revoga quando quiser; o app não se autoautoriza.",
                fontSize = 12.sp, color = Lab.Muted, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Lab.Cyan.copy(alpha = 0.12f))
                    .border(1.dp, Lab.Cyan.copy(alpha = 0.3f), RoundedCornerShape(11.dp))
                    .clickable { onOpenAutonomy() }
                    .padding(11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Abrir Autonomia", fontSize = 12.sp, color = Lab.Cyan, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Execução em segundo plano", "foreground service + orçamento de RAM")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            KeyValueRow("RAM livre", "%.2f GB".format(dev.labforge.app.service.MemoryGuard.availableGb(vm.getApplication())))
            KeyValueRow("RAM total", "%.1f GB".format(vm.deviceRamGb))
            KeyValueRow("heurística LoRA", "modelo × 1.6 + 0.4 GB")
            KeyValueRow("heurística full FT", "modelo × 4.0 + 0.4 GB")
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Lab.Violet.copy(alpha = 0.14f))
                    .border(1.dp, Lab.Violet.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                    .clickable(enabled = !boosting) {
                        scope.launch {
                            boosting = true
                            boostResult = dev.labforge.app.service.MemoryBooster.boost(vm.getApplication())
                            boosting = false
                        }
                    }
                    .padding(11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (boosting) "Liberando…" else "Forçar liberação de RAM",
                    fontSize = 12.sp, color = Lab.Violet, fontWeight = FontWeight.SemiBold,
                )
            }
            boostResult?.let { r ->
                Spacer(Modifier.height(7.dp))
                Text(
                    "+${r.freedMb} MB · ${r.appsTouched} apps tocados · ${r.cachesMb} MB de cache",
                    fontSize = 12.sp, color = Lab.Green,
                )
                Text(r.note, fontSize = 12.sp, color = Lab.Muted, lineHeight = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Pesquisa gratuita", "APIs sem chave, integradas nativamente")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text(
                "O agente consulta essas fontes em paralelo e guarda tudo no RAG local. " +
                    "Nenhuma conta, chave ou cadastro.",
                fontSize = 12.sp, color = Lab.Muted, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill("Wikipedia", Lab.Cyan)
                Pill("OpenAlex", Lab.Violet)
                Pill("arXiv", Lab.Pink)
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill("Stack Overflow", Lab.Amber)
                Pill("DuckDuckGo", Lab.Green)
                Pill("Semantic Scholar", Lab.Indigo)
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill("GitHub", Lab.Text)
                Pill("Brave Search · chave", Lab.Amber)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Runtime Llamatik", "SDK 2.2.2 · tools · memória")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Text(
                "Camada de agente oficial (ToolRegistry, AgentExecutor, PersistentMemory, AgentStreamChunk) " +
                    "com a tool REST Retrofit e a pesquisa gratuita. O core nativo fica desativado para não " +
                    "colidir com o llama.cpp de treino.",
                fontSize = 12.sp, color = Lab.Muted, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Lab.Cyan.copy(alpha = 0.12f))
                    .border(1.dp, Lab.Cyan.copy(alpha = 0.3f), RoundedCornerShape(11.dp))
                    .clickable { onOpenLlamatik() }
                    .padding(11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Abrir Llamatik Runtime", fontSize = 12.sp, color = Lab.Cyan, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Sobre")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = Lab.Muted, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Girassol ${dev.labforge.app.BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Lab.Text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Estúdio de GGUF/imatrix com LoRA nativo, Codex autônomo e autoestudo. " +
                            "Motor de quantização, treino, tokenização e adaptadores: llama.cpp (MIT). Git: Eclipse JGit. " +
                            "UI: Jetpack Compose. Jobs rodam em foreground service para sobreviver ao background. " +
                            "Nenhum dado sai do aparelho, exceto as chamadas ao endpoint do agente.",
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = Lab.Muted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("llama.cpp", Lab.Violet)
                Pill("JGit", Lab.Cyan)
                Pill("Compose", Lab.Green)
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}
