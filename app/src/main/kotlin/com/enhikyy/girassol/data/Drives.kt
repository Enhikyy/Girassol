package com.enhikyy.girassol.data

import kotlin.math.abs

/**
 * Os quatro drives que alimentam o Sistema 1.
 *
 * Todos em 0..1. São o único estado interno que a Utility AI consulta —
 * manter isso pequeno é o que permite simular muitos agentes por tick.
 */
data class Drives(
    val energy: Float = 0.5f,
    val social: Float = 0.5f,
    val knowledge: Float = 0.3f,
    val focus: Float = 0.5f,
) {
    /** Média ponderada: sobrevivência pesa mais que foco. */
    fun total(): Float = energy * 0.4f + social * 0.25f + knowledge * 0.2f + focus * 0.15f

    fun clamped(): Drives = Drives(
        energy = energy.coerceIn(0f, 1f),
        social = social.coerceIn(0f, 1f),
        knowledge = knowledge.coerceIn(0f, 1f),
        focus = focus.coerceIn(0f, 1f),
    )

    /** Aplica deltas e devolve um novo [Drives] já limitado. */
    fun plus(
        energy: Float = 0f,
        social: Float = 0f,
        knowledge: Float = 0f,
        focus: Float = 0f,
    ): Drives {
        val raw = Drives(
            energy = this.energy + energy,
            social = this.social + social,
            knowledge = this.knowledge + knowledge,
            focus = this.focus + focus,
        )
        return raw.clamped()
    }

    /** Distância de Chebyshev ao estado ideal — usada na aptidão. */
    fun imbalance(): Float = abs(energy - 0.7f) + abs(social - 0.6f) + abs(knowledge - 0.5f)
}
