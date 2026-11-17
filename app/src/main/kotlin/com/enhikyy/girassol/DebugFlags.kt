package com.enhikyy.girassol

/**
 * Chaves de isolamento para diagnóstico de tela preta.
 *
 * Cada recurso problemático tem a sua própria flag. Desativar uma flag isola
 * aquele recurso sem afetar os outros, permitindo achar o culpado binariamente.
 *
 * As flags são apenas dados: não há efeitos colaterais na inicialização da
 * classe (isso partiria previews e testes). Quem quiser registá-las no log
 * chama [logState] explicitamente — é o que [MyApplication] faz no arranque.
 */
object DebugFlags {

    /** Filtro cinematográfico Kodak (AGSL/`RenderEffect`, API 33+). */
    var enableKodakOverlay: Boolean = true

    /** Glassmorphism Haze (blur via `RenderEffect`, API 31+). */
    var enableHaze: Boolean = true

    /** Geofences e agentes animados no `MundoScreen`. */
    var enableMundoAnimations: Boolean = true

    /** Carregamento de modelos `.gguf` (pesado, nunca na main thread). */
    var enableModelLoading: Boolean = true

    /** Regista o estado atual das flags no log do app. */
    fun logState() {
        AppLogger.log(
            "INFO",
            AppLogger.TAG,
            "Flags: kodak=$enableKodakOverlay | haze=$enableHaze | " +
                "mundoAnims=$enableMundoAnimations | modelLoading=$enableModelLoading",
        )
    }

    /** Representação legível, útil em ecrãs de diagnóstico. */
    override fun toString(): String =
        "DebugFlags(kodak=$enableKodakOverlay, haze=$enableHaze, " +
            "mundoAnims=$enableMundoAnimations, modelLoading=$enableModelLoading)"
}