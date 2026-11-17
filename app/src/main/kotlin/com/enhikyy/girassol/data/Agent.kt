package com.enhikyy.girassol.data

/** Estado de saúde de um agente, do ponto de vista do AI Hospital. */
enum class Health { HEALTHY, HALLUCINATING, DEGRADED, COLLAPSED }

/** Um agente do ecossistema Girassol. */
data class Agent(
    val id: Long,
    val name: String,
    val role: String,
    val drives: Drives = Drives(),
    val isInnie: Boolean = true,
    val x: Float = 0f,
    val z: Float = 0f,
    val generation: Int = 0,
    val health: Health = Health.HEALTHY,
    /** Aptidão acumulada em vida; alimenta o motor evolutivo. */
    val fitness: Float = 0f,
    /** Última intenção decidida pelo Sistema 1, para o Dashboard. */
    val lastIntention: String = "—",
) {
    /** Aplica deltas de drives e devolve o agente atualizado. */
    fun withDrives(newDrives: Drives): Agent {
        return this.copy(drives = newDrives)
    }

    /** Reinicia os drives mas mantém identidade, posição e genoma. */
    fun revived(): Agent {
        val freshDrives = Drives(energy = 0.8f, social = 0.6f, knowledge = drives.knowledge, focus = 0.7f)
        return this.copy(
            drives = freshDrives,
            health = Health.HEALTHY,
        )
    }

    /** Posição do agente como par, para os consumidores que trabalham no plano. */
    val position: Pair<Float, Float> get() = x to z
}
