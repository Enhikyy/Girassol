# PROMPT MESTRE v2 — GIRASSOL
Pacote: `com.enhikyy.girassol` · Repo: github.com/Enhikyy/Girassol · Alvo: Android arm64-v8a (somente), Kotlin + Compose + C++ (NDK)

ATUE COMO: Arquiteto Principal, Engenheiro Gráfico Sênior, Engenheiro de ML On-Device (ARM) e Engenheiro de Build. Você escreve código que COMPILA. Se uma API não existir na versão da biblioteca, você consulta a documentação/README da versão exata e adapta.

---

## 0. REGRAS DE TRABALHO (OBRIGATÓRIAS)

1. **Projeto existente.** Já existem `AppLogger`, `DebugFlags`, `MyApplication`, `GirassolApp`, `MundoScreen`, `DashboardScreen`, `KodakOverlay`, `Glass`, `GirassolTheme`. NÃO recrie nem remova o sistema de logs (tag `GIRASSOL_DEBUG`, arquivos em `getExternalFilesDir/logs`). Todo módulo novo registra início, fim, erros e tempo de execução nele.
2. **Fase 0 antes de tudo (seção 0.5):** corrigir os erros de Gradle, o Canvas do `MundoScreen` e a tela preta. Só então siga para a Fase 1.
   **Como usar este arquivo:** cole TUDO no CodeAssist e diga "execute a Fase 0". Quando ela passar no portão, diga "execute a Fase 1", e assim por diante. Uma fase por vez; nunca pule o portão.
3. **Portão de qualidade em CADA fase:** `./gradlew assembleDebug` verde → `./gradlew lint` sem erros → app abre e não fica preto → log sem `FATAL`/`AndroidRuntime`. Se falhar, corrija e repita. Nunca avance com build vermelho.
4. **Versões:** antes de adicionar qualquer dependência, consulte a versão estável mais recente (Google Maven / Maven Central / GitHub releases) e fixe no `libs.versions.toml`. Nunca chute versão. Leia o README da versão para a API correta (ex.: Haze 1.x usa `hazeSource`/`hazeEffect`; 0.x usava `haze`/`hazeChild`).
5. **Sem placeholders, sem `TODO`.** Código completo, com imports. Onde faltar backend, use implementação real simples (não stub vazio) e `DebugFlags` para ligar/desligar.
6. **Regras Compose que quebram build:** nunca chame `remember`, `mutableStateOf` ou função `@Composable` dentro de `Canvas { }`/`drawBehind`/`graphicsLayer { }`. Estado fica fora; o lambda de desenho só lê.
7. **Compatibilidade:** `minSdk = 31`, `targetSdk` mais recente estável. AGSL só em API ≥ 33; blur/RenderEffect só em API ≥ 31. Sempre com fallback VISÍVEL (nunca preto).
8. **Commit por fase** no GitHub com mensagem descritiva.

---

## 0.5 FASE 0 — CONSERTAR O BUILD E A TELA PRETA (execute primeiro)

Projeto no CodeAssist: `girassol-2`. Faça os passos na ordem. Compile ao final de cada um.

### 0.5.1 Erros de Gradle (altere SÓ arquivos Gradle: settings, `app/build.gradle(.kts)`, `libs.versions.toml`)

**Erro A — `Redeclaration: BuildConfig`** em `app/build/build-config/kotlin/com/enhikyy/girassol/BuildConfig.kt`. Dois geradores criam a mesma classe: o do Android (AGP) e um plugin de build-config (ex.: `com.github.gmazzo.buildconfig`).
- Liste todos os plugins do `app/build.gradle`.
- Se houver o plugin extra de BuildConfig, remova-o junto com o bloco `buildConfig { }` dele. Mantenha o do Android com `android { buildFeatures { buildConfig = true } }` se algum código usar `BuildConfig.DEBUG` ou campos dele.
- Se nenhum código usar `BuildConfig`, desative um dos dois geradores.

**Erro B — `Unresolved reference InternalResourceApi` / `org.jetbrains.compose.resources`** em `app/build/compose-resources/.../Res.kt`. O plugin de recursos do Compose Multiplatform gerou o `Res.kt`, mas a biblioteca `components-resources` não está nas dependências.
- Procure no projeto qualquer uso de `Res.` ou `org.jetbrains.compose.resources`.
- Se NÃO houver uso: desative a geração com `compose.resources { generateResClass = never }` e remova o plugin `org.jetbrains.compose` se só servir para isso.
- Se houver uso: adicione `org.jetbrains.compose.components:components-resources` na MESMA versão do plugin `org.jetbrains.compose` (confirme a versão estável no Maven Central).

Rode `./gradlew clean assembleDebug`. Se surgirem novos erros, corrija e compile de novo.

### 0.5.2 Erro de sintaxe em `MundoScreen.kt`
O `if (DebugFlags.enableMundoAnimations) { ... } else { ... }` precisa ficar DENTRO do lambda do `Canvas`, com chaves balanceadas. Regras:
- NUNCA chame `remember`, `mutableStateOf` nem função `@Composable` dentro do `Canvas { }` (é um `DrawScope`).
- Declare `val firstFrameLogged = remember { mutableStateOf(false) }` FORA do Canvas e registre o primeiro frame com `LaunchedEffect(Unit) { AppLogger.log(...) }` ou `Modifier.onGloballyPositioned`.
- Estrutura final:
```kotlin
Canvas(Modifier.fillMaxSize()) {
    if (DebugFlags.enableMundoAnimations) {
        // desenho original existente (fundo, geofences, agentes), sem alterar
    } else {
        drawRect(color = /* cor de fundo VISÍVEL, nunca preto */)
    }
}
```
- Preserve o `Modifier.clickable` original, se existia.

### 0.5.3 Diagnóstico da tela preta
O app já tem `AppLogger` (tag `GIRASSOL_DEBUG`), `DebugFlags` (`enableKodakOverlay`, `enableHaze`, `enableMundoAnimations`, `enableModelLoading`) e logs na pipeline. Garanta que estejam completos:
- Arquivos no aparelho: `Android/data/com.enhikyy.girassol/files/logs/log_*.txt` e `logcat_*.txt`.
- Comando de captura no terminal do CodeAssist (não use `-s`, que esconde crashes):
  `logcat GIRASSOL_DEBUG:V AndroidRuntime:E AGSL:V RenderThread:V libc:F DEBUG:F *:S`
- Instale o APK, abra o app, espere 10 s na tela preta, leia os dois arquivos de log e o logcat, e reporte a causa.

Suspeitas, nesta ordem:
1. **KodakOverlay (AGSL):** `RuntimeShader` só existe em API ≥ 33. Em versão menor, falha ou desenha preto. Confirme `Build.VERSION.SDK_INT >= 33`, `try/catch` na criação do shader, alfa do overlay (nunca preto opaco), uniforms (`resolution`, `time`) com valores válidos e diferentes de zero.
2. **Haze (blur):** exige API ≥ 31. Se o fundo do Haze for preto/transparente, a tela toda fica preta. Fallback = cor sólida semitransparente VISÍVEL.
3. **Cores/tema:** `background`, `surface` e `onSurface` iguais ou todos pretos. Logue as cores efetivas.
4. **Carga de modelo `.gguf` na thread principal** (trava a UI) e **RAM insuficiente** (o Android mata o processo). Use o log de memória e o watchdog da main thread.
5. **Exceção engolida em coroutine:** todo `launch` com `CoroutineExceptionHandler` que loga com `AppLogger`.
6. **Isolamento por flags:** se os logs não apontarem a causa, desligue UMA flag de cada vez (comece por `enableKodakOverlay`, depois `enableHaze`, `enableMundoAnimations`, `enableModelLoading`), recompile e veja qual traz a tela de volta.

Para cada suspeita, corrija a causa e mantenha o fallback visível. O portão da Fase 0 só passa quando a tela MUNDO aparece em API 31, 33 e 34+ (ou, se só houver um aparelho, no aparelho do usuário com a versão do Android registrada no log).

### 0.5.4 Entrega da Fase 0
Informe: causa raiz encontrada, arquivos alterados, se o APK compilou, o caminho do APK, a versão do Android do aparelho (do boot log) e qual flag/correção resolveu a tela preta.

---

## 1. VERDADES TÉCNICAS DE ARM/ANDROID (já decididas, não renegocie)

| Tema | Decisão |
|---|---|
| ABI | `abiFilters += "arm64-v8a"`. Compile o C++ com `-O3 -march=armv8.2-a+dotprod+i8mm+fp16` quando a CPU suportar (detecte em runtime via `getauxval(AT_HWCAP)`/`HWCAP_ASIMDDP`/`HWCAP2_I8MM` e carregue a variante certa da `.so`). |
| Inferência LLM | **llama.cpp** nativo via JNI (baseie no exemplo oficial `examples/llama.android`, fixe um commit). Backend padrão CPU com kernels ARM (KleidiAI/i8mm/dotprod). Detecte GPU Adreno e teste backend OpenCL do ggml no 1º uso. Rode um **auto-tuner de 20s** que mede tokens/s em {CPU, OpenCL} e nº de threads e guarda o melhor. Threads = nº de núcleos big/prime (leia `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`). |
| Modelos | `.gguf`. F16 é aceito, mas o app oferece converter/quantizar para Q4_K_M/Q8_0 para inferência rápida e manter o F16 como "genoma mestre" para treino/merge. |
| 2 modelos juntos | Modelo A pequeno (0.5–1B) = **draft**; modelo B (1–3B) = **alvo**. Use **decodificação especulativa** (A propõe, B verifica): mais velocidade com a mesma qualidade. Também funcionam como par "Professor/Aluno" e "Colegas no Bar". |
| Treino on-device | Em celular, treinar pesos completos de LLM não é viável. Implemente **LoRA/QLoRA em C++ (ggml-opt)** sobre os modelos pequenos, com matrizes LoRA em FP32 e merge periódico no F16. Verifique no commit fixado do llama.cpp o suporte atual a fine-tuning (`examples/training`/`ggml-opt`); se ausente, implemente o LoRA próprio em C++/ggml. |
| Autonomia sobre pesos | Pesos base são **somente leitura (mmap)**. Toda auto-modificação é um **delta versionado** (adapter LoRA ou patch por tensor) + `ChangeJournal`. "Merge" cria uma nova **geração** de arquivo. Isso dá autonomia total sobre os pesos com custo de rollback ≈ 0. |
| Execução de código do coder | Android 10+ proíbe executar binários baixados/gerados no armazenamento do app. O "coder nativo" roda **Lua (LuaJ, JVM puro)** e **JavaScript (QuickJS via quickjs-kt)** embutidos. Agentes escrevem **plugins** Lua/JS que são carregados a quente (isso é a auto-extensão do app sem recompilar). Opcional: ponte Termux via `RUN_COMMAND` para compilar projetos Gradle fora do app. |
| Serviço 24/7 | **NÃO use `dataSync`**: no Android 15 ele tem timeout (~6h/24h). Use `foregroundServiceType="specialUse"` + `FOREGROUND_SERVICE_SPECIAL_USE`, WakeLock parcial, pedido de ignorar otimização de bateria. |
| 3D | Use **Filament** (Google, Vulkan, PBR, IBL, SSAO, SSR, Bloom, TAA, color grading) em vez de Vulkan cru: menos bugs e mesma beleza. Ray tracing real (`VK_KHR_ray_query`) só em GPUs que expõem a extensão: detecte; se existir, ligue um modo híbrido opcional de sombras/reflexos; senão use SSR + sondas de reflexão. Renderize em **resolução dinâmica** (50–100%) e faça upscale (FSR1 ou o upscaler do Filament). |
| Filmagem x efeito | Efeitos AGSL (`RenderEffect`) NÃO afetam `SurfaceView`. Hospede o Filament em **TextureView** dentro de `AndroidView` para o filtro Kodak pegar a cena. |
| Desempenho | Use `PerformanceHintManager` (ADPF) para informar a carga das threads de sim/render, e escala dinâmica de qualidade por tempo de frame. Isso é otimização, não limitação. |
| Memória | Embeddings com `bge-small`/`all-MiniLM` GGUF (384 dims) + **ObjectBox 4.x com índice HNSW** (`@HnswIndex(dimensions = 384)`) para busca vetorial de memórias. Salve o **KV-cache por agente** (`saveState/loadState`) para trocar de agente em milissegundos. |

---

## 2. AUTONOMIA INTEGRAL

O sistema tem autonomia total para: alterar seus próprios parâmetros (`genome.json` versionado), agendar/iniciar/encerrar treinos, criar/remover/promover agentes, escrever e executar plugins Lua/JS, editar/mesclar pesos (como deltas/gerações), escolher que fontes ler (HTTP/HTML/RSS/repositórios), e abrir/fechar suas próprias abas de trabalho. Nenhuma ação exige confirmação humana.

Três mecanismos de infraestrutura são **obrigatórios**. Eles NÃO reduzem a autonomia; só tornam tudo recuperável e depurável:

1. **`AutonomyController`** com botão PAUSAR sempre visível na AppBar e ação "Pausar" na notificação do serviço. Todo loop (sim, treino, LLM, coder) chama `awaitRunning()`. Sem isso, um loop descontrolado trava o celular e você não consegue nem abrir o app para corrigir.
2. **Checkpoint + rollback automático** antes de qualquer modificação de pesos/parâmetros, e reversão automática se o benchmark de canário cair.
3. **`ChangeJournal`** append-only com toda auto-modificação (quem, o quê, antes, depois).

Código completo desses três (use exatamente):

```kotlin
// core/AutonomyController.kt
package com.enhikyy.girassol.core

import com.enhikyy.girassol.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class RunState { RUNNING, PAUSED }

object AutonomyController {
    private val _state = MutableStateFlow(RunState.RUNNING)
    val state: StateFlow<RunState> = _state.asStateFlow()

    fun pause(reason: String) {
        _state.value = RunState.PAUSED
        AppLogger.log("WARN", "AUTONOMY", "PAUSADO: $reason")
    }

    fun resume(reason: String) {
        _state.value = RunState.RUNNING
        AppLogger.log("INFO", "AUTONOMY", "RETOMADO: $reason")
    }

    suspend fun awaitRunning() {
        if (_state.value == RunState.RUNNING) return
        _state.first { it == RunState.RUNNING }
    }
}
```

```kotlin
// core/ChangeJournal.kt
package com.enhikyy.girassol.core

import org.json.JSONObject
import java.io.File

class ChangeJournal(file: File) {
    private val f: File = file.also { it.parentFile?.mkdirs() }

    @Synchronized
    fun record(author: String, target: String, before: String, after: String) {
        val line = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("author", author)
            .put("target", target)
            .put("before", before)
            .put("after", after)
            .toString()
        f.appendText(line + "\n")
    }
}
```

```kotlin
// core/WeightVault.kt
package com.enhikyy.girassol.core

import java.io.File
import java.security.MessageDigest

fun File.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().buffered(1 shl 20).use { ins ->
        val buf = ByteArray(1 shl 20)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

class WeightVault(private val root: File) {
    data class Snapshot(val dir: File, val sha: String, val tag: String)

    init { root.mkdirs() }

    fun snapshot(adapter: File, tag: String): Snapshot {
        val sha = adapter.sha256()
        val dest = File(root, "${System.currentTimeMillis()}_${tag}_${sha.take(12)}/${adapter.name}")
        dest.parentFile!!.mkdirs()
        adapter.copyTo(dest, overwrite = false)
        return Snapshot(dest.parentFile!!, sha, tag)
    }

    fun rollback(target: File, snap: Snapshot) {
        val src = File(snap.dir, target.name)
        require(src.sha256() == snap.sha) { "snapshot corrompido: ${snap.dir}" }
        src.copyTo(target, overwrite = true)
    }
}
```

---

## 3. SERVIÇO 24/7 (código completo)

`AndroidManifest.xml` (acrescente; mantenha `android:name=".MyApplication"`):

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>

<service
    android:name=".core.SimService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="local_ai_simulation_training"/>
</service>
```

```kotlin
// core/SimService.kt
package com.enhikyy.girassol.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.enhikyy.girassol.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SimService : Service() {
    companion object {
        const val ACTION_PAUSE = "girassol.PAUSE"
        const val ACTION_RESUME = "girassol.RESUME"
        private const val CHANNEL = "girassol_sim"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, SimService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var loop: GameLoop? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Girassol em execução", NotificationManager.IMPORTANCE_LOW)
        )
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "girassol:sim").apply { acquire() }
        AppLogger.log("INFO", "SERVICE", "SimService criado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> AutonomyController.pause("notificação")
            ACTION_RESUME -> AutonomyController.resume("notificação")
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (loop == null) {
            loop = GameLoop(World.shared, scope).also { it.start() }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        fun action(label: String, act: String, code: Int): NotificationCompat.Action {
            val pi = PendingIntent.getService(
                this, code, Intent(this, SimService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }
        val paused = AutonomyController.state.value == RunState.PAUSED
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Girassol")
            .setContentText(if (paused) "Pausado" else "Simulação e treino em andamento")
            .setOngoing(true)
            .addAction(if (paused) action("Retomar", ACTION_RESUME, 2) else action("Pausar", ACTION_PAUSE, 1))
            .build()
    }

    override fun onDestroy() {
        loop?.stop()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        AppLogger.log("INFO", "SERVICE", "SimService destruído")
        super.onDestroy()
    }
}
```

---

## 4. LOOP DA SIMULAÇÃO + IA DE INSTINTO (código completo)

```kotlin
// core/GameLoop.kt
package com.enhikyy.girassol.core

import android.content.Context
import com.enhikyy.girassol.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Tempo fixo de 50 ms por tick. Se ficou parado muito tempo (processo morto), recupera com macro-ticks de 1 s. */
class GameLoop(private val world: World, private val scope: CoroutineScope) {
    companion object {
        const val TICK_MS = 50L
        const val MACRO_TICK_MS = 1_000L
        const val MAX_FINE_CATCHUP_MS = 10 * 60_000L
        private const val PREFS = "girassol"
        private const val KEY_LAST_WALL = "last_wall_ms"
    }

    @Volatile var renderingEnabled = true   // false com a tela apagada (modo headless)
    private var job: Job? = null

    fun start(ctx: Context? = null) {
        if (job?.isActive == true) return
        val prefs = ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        job = scope.launch(Dispatchers.Default) {
            var last = prefs?.getLong(KEY_LAST_WALL, 0L)?.takeIf { it > 0 } ?: System.currentTimeMillis()
            var acc = 0L
            var persistCounter = 0
            AppLogger.log("INFO", "LOOP", "GameLoop iniciado; atraso inicial=${System.currentTimeMillis() - last}ms")
            while (isActive) {
                if (AutonomyController.state.value == RunState.PAUSED) {
                    AutonomyController.awaitRunning()
                    last = System.currentTimeMillis()   // tempo pausado não conta
                    acc = 0L
                }
                val now = System.currentTimeMillis()
                acc += (now - last).coerceAtLeast(0L)
                last = now

                if (acc > MAX_FINE_CATCHUP_MS) {
                    val macro = (acc / MACRO_TICK_MS).toInt()
                    repeat(macro) { i ->
                        world.step(MACRO_TICK_MS / 1000f)
                        if (i % 500 == 0) yield()
                    }
                    acc -= macro * MACRO_TICK_MS
                }
                while (acc >= TICK_MS) {
                    world.step(TICK_MS / 1000f)
                    acc -= TICK_MS
                }
                if (++persistCounter % 100 == 0) {
                    prefs?.edit()?.putLong(KEY_LAST_WALL, now)?.apply()
                }
                delay(TICK_MS / 2)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
```

```kotlin
// core/World.kt  (Utility AI = Sistema 1; coletivismo: cooperar rende mais)
package com.enhikyy.girassol.core

import java.util.concurrent.atomic.AtomicReference

enum class Mode { INNIE, OUTIE }

class Agent(val id: Int, val name: String) {
    // 0 = satisfeito, 1 = urgente
    val drives = floatArrayOf(0.2f, 0.2f, 0.2f)   // [ENERGIA, SOCIAL, CONHECIMENTO]
    var x = 0f
    var z = 0f
    var mode = Mode.OUTIE
    var actionName = "Ocioso"
    var coop = 0f                                  // reputação de cooperação
}

data class AgentView(val id: Int, val name: String, val x: Float, val z: Float,
                     val drives: FloatArray, val mode: Mode, val action: String)

interface Action {
    val name: String
    fun utility(a: Agent, w: World): Float
    fun perform(a: Agent, w: World, dt: Float)
}

object Rest : Action {
    override val name = "Descansar"
    override fun utility(a: Agent, w: World): Float = a.drives[0] * a.drives[0]
    override fun perform(a: Agent, w: World, dt: Float) {
        a.drives[0] = (a.drives[0] - 0.08f * dt).coerceAtLeast(0f)
    }
}

object Socialize : Action {
    override val name = "Socializar"
    override fun utility(a: Agent, w: World): Float = a.drives[1] * a.drives[1] + 0.15f * a.coop
    override fun perform(a: Agent, w: World, dt: Float) {
        a.drives[1] = (a.drives[1] - 0.07f * dt).coerceAtLeast(0f)
        a.coop += 0.01f * dt
    }
}

object Study : Action {
    override val name = "Estudar"
    override fun utility(a: Agent, w: World): Float = a.drives[2] * a.drives[2]
    override fun perform(a: Agent, w: World, dt: Float) {
        a.drives[2] = (a.drives[2] - 0.06f * dt).coerceAtLeast(0f)
        a.drives[0] = (a.drives[0] + 0.01f * dt).coerceAtMost(1f)
    }
}

object Help : Action {
    override val name = "Ajudar"
    // Cooperar sobe a utilidade quando há alguém muito necessitado; egoísmo não rende bônus.
    override fun utility(a: Agent, w: World): Float {
        val neediest = w.agents.filter { it !== a }.maxOfOrNull { it.drives.max() } ?: 0f
        return 0.5f * neediest + 0.2f * a.coop
    }
    override fun perform(a: Agent, w: World, dt: Float) {
        val target = w.agents.filter { it !== a }.maxByOrNull { it.drives.max() } ?: return
        val i = target.drives.indices.maxByOrNull { target.drives[it] } ?: return
        target.drives[i] = (target.drives[i] - 0.05f * dt).coerceAtLeast(0f)
        a.coop += 0.03f * dt
    }
}

class World {
    companion object { val shared = World() }

    val agents = ArrayList<Agent>()
    var simTimeMs = 0L
    private val actions: List<Action> = listOf(Rest, Socialize, Study, Help)
    private val snapshot = AtomicReference<List<AgentView>>(emptyList())

    init {
        listOf("Ada", "Turing", "Lovelace").forEachIndexed { i, n ->
            agents += Agent(i, n).also { it.x = i * 2f }
        }
    }

    fun step(dt: Float) {
        simTimeMs += (dt * 1000f).toLong()
        for (a in agents) {
            for (i in a.drives.indices) a.drives[i] = (a.drives[i] + 0.01f * dt).coerceAtMost(1f)
            val best = actions.maxByOrNull { it.utility(a, this) } ?: Rest
            a.actionName = best.name
            best.perform(a, this, dt)
        }
        // Publica uma cópia imutável para a UI/Renderer (thread-safe)
        snapshot.set(agents.map { AgentView(it.id, it.name, it.x, it.z, it.drives.copyOf(), it.mode, it.actionName) })
    }

    fun view(): List<AgentView> = snapshot.get()
}
```

---

## 5. FILA DE PENSAMENTOS (Sistema 2) — código completo

```kotlin
// ai/LlmEngine.kt
package com.enhikyy.girassol.ai

interface LlmEngine : AutoCloseable {
    suspend fun load(modelPath: String, nCtx: Int, nThreads: Int, loraPaths: List<String> = emptyList()): Boolean
    /** Bloqueante. Chame fora da main thread. */
    fun generate(prompt: String, maxTokens: Int, temperature: Float, onToken: (String) -> Unit): String
    fun saveState(path: String)
    fun loadState(path: String)
    fun embed(text: String): FloatArray
}
```
Implemente `LlamaCppEngine : LlmEngine` com JNI sobre o commit fixado do llama.cpp (CMake com `GGML_NATIVE=OFF`, flags ARM acima, `LLAMA_CURL=OFF`). Logue: tempo de carga, tokens/s, tamanho do modelo, backend escolhido. Carregamento SEMPRE fora da main thread.

```kotlin
// ai/PromptQueue.kt
package com.enhikyy.girassol.ai

import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.core.AutonomyController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class PromptQueue(private val engine: LlmEngine, scope: CoroutineScope) {
    private class Req(val prompt: String, val maxTokens: Int, val result: CompletableDeferred<String>)

    private val high = Channel<Req>(Channel.UNLIMITED)   // chat do usuário
    private val low = Channel<Req>(Channel.UNLIMITED)    // pensamentos dos NPCs

    init {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                AutonomyController.awaitRunning()
                val req = select<Req> {
                    high.onReceive { it }
                    low.onReceive { it }
                }
                val t0 = System.nanoTime()
                try {
                    val out = engine.generate(req.prompt, req.maxTokens, 0.7f) { }
                    req.result.complete(out)
                    AppLogger.log("INFO", "LLM", "gerou ${out.length} chars em ${(System.nanoTime() - t0) / 1_000_000}ms")
                } catch (t: Throwable) {
                    AppLogger.log("ERROR", "LLM", "falha na geração", t)
                    req.result.completeExceptionally(t)
                }
            }
        }
    }

    suspend fun ask(prompt: String, maxTokens: Int = 256, urgent: Boolean = false): String {
        val r = Req(prompt, maxTokens, CompletableDeferred())
        (if (urgent) high else low).send(r)
        return r.result.await()
    }
}
```

---

## 6. FILTRO KODAK 70mm (AGSL — código completo e seguro)

```kotlin
// ui/KodakFilm.kt
package com.enhikyy.girassol.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.enhikyy.girassol.AppLogger

private const val KODAK_AGSL = """
uniform shader content;
uniform float2 resolution;
uniform float time;
uniform float intensity;
uniform float quality;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 fc) {
    float2 uv = fc / resolution;
    float2 c = uv - 0.5;
    float r2 = dot(c, c);

    float4 base = float4(content.eval(fc));

    // aberração cromática: cresce com a distância do centro
    float2 off = c * r2 * 14.0 * intensity;
    float cr = content.eval(fc + off).r;
    float cb = content.eval(fc - off).b;
    float3 col = float3(cr, base.g, cb);

    // halation (vermelha) + bloom: anel de 8 taps, só luz acima do limiar
    if (quality > 0.5) {
        float3 glow = float3(0.0);
        for (int i = 0; i < 8; i++) {
            float a = 6.2831853 * float(i) / 8.0;
            float2 d = float2(cos(a), sin(a));
            float3 s1 = float3(content.eval(fc + d * 6.0).rgb);
            float3 s2 = float3(content.eval(fc + d * 14.0).rgb);
            glow += max(s1 - 0.7, 0.0) + max(s2 - 0.7, 0.0) * 0.6;
        }
        glow /= 8.0;
        col += glow * float3(1.0, 0.25, 0.08) * 1.4 * intensity;
        col += glow * 0.35 * intensity;
    }

    // grão a 24 fps, mais visível nas sombras
    float g = hash(fc + floor(time * 24.0) * 17.0) - 0.5;
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    col += g * 0.09 * intensity * (1.0 - lum * 0.6);

    // vinheta
    col *= 1.0 - smoothstep(0.25, 0.85, r2 * 2.2) * 0.45 * intensity;

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col), half(base.a));
}
"""

@Composable
fun KodakFilm(enabled: Boolean, intensity: Float = 1f, highQuality: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled || Build.VERSION.SDK_INT < 33) {
        AppLogger.log("INFO", "KODAK", "desativado (enabled=$enabled, sdk=${Build.VERSION.SDK_INT})")
        content()
        return
    }
    KodakFilmApi33(intensity, highQuality, content)
}

@RequiresApi(33)
@Composable
private fun KodakFilmApi33(intensity: Float, highQuality: Boolean, content: @Composable () -> Unit) {
    val shader = remember {
        runCatching { RuntimeShader(KODAK_AGSL) }
            .onFailure { AppLogger.log("ERROR", "KODAK", "shader AGSL falhou ao compilar", it) }
            .getOrNull()
    }
    if (shader == null) { content(); return }

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { time = ((it / 1_000_000_000.0) % 1000.0).toFloat() }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("intensity", intensity)
                shader.setFloatUniform("quality", if (highQuality) 1f else 0f)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }
    ) { content() }
}
```
Aplique `KodakFilm` apenas ao viewport da aba MUNDO (não ao app inteiro) para preservar desempenho e nitidez de texto. `DebugFlags.enableKodakOverlay` controla `enabled`. Se o tempo de frame passar de 20 ms, o gerenciador de qualidade troca `highQuality=false` automaticamente.

---

## 7. TEMAS (15 film stocks + OLED) — código completo

```kotlin
// ui/ThemeManager.kt
package com.enhikyy.girassol.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FilmTheme(
    val id: String, val label: String,
    val primary: Color, val secondary: Color, val tertiary: Color,
    val background: Color, val surface: Color, val onSurface: Color,
    val grain: Float
)

private fun t(id: String, label: String, p: Long, s: Long, te: Long, bg: Long, sf: Long, on: Long, grain: Float) =
    FilmTheme(id, label, Color(p), Color(s), Color(te), Color(bg), Color(sf), Color(on), grain)

object FilmThemes {
    val all: List<FilmTheme> = listOf(
        t("portra400", "Portra 400", 0xFFE8B89A, 0xFF9DB4A0, 0xFFD9A066, 0xFF16130F, 0xFF1F1B16, 0xFFF1E7DA, 0.8f),
        t("portra160", "Portra 160", 0xFFD9C2B0, 0xFFA8B8B5, 0xFFC9A27E, 0xFF14130F, 0xFF1C1A15, 0xFFEFE6DB, 0.5f),
        t("gold200", "Gold 200", 0xFFF2B33D, 0xFFD97B29, 0xFFE8D26B, 0xFF171208, 0xFF211A0C, 0xFFF7EBCB, 1.0f),
        t("ektar100", "Ektar 100", 0xFFFF5A36, 0xFF2F9E8F, 0xFFFFC857, 0xFF110F0E, 0xFF1A1716, 0xFFF4EDE8, 0.3f),
        t("trix400", "Tri-X 400", 0xFFE0E0E0, 0xFF9A9A9A, 0xFFBDBDBD, 0xFF0E0E0E, 0xFF181818, 0xFFEDEDED, 1.2f),
        t("vision3_500t", "Vision3 500T", 0xFF5FA8D3, 0xFFE07A5F, 0xFFF2CC8F, 0xFF0B1118, 0xFF121A24, 0xFFE6EEF5, 0.9f),
        t("vision3_250d", "Vision3 250D", 0xFFF4A259, 0xFF5B8E7D, 0xFFBC4B51, 0xFF12100C, 0xFF1B1812, 0xFFF3EADF, 0.6f),
        t("e100", "Ektachrome E100", 0xFF3FA7D6, 0xFFFAC05E, 0xFF59CD90, 0xFF0C1114, 0xFF141A1E, 0xFFE9F1F5, 0.3f),
        t("kodachrome64", "Kodachrome 64", 0xFFD7263D, 0xFFF4B942, 0xFF2E86AB, 0xFF130D0D, 0xFF1C1313, 0xFFF5E9E4, 0.5f),
        t("ultramax400", "Ultramax 400", 0xFFFF9F1C, 0xFF2EC4B6, 0xFFE71D36, 0xFF121010, 0xFF1B1717, 0xFFF6EEE6, 1.0f),
        t("colorplus200", "ColorPlus 200", 0xFFE9C46A, 0xFF8AB17D, 0xFFE76F51, 0xFF14120D, 0xFF1D1A12, 0xFFF4EBD9, 1.1f),
        t("doublex5222", "Double-X 5222", 0xFFCFCFCF, 0xFF8C8C8C, 0xFFAFAFAF, 0xFF0A0A0A, 0xFF141414, 0xFFE8E8E8, 1.3f),
        t("hp5", "Ilford HP5", 0xFFD8D8D8, 0xFFA0A0A0, 0xFFC4C4C4, 0xFF101010, 0xFF1A1A1A, 0xFFECECEC, 1.1f),
        t("cinestill800t", "CineStill 800T", 0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFE66D, 0xFF0A0D14, 0xFF111623, 0xFFE8ECF4, 1.0f),
        t("superia400", "Fuji Superia 400", 0xFF7BC96F, 0xFFE9724C, 0xFF5AB1BB, 0xFF0F1210, 0xFF171C18, 0xFFEAF1EA, 0.9f),
        t("oled", "OLED", 0xFFFFFFFF, 0xFFB0B0B0, 0xFF808080, 0xFF000000, 0xFF0A0A0A, 0xFFFFFFFF, 0.0f)
    )
    fun byId(id: String?): FilmTheme? = all.firstOrNull { it.id == id }
}

object ThemeManager {
    private val _theme = MutableStateFlow(FilmThemes.all.first())
    val theme: StateFlow<FilmTheme> = _theme.asStateFlow()

    fun init(ctx: Context) {
        val id = ctx.getSharedPreferences("girassol", Context.MODE_PRIVATE).getString("theme", null)
        FilmThemes.byId(id)?.let { _theme.value = it }
    }

    fun set(ctx: Context, theme: FilmTheme) {
        _theme.value = theme
        ctx.getSharedPreferences("girassol", Context.MODE_PRIVATE).edit().putString("theme", theme.id).apply()
    }
}

@Composable
fun GirassolTheme(content: @Composable () -> Unit) {
    val ft by ThemeManager.theme.collectAsState()
    val scheme = darkColorScheme(
        primary = ft.primary, secondary = ft.secondary, tertiary = ft.tertiary,
        background = ft.background, surface = ft.surface,
        surfaceVariant = lerp(ft.surface, ft.onSurface, 0.08f),
        onBackground = ft.onSurface, onSurface = ft.onSurface,
        onPrimary = ft.background, onSecondary = ft.background, onTertiary = ft.background
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
```
Troca instantânea (StateFlow → recomposição), sem recriar a Activity. O campo `grain` alimenta `intensity` do `KodakFilm`.

---

## 8. MÓDULOS DE IA (especificação detalhada; implemente na ordem das fases)

### 8.1 Cérebro dual
- **Sistema 1:** `World`/`UtilityAI` (seção 4).
- **Sistema 2:** `PromptQueue` + `LlmEngine`. Agentes só "pensam" via LLM quando um evento exige (conflito, conversa, decisão não rotineira); o resto é instinto. Cache de KV por agente.
- **Roteador de Chat:** calcule embedding da mensagem, compare por cosseno com o perfil de cada agente/professor, escolha o melhor; LLM só desempata. Animação "Analisando e roteando..." dura o tempo real do roteamento (mín. 600 ms), depois mostra "<Agente> conectado".

### 8.2 Memória Severance
ObjectBox: `Memory(id, agentId, text, embedding[384] @HnswIndex, mode: INNIE/OUTIE, place, t)`. A busca vetorial filtra por `mode` e pelo geofence onde o agente está (trabalho/faculdade = INNIE; bar/praça = OUTIE).

### 8.3 Aprendizado com recompensa VERIFICÁVEL (substitui "LLM julga LLM")
Auto-avaliação por LLM piora o colapso de modelo. Use verificadores objetivos:
- **Código:** problema + testes unitários gerados/curados; o agente escreve em Lua/JS; o sandbox embutido executa os testes; passa = recompensa 1.
- **Matemática/lógica:** verificador programático (avaliador de expressões, SAT pequeno).
- **Fatos:** checagem cruzada com ≥2 fontes lidas da web/repositórios.
Só exemplos que passam no verificador entram no dataset de treino (**rejection sampling / RFT**). Misture 20–30% de dados "reais" (textos humanos lidos de jornais, fóruns, repositórios) em todo lote para evitar colapso. Meça diversidade (distinct-n) e perplexidade em um conjunto de canário fixo.

### 8.4 Faculdade / Bar / Campeonato / Sono
- **Faculdade:** currículo por grafo de conhecimento + ZPD; professor propõe problemas ligeiramente acima do nível atual; progresso = taxa de acerto verificada.
- **Bar:** dois modelos trocam conversa; um "resumidor" extrai insights; só insights que passam em verificador viram dados.
- **Campeonato:** provas (código, matemática, raciocínio, memória, cooperação) com **Elo/TrueSkill**; o vencedor vira genitor preferencial.
- **Modo Sono (inspirado no cérebro):** *NREM* = consolida memórias (resume, deduplica, reforça as de maior valor, poda o resto); *REM* = recombina memórias em cenários contrafactuais ("sonhos") e treina LoRA com os que passaram no verificador. Executa quando a tela apaga e o sistema está ocioso.

### 8.5 Evolução genética
`EvolutionaryMergeEngine` em C++ (NEON), por tensor, lendo F16 → FP32 em blocos: **SLERP**, **TIES** (trim + elect sign + merge), **DARE** (drop-and-rescale). Busca dos coeficientes por **CMA-ES simplificado** com fitness = nota no benchmark local (seção 8.3). Cada filho nasce como nova geração com `lineage.json` (pais, método, coeficientes, notas). Rejeite filhos abaixo de ambos os pais no canário. Mantenha o arquivo `genome.json` (hiperparâmetros do próprio app) também evoluindo por CMA-ES.

### 8.6 Hospital
Monitor contínuo: perplexidade no canário, taxa de alucinação (fatos verificáveis), repetição/degeneração, deriva de pesos. Diagnóstico → terapia automática: *re-grounding* (retreino curto com dados verificados) ou *rollback* (`WeightVault.rollback`). Mostra botões manuais "Re-grounding" e "Rollback de Pesos" para cada agente doente.

### 8.7 Coder nativo
Aba CODER: editor com realce de sintaxe (Lua/JS/Kotlin), terminal embutido, executor sandbox (LuaJ + quickjs-kt) com timeout e limite de memória do interpretador, e o **loop do agente-programador:** planejar → escrever → executar testes → ler erro → corrigir → registrar. Plugins aprovados nos testes vão para `plugins/` e são carregados a quente pelo app (novas ações de NPC, novos temas, novas provas do campeonato, novas fontes de leitura). Leitor de repositórios: clone via HTTP (zip do GitHub), indexação por embeddings, resumo por arquivo.

### 8.8 Leitura do mundo
Crawler HTTP (OkHttp) com fila, cache em disco, extração de texto (Jsoup), RSS, repositórios (zip), imagens (legenda via modelo de visão pequeno, se houver GGUF multimodal). Rate limit por domínio para não ser bloqueado.

---

## 9. MUNDO 3D (Filament)

- `Engine` + `Scene` + `View` em **TextureView** (para o filtro AGSL pegar a cena).
- Iluminação: IBL (HDR gerado/embutido), sol direcional com sombras em cascata, SSAO, SSR, Bloom, TAA, color grading por tema (LUT 3D gerada do `FilmTheme`).
- Modo híbrido RT opcional quando `VK_KHR_ray_query` existir.
- Agentes: malhas low-poly estilizadas + animação por esqueleto; instancing para multidões; LOD por distância.
- Dia/noite contínuo ligado ao `simTimeMs`; clima (chuva/neblina) afeta humor.
- Modo headless: ao apagar a tela, `renderer` pausa; o `GameLoop` segue. Ao acender, a cena é sincronizada via `World.view()`.
- Resolução dinâmica + upscale; alvo 60 fps; se `frameTime > 20 ms` por 30 quadros, reduz escala 5%.

---

## 10. TELAS E UI

- Abas: MUNDO | AGENTES | CHAT | DASHBOARD | ESCOLA | HOSPITAL | CODER | TREINO. Dock flutuante `RoundedCornerShape(50.dp)`, fundo `#18181B` 75% + Haze blur 20dp (só se API ≥ 31; fallback = cor sólida semitransparente).
- AppBar: ícone de engrenagem (Fontes/Temas) e **botão PAUSAR** do `AutonomyController`.
- **MUNDO:** card glass inferior-esquerdo com nome, barras finas (Energia, Social, Foco) e switch INNIE/OUTIE (vermelho/azul).
- **CHAT:** sem balões coloridos, input flutuante (bordas 24dp), botão enviar só com texto.
- **DASHBOARD:** `LazyVerticalGrid`; cards `#18181B` borda `#27272A`: linha (Evolução Cognitiva, Canvas puro), anéis (Qualidade/Velocidade), log estilo terminal (avisos `#F59E0B`, erros `#EF4444` piscando), área com gradiente (Crescimento), status de módulos, **tokens/s, memória, temperatura, geração atual do modelo, Elo dos agentes**.
- **AGENTES/ESCOLA/HOSPITAL:** listas com divisórias `#27272A`.
- **TREINO:** curva de loss, fila de treinos, linhagem genética (árvore), botão "Forçar sono".
- **Animações:** springs para posição/escala/tamanho; `tween` para cor/opacidade; pressão = escala 0.96 com `spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)`; troca de abas `AnimatedContent` com `fadeIn + slideInHorizontally` (~250 ms).
- **FontManager:** `StateFlow` + `ModalBottomSheet` com as 35 fontes (Manrope, Poppins, Work Sans, Karla, Dongle, Raleway, Inconsolata, Comfortaa, Antic, Actor, Agadasima, Hanken Grotesk, Basic, Cabin, Cantarell, Inter, Hind, Gowun Batang, Jost, Krub, Lato, Nunito, Epilogue, Open Sans, Sintony, Montserrat, Rethink Sans, Space Grotesk, Geist, Sofia Sans, Source Serif 4, Scheherazade New, DM Sans, Roboto, Mulish, Cormorant Garamond), cada nome na própria fonte. O provedor `androidx.compose.ui.text.googlefonts` exige Google Play Services: se ausente, use as fontes empacotadas em `res/font` como fallback.

---

## 11. FASES E PORTÕES

| Fase | Entrega | Portão |
|---|---|---|
| 0 | Seção 0.5: erros de Gradle (BuildConfig, compose-resources), Canvas do `MundoScreen`, diagnóstico da tela preta com os logs | `clean assembleDebug` verde; MUNDO aparece; log sem FATAL |
| 1 | `AutonomyController`, `ChangeJournal`, `WeightVault`, `SimService`, `GameLoop`, `World`, `ThemeManager` (16 temas), botão PAUSAR | build verde; serviço sobrevive 10 min com tela apagada; pausar/retomar funciona |
| 2 | `KodakFilm` + Filament em TextureView + Haze + MUNDO completo | 60 fps estável; filtro visível; fallback sem blur ok |
| 3 | `LlamaCppEngine` (JNI), auto-tuner, `PromptQueue`, decodificação especulativa | modelo carrega fora da main thread; tokens/s no log |
| 4 | ObjectBox + embeddings + memória INNIE/OUTIE + roteador de chat | busca vetorial retorna memórias corretas por modo |
| 5 | Faculdade, Bar, verificadores, Campeonato (Elo), coleta/leitura web | benchmark local roda e pontua |
| 6 | LoRA on-device, Modo Sono, Hospital, `EvolutionaryMergeEngine` | filho gerado, avaliado, com `lineage.json`; rollback testado |
| 7 | Coder nativo (Lua/JS), plugins a quente, leitor de repositórios | agente passa testes e plugin carrega |
| 8 | Dashboard, Treino, FontManager, polimento, 15 temas validados | auditoria final (seção 12) |

## 12. AUDITORIA FINAL ANTES DE ENTREGAR CADA FASE
- Compilou do zero (`./gradlew clean assembleDebug`)?
- Nenhum `remember`/`@Composable` dentro de lambda de desenho?
- Todas as APIs conferidas na versão fixada (Haze, Filament, ObjectBox, quickjs-kt, LuaJ)?
- Todo loop longo chama `AutonomyController.awaitRunning()`?
- Toda mudança de pesos/parâmetros passa por `WeightVault` + `ChangeJournal`?
- Nada pesado na main thread (carga de modelo, I/O, treino)?
- Log da fase está completo e o app não fica preto em API 31, 33 e 34+?
- Entregue: arquivos criados/alterados, resultado do build, tokens/s medido e próximos passos.
