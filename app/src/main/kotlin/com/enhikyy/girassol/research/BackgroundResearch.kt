package com.enhikyy.girassol.research

import android.content.Context
import com.enhikyy.girassol.AppLogger
import com.enhikyy.girassol.core.AutonomyController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pesquisa autónoma em ciclos espaçados. Descobre e cataloga referências;
 * não baixa ficheiros, não altera parâmetros e não treina sem aprovação.
 */
object BackgroundResearch {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val topics = listOf(
        "small language model on-device inference",
        "continual learning memory consolidation",
        "agent-based economic simulation",
        "LoRA parameter-efficient fine tuning",
        "active inference cognitive architecture",
        "mobile AI model quantization",
        "multi-agent reinforcement learning",
    )

    fun start(context: Context) {
        BraveConfig.init(context.applicationContext)
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            AppLogger.log("INFO", "RESEARCH", "Pesquisa autónoma iniciada; intervalo de 10 minutos")
            var index = 0
            while (isActive) {
                AutonomyController.awaitRunning()
                val topic = topics[index++ % topics.size]
                runCatching {
                    val hits = ResearchService.search(topic, maxHits = 5)
                    if (hits.isNotEmpty()) {
                        AppLogger.log(
                            "INFO",
                            "RESEARCH",
                            "Tema '$topic': ${hits.size} referências; fontes=${hits.map { it.source }.distinct().joinToString()}",
                        )
                    } else {
                        AppLogger.log("WARN", "RESEARCH", "Sem resultados para '$topic'")
                    }
                }.onFailure {
                    AppLogger.log("WARN", "RESEARCH", "Pesquisa falhou para '$topic': ${it.message}")
                }
                delay(10L * 60L * 1000L)
            }
        }
    }
}
