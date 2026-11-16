package dev.labforge.app.core.world.cognition

import dev.labforge.app.core.agent.LlmClient
import dev.labforge.app.core.world.ActionType
import dev.labforge.app.core.world.building
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Calls the configured endpoint only when System 1 confidence is below threshold. */
class LlmSystem2Reasoner(private val client:LlmClient):System2Reasoner {
 override suspend fun reason(input:CognitiveInput,fast:CognitiveDecision):CognitiveDecision? {
  val a=input.agent
  val location=a.currentBuildingId?.let{input.world.building(it)?.name}.orEmpty()
  val system=JSONObject().put("role","system").put("content",
   "Você é o Sistema 2 de um NPC numa simulação. Escolha uma única ação permitida considerando necessidades, personalidade, contexto e objetivos. Responda somente JSON: {\"action\":\"ENUM\",\"confidence\":0.0,\"reason\":\"curto\"}.")
  val context=JSONObject()
   .put("agent",a.name).put("profession",a.profession.name).put("location",location)
   .put("needs",JSONObject().put("energy",a.needs.energy).put("nutrition",a.needs.nutrition)
    .put("social",a.needs.social).put("motivation",a.needs.motivation)
    .put("satisfaction",a.needs.satisfaction).put("stress",a.needs.stress))
   .put("personality",JSONObject().put("openness",a.personality.openness)
    .put("conscientiousness",a.personality.conscientiousness).put("extraversion",a.personality.extraversion)
    .put("agreeableness",a.personality.agreeableness).put("ambition",a.personality.ambition))
   .put("soul_values",JSONObject(input.soul.values))
   .put("system1_action",fast.action.name)
   .put("allowed_actions",JSONArray(ActionType.entries.map{it.name}))
  val user=JSONObject().put("role","user").put("content",context.toString())
  return try {
   val reply=client.chat(JSONArray().put(system).put(user),tools=null)
   val parsed=runCatching{JSONObject(reply.content)}.getOrNull()?:return null
   val action=ActionType.entries.firstOrNull{it.name.equals(parsed.optString("action"),true)}?:return null
   fast.copy(action=action,confidence=parsed.optDouble("confidence",.5).toFloat().coerceIn(0f,1f),
    reason="system2:"+parsed.optString("reason","deep reasoning").take(120))
  } catch(cancelled:CancellationException) { throw cancelled }
 }
}
