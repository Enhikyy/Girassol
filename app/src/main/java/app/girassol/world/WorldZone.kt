package app.girassol.world

data class WorldZone(
    val id: String,
    val title: String,
    val purpose: String,
    val x: Float,
    val z: Float,
    val color: Long,
)

object WorldZones {
    val atrium = WorldZone("atrium", "Átrio", "Conversas e coordenação", 0f, 0f, 0xFF61C7AE)
    val library = WorldZone("library", "Biblioteca", "Autoestudo e fontes reais", -7f, -4f, 0xFF59B8C7)
    val university = WorldZone("university", "Faculdade", "Datasets e especialização", 0f, -8f, 0xFF657DE8)
    val home = WorldZone("home", "Casa", "Sono e consolidação", 7f, -4f, 0xFF91B88A)
    val clinic = WorldZone("clinic", "Consultório", "Diagnóstico e revisão de erros", 7f, 5f, 0xFFE58D82)
    val studio = WorldZone("studio", "Studio", "Codex, projetos e builds", -7f, 5f, 0xFFA18BD4)

    val all = listOf(atrium, library, university, home, clinic, studio)
    fun byId(id: String): WorldZone = all.firstOrNull { it.id == id } ?: atrium
}
