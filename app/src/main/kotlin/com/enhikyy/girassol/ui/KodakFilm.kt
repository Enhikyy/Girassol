package com.enhikyy.girassol.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.DebugFlags

/**
 * Filtro "filme 70mm" — grain, halation, aberração cromática e vinheta.
 *
 * Este filtro **lê o conteúdo que envolve** (`uniform shader content`), ao
 * contrário de um shader aplicado como `Paint`: o `RenderEffect` recebe o
 * resultado da subárvore, por isso o overlay nunca pode tapar a interface.
 *
 * ## Custo controlado (a causa da tela preta)
 *
 * O AGSL corre **por pixel** e cada `content.eval()` é uma leitura de textura
 * da subárvore. A primeira versão fazia 1 + 16 + 3 = 20 amostras por pixel em
 * resolução nativa: num ecrã de 1080×2400 são ~52 milhões de leituras por
 * frame. O RenderThread não terminava dentro do vsync, o frame nunca era
 * apresentado e o resultado visível era um ecrã parado/negro.
 *
 * Correções aplicadas aqui:
 * - o anel de halation passou de 8×2 para 4 amostras, e a aberração cromática
 *   reutiliza a amostra de base em vez de reler;
 * - [highQuality] é **adaptativo**: começa ligado e desliga-se sozinho quando o
 *   tempo entre frames passa de [SLOW_FRAME_MS] (o comportamento que a
 *   documentação prometia mas não existia);
 * - o relógio do grão é atualizado a ~[GRAIN_FPS] em vez de a cada frame, e
 *   para quando a app não está em `RESUMED` — sem isto o shader voltava a
 *   desenhar para sempre em segundo plano;
 * - o efeito é removido por completo ([KODAK_DISABLE_AFTER_SLOW_FRAMES] frames
 *   lentos consecutivos), voltando ao conteúdo puro.
 *
 * Degradação garantida — em nenhum ramo a tela fica preta:
 * - abaixo de API 33, sem `RuntimeShader`, o filtro é simplesmente omitido;
 * - se o AGSL falhar a compilação, o conteúdo aparece tal como é;
 * - [DebugFlags.enableKodakOverlay] desliga o filtro sem tocar no resto;
 * - se o aparelho não aguentar o custo, o filtro desliga-se sozinho.
 */
private const val GRAIN_FPS = 24f
private const val SLOW_FRAME_MS = 20.0
private const val KODAK_DISABLE_AFTER_SLOW_FRAMES = 90

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
    float2 res = max(resolution, float2(1.0));
    float2 uv = fc / res;
    float2 c = uv - 0.5;
    float r2 = dot(c, c);

    float4 base = float4(content.eval(fc));

    // Aberração cromática: duas leituras, simétrica e crescente com o raio.
    float2 off = c * r2 * 10.0 * intensity;
    float cr = content.eval(fc + off).r;
    float cb = content.eval(fc - off).b;
    float3 col = float3(cr, base.g, cb);

    // Halation: 4 taps na diagonal, apenas luz acima do limiar.
    if (quality > 0.5) {
        float3 glow = float3(0.0);
        float d = 9.0;
        glow += max(float3(content.eval(fc + float2( d,  d)).rgb) - 0.7, 0.0);
        glow += max(float3(content.eval(fc + float2(-d,  d)).rgb) - 0.7, 0.0);
        glow += max(float3(content.eval(fc + float2( d, -d)).rgb) - 0.7, 0.0);
        glow += max(float3(content.eval(fc + float2(-d, -d)).rgb) - 0.7, 0.0);
        glow *= 0.25;
        col += glow * float3(1.0, 0.25, 0.08) * 1.4 * intensity;
        col += glow * 0.35 * intensity;
    }

    // Grão a 24 fps, mais visível nas sombras.
    float g = hash(fc + floor(time * 24.0) * 17.0) - 0.5;
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    col += g * 0.09 * intensity * (1.0 - lum * 0.6);

    // Vinheta.
    col *= 1.0 - smoothstep(0.25, 0.85, r2 * 2.2) * 0.45 * intensity;

    col = clamp(col, 0.0, 1.0);
    // O alfa vem do conteúdo: onde nada foi desenhado continua transparente,
    // por isso o efeito nunca transforma a área num retângulo preto.
    return half4(half3(col), base.a);
}
"""

/**
 * Aplica o filtro Kodak ao [content].
 *
 * Use **apenas no viewport** (a cena do MUNDO), nunca sobre a app inteira: o
 * texto pequeno perde nitidez e o custo por frame sobe.
 *
 * @param enabled interruptor do chamador (ver [DebugFlags.enableKodakOverlay]).
 * @param intensity força global do efeito, 0..1.5 — vem do `grain` do film stock.
 * @param highQuality liga o anel de halation de 4 taps. Desliga-se sozinho
 *        quando o tempo de frame passa dos 20 ms.
 */
@Composable
fun KodakFilm(
    enabled: Boolean,
    intensity: Float = 1f,
    highQuality: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Checagem inline de SDK_INT: o lint rastreia a guarda direta no if (val intermediária
    // 'supported' causava NewApi falso-positivo em KodakFilmApi33).
    if (!enabled ||
        !DebugFlags.enableKodakOverlay ||
        intensity <= 0.01f ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    ) {
        // Sem filtro o conteúdo aparece tal como é — nunca há tela preta.
        // O motivo fica registado pelo chamador (ver MundoScreen).
        content()
        return
    }

    KodakFilmApi33(
        intensity = intensity,
        highQuality = highQuality,
        content = content,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun KodakFilmApi33(
    intensity: Float,
    highQuality: Boolean,
    content: @Composable () -> Unit,
) {
    // Um único shader compilado por composição do filtro.
    val shader = remember {
        runCatching { RuntimeShader(KODAK_AGSL) }
            .onFailure { AppLogger.log("ERROR", "KODAK", "AGSL falhou a compilar", it) }
            .getOrNull()
    }

    if (shader == null) {
        // AGSL indisponível neste dispositivo: conteúdo sem filtro.
        content()
        return
    }

    AppLogger.logOnce("kodak-shader-ok", "INFO", "KODAK", "Shader AGSL compilado; filtro ativo")

    // O relógio do grão vive no estado, fora do lambda de desenho, e só avança
    // a ~24 fps — o shader quantiza o tempo em floor(time*24) de qualquer forma.
    var time by remember { mutableFloatStateOf(0f) }

    // Qualidade adaptativa e desligar de emergência, decididos fora do desenho.
    var adaptiveQuality by remember { mutableStateOf(highQuality) }
    var slowFrames by remember { mutableIntStateOf(0) }
    var disabled by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // `resumed` é lido pelo laço de frames, mas não é estado de composição:
    // uma variável simples evita recompor o viewport a cada pausa/retoma.
    val resumed = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed.set(
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(shader) {
        val intervalNanos = (1_000_000_000.0 / GRAIN_FPS).toLong()
        var lastGrainNanos = 0L
        var lastFrameNanos = 0L

        while (true) {
            withFrameNanos { nanos ->
                if (!resumed.get()) {
                    // Em segundo plano não há nada a desenhar: o relógio pára.
                    lastFrameNanos = 0L
                    return@withFrameNanos
                }

                // Monitor de tempo de frame: se o RenderThread passa dos 20 ms
                // de forma persistente, o efeito é a causa — baixa a qualidade.
                if (lastFrameNanos != 0L) {
                    val frameMs = (nanos - lastFrameNanos) / 1_000_000.0
                    if (frameMs > SLOW_FRAME_MS) {
                        slowFrames++
                        if (slowFrames > 8 && adaptiveQuality) {
                            adaptiveQuality = false
                            AppLogger.log(
                                "WARN", "KODAK",
                                "frame ${frameMs.toInt()}ms: halation desligado (modo económico)",
                            )
                        }
                        if (slowFrames > KODAK_DISABLE_AFTER_SLOW_FRAMES && !disabled) {
                            disabled = true
                            AppLogger.log(
                                "WARN", "KODAK",
                                "filtro desligado: $slowFrames frames lentos consecutivos",
                            )
                        }
                    } else if (slowFrames > 0) {
                        slowFrames--
                    }
                }
                lastFrameNanos = nanos

                if (nanos - lastGrainNanos >= intervalNanos) {
                    lastGrainNanos = nanos
                    time = ((nanos / 1_000_000_000.0) % 1_000.0).toFloat()
                }
            }
        }
    }

    if (disabled) {
        content()
        return
    }

    val quality = adaptiveQuality

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val w = size.width
                val h = size.height
                // Uniforms só com valores válidos: resolução nunca é zero.
                if (w > 0f && h > 0f) {
                    shader.setFloatUniform("resolution", w, h)
                    shader.setFloatUniform("time", time)
                    shader.setFloatUniform("intensity", intensity.coerceIn(0f, 1.5f))
                    shader.setFloatUniform("quality", if (quality) 1f else 0f)
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(shader, "content")
                        .asComposeRenderEffect()
                }
            },
    ) {
        content()
    }
}