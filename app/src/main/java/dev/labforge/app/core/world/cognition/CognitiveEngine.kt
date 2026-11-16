package dev.labforge.app.core.world.cognition
import dev.labforge.app.core.world.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow

enum class CognitiveSystem { SYSTEM_1, SYSTEM_2 }
data class BalancedTernaryState(val axes:List<Int>) {
 init { require(axes.size==9 && axes.all { it in -1..1 }) }
 val code:Int get()=axes.foldIndexed(0){i,n,v->n+(v+1)*3.0.pow(i).toInt()}
 companion object {
  const val STATE_COUNT=19_683
  fun from(a:AgentState,minute:Int):BalancedTernaryState {
   fun t(v:Float)=if(v<33f)-1 else if(v>66f)1 else 0
   val n=a.needs
   return BalancedTernaryState(listOf(t(n.energy),t(n.nutrition),t(n.social),t(n.motivation),t(n.satisfaction),t(n.stress),t(a.personality.openness*100f),if(n.energy<18f||n.nutrition<14f||n.stress>85f)1 else 0,if(minute in 540..1079)1 else -1))
  }
 }
}
@Serializable
data class SoulProfile(val values:Map<String,Float> = mapOf("care" to .5f,"curiosity" to .5f,"discipline" to .5f,"sociability" to .5f),val experiences:Long=0) {
 fun absorb(key:String,success:Boolean):SoulProfile { val k=key.takeIf{values.containsKey(it)}?:"care"; return copy(values=values+(k to ((values[k]?:.5f)+(if(success).015f else -.01f)).coerceIn(0f,1f)),experiences=experiences+1) }
}
data class CognitiveInput(val agent:AgentState,val world:WorldState,val nowMinute:Long,val soul:SoulProfile=SoulProfile())
data class CognitiveDecision(val action:ActionType,val confidence:Float,val stateCode:Int,val reason:String,val system:CognitiveSystem=CognitiveSystem.SYSTEM_1,val escalated:Boolean=false)
fun interface System2Reasoner { suspend fun reason(input:CognitiveInput,fast:CognitiveDecision):CognitiveDecision? }

class System1Cognition {
 fun decide(input:CognitiveInput):CognitiveDecision {
  val a=input.agent; val n=a.needs; val minute=(input.nowMinute%1440L).toInt()
  val routine=a.routine.firstOrNull{minute>=it.startMinute&&minute<it.endMinute}?.preferredAction
  val employed=a.employerId?.let{input.world.company(it)?.takeUnless{c->c.bankrupt}}!=null
  val scores=mutableMapOf(
   ActionType.SLEEP to (100f-n.energy)*1.35f+n.stress*.2f,
   ActionType.EAT to (100f-n.nutrition)*1.2f,
   ActionType.SOCIALIZE to (100f-n.social)*(.55f+a.personality.extraversion*.7f),
   ActionType.WORK to if(employed||a.profession==Profession.EDUCATOR||a.profession==Profession.ARTISAN) 28f+n.motivation*.55f+a.personality.conscientiousness*28f-n.stress*.32f else -100f,
   ActionType.RESEARCH to 14f+a.personality.openness*30f+a.personality.ambition*18f+(if(a.profession==Profession.RESEARCHER)26f else 0f)-n.stress*.2f,
   ActionType.LEARN to 20f+a.personality.openness*25f+a.personality.ambition*24f-(if(a.skills.isEmpty())0f else a.skills.values.average().toFloat()*12f),
   ActionType.RELAX to n.stress*.95f+(100f-n.satisfaction)*.32f, ActionType.IDLE to 5f)
  if(n.energy<22f)scores[ActionType.SLEEP]=scores.getValue(ActionType.SLEEP)+100f
  if(n.nutrition<18f)scores[ActionType.EAT]=scores.getValue(ActionType.EAT)+100f
  routine?.let{p->if(p in scores)scores[p]=scores.getValue(p)+(if(p==ActionType.SLEEP||p==ActionType.WORK)30f else 14f);if(p==ActionType.SLEEP){scores[ActionType.WORK]=scores.getValue(ActionType.WORK)*.2f;scores[ActionType.RESEARCH]=scores.getValue(ActionType.RESEARCH)*.35f}}
  val ranked=scores.entries.sortedByDescending{it.value};val best=ranked.first();val second=ranked.getOrNull(1)?.value?:0f
  val confidence=((best.value-second)/maxOf(abs(best.value),1f)).coerceIn(0f,1f)
  return CognitiveDecision(best.key,confidence,BalancedTernaryState.from(a,minute).code,"system1:"+best.key.name.lowercase())
 }
}
data class CrystallizedPolicy(val action:ActionType,val attempts:Int,val successes:Int){val rate:Float get()=if(attempts==0)0f else successes.toFloat()/attempts}
class KahnemanControlLoop {
 private val policies=mutableMapOf<Int,CrystallizedPolicy>()
 @Synchronized fun recall(code:Int)=policies[code]?.takeIf{it.attempts>=3&&it.rate>=.75f}
 @Synchronized fun record(d:CognitiveDecision,success:Boolean){val old=policies[d.stateCode];policies[d.stateCode]=if(old==null||old.action!=d.action)CrystallizedPolicy(d.action,1,if(success)1 else 0) else old.copy(attempts=old.attempts+1,successes=old.successes+if(success)1 else 0)}
}
class CognitiveEngine(private val system1:System1Cognition=System1Cognition(),private val control:KahnemanControlLoop=KahnemanControlLoop(),private val threshold:Float=.28f) {
 fun decideFast(input:CognitiveInput):CognitiveDecision {val fast=system1.decide(input);val learned=control.recall(fast.stateCode)?:return fast;return fast.copy(action=learned.action,confidence=maxOf(fast.confidence,learned.rate),reason="crystallized:"+learned.action.name.lowercase())}
 suspend fun decide(input:CognitiveInput,reasoner:System2Reasoner?,timeoutMs:Long=8000L):CognitiveDecision {val fast=decideFast(input);if(reasoner==null||fast.confidence>=threshold)return fast;val deep=withTimeoutOrNull(timeoutMs.coerceIn(250L,20000L)){reasoner.reason(input,fast)}?:return fast;return deep.copy(confidence=deep.confidence.coerceIn(0f,1f),stateCode=fast.stateCode,system=CognitiveSystem.SYSTEM_2,escalated=true)}
 fun recordOutcome(decision:CognitiveDecision,success:Boolean)=control.record(decision,success)
}
