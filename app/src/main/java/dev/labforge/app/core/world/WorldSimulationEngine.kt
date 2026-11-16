package dev.labforge.app.core.world

import kotlin.math.cos
import kotlin.math.sin

enum class ResidentAction { STUDY, COOPERATE, SOCIALIZE, REST, BUILD }

data class ResidentState(
    val id: Int, val name: String, val x: Float, val z: Float,
    val energy: Float, val social: Float, val knowledge: Float, val focus: Float,
    val action: ResidentAction, val innie: Boolean, val cooperation: Float,
)

data class WorldSnapshot(
    val simulatedMinutes: Long,
    val eventCount: Long,
    val residents: List<ResidentState>,
) {
    val averageEnergy get() = residents.map { it.energy }.average().toFloat()
    val averageSocial get() = residents.map { it.social }.average().toFloat()
    val averageKnowledge get() = residents.map { it.knowledge }.average().toFloat()

    companion object {
        fun initial(): WorldSnapshot {
            val names = listOf("Ada","Nilo","Maya","Theo","Lina","Noah","Iris","Caio","Luna","Davi","Yara","Eli","Ravi","Bia","Téo","Mira")
            val residents = names.mapIndexed { index, name ->
                val angle = index.toFloat() / names.size * (Math.PI * 2.0).toFloat()
                ResidentState(
                    id=index, name=name, x=cos(angle)*2.1f, z=sin(angle)*2.1f,
                    energy=72f+(index%5)*4f, social=48f+(index%7)*5f,
                    knowledge=28f+(index%6)*6f, focus=55f+(index%4)*7f,
                    action=if(index%2==0) ResidentAction.STUDY else ResidentAction.COOPERATE,
                    innie=index%2==0, cooperation=0.65f+(index%4)*0.07f,
                )
            }
            return WorldSnapshot(0,0,residents)
        }
    }
}

/** One real second advances fifteen in-world minutes; catch-up is deterministic after suspension. */
class WorldSimulationEngine {
    fun advance(previous: WorldSnapshot, elapsedSeconds: Double): WorldSnapshot {
        if (elapsedSeconds <= 0.0 || previous.residents.isEmpty()) return previous
        val worldMinutes=(elapsedSeconds*15.0).toLong().coerceAtLeast(0L)
        if(worldMinutes==0L) return previous
        val slices=(worldMinutes/5L).coerceAtMost(105_120L)
        var residents=previous.residents
        var events=previous.eventCount
        repeat(slices.toInt()) { step ->
            residents=residents.map { resident ->
                val next=decide(resident,previous.simulatedMinutes/5L+step)
                if(next.action!=resident.action) events++
                next
            }
        }
        return previous.copy(simulatedMinutes=previous.simulatedMinutes+worldMinutes,eventCount=events,residents=residents)
    }

    private fun decide(r: ResidentState, step: Long): ResidentState {
        val collective=r.cooperation*14f
        val scores=mapOf(
            ResidentAction.REST to (100f-r.energy)*0.82f,
            ResidentAction.STUDY to (100f-r.knowledge)*0.58f+r.focus*0.18f+if(r.innie)8f else 0f,
            ResidentAction.COOPERATE to (100f-r.knowledge)*0.26f+(100f-r.social)*0.34f+collective,
            ResidentAction.SOCIALIZE to (100f-r.social)*0.66f+collective*0.35f,
            ResidentAction.BUILD to r.focus*0.28f+r.knowledge*0.22f+collective*0.45f,
        )
        val action=scores.maxBy { it.value }.key
        val drift=((r.id*17L+step*7L)%19L-9L).toFloat()*0.0025f
        val updated=when(action) {
            ResidentAction.REST -> r.copy(energy=(r.energy+1.25f).coerceAtMost(100f),focus=(r.focus+0.22f).coerceAtMost(100f),social=(r.social-0.04f).coerceAtLeast(0f),innie=false)
            ResidentAction.STUDY -> r.copy(energy=(r.energy-0.13f).coerceAtLeast(0f),focus=(r.focus-0.16f).coerceAtLeast(0f),knowledge=(r.knowledge+0.48f).coerceAtMost(100f),innie=true)
            ResidentAction.COOPERATE -> r.copy(energy=(r.energy-0.10f).coerceAtLeast(0f),knowledge=(r.knowledge+0.28f).coerceAtMost(100f),social=(r.social+0.42f+r.cooperation*0.12f).coerceAtMost(100f),cooperation=(r.cooperation+0.0008f).coerceAtMost(1f),innie=true)
            ResidentAction.SOCIALIZE -> r.copy(energy=(r.energy-0.08f).coerceAtLeast(0f),social=(r.social+0.72f).coerceAtMost(100f),innie=false)
            ResidentAction.BUILD -> r.copy(energy=(r.energy-0.18f).coerceAtLeast(0f),focus=(r.focus+0.16f).coerceAtMost(100f),knowledge=(r.knowledge+0.22f).coerceAtMost(100f),innie=true)
        }
        return updated.copy(x=(updated.x+drift).coerceIn(-8.5f,8.5f),z=(updated.z-drift*0.7f).coerceIn(-8.5f,8.5f))
    }
}
