package dev.labforge.app.core.world.cognition
import dev.labforge.app.core.world.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.sqrt

@Serializable enum class MemoryMode { INNIE, OUTIE }
@Serializable enum class MemoryTier { WORKING, EPISODIC, SEMANTIC, SOUL }
@Serializable data class ScopedMemory(val id:String,val agentId:String,val mode:MemoryMode,val locationId:String?,val sourceKey:String,val text:String,val createdMinute:Long,val importance:Float,val emotionalIntensity:Float,val tier:MemoryTier,val embedding:List<Float> = emptyList())
@Serializable data class AgentMemoryDocument(val memories:List<ScopedMemory> = emptyList(),val soul:SoulProfile=SoulProfile())
data class MemorySearchResult(val memory:ScopedMemory,val score:Float)

/** Persona filtering happens before ranking, preventing innie/outie memory leakage. */
class SeveranceMemoryManager(private val directory:File,private val json:Json=Json{ignoreUnknownKeys=true;encodeDefaults=true}) {
 private val cache=mutableMapOf<String,AgentMemoryDocument>()
 @Synchronized fun currentMode(agent:AgentState,world:WorldState):MemoryMode {
  val kind=agent.currentBuildingId?.let{world.building(it)?.kind}
  return if(kind in WORK_CONTEXTS&&agent.needs.stress<82f&&agent.needs.energy>8f)MemoryMode.INNIE else MemoryMode.OUTIE
 }
 @Synchronized fun remember(agent:AgentState,world:WorldState,text:String,importance:Float=.5f,emotionalIntensity:Float=0f,embedding:List<Float> = emptyList(),sourceKey:String=""):ScopedMemory? {
  val clean=text.trim().take(1200);if(clean.isBlank())return null
  val doc=document(agent.id);if(sourceKey.isNotBlank()&&doc.memories.any{it.sourceKey==sourceKey})return null
  val memory=ScopedMemory(agent.id+"-"+world.elapsedMinutes+"-"+doc.memories.size,agent.id,currentMode(agent,world),agent.currentBuildingId,sourceKey,clean,world.elapsedMinutes,importance.coerceIn(0f,1f),emotionalIntensity.coerceIn(0f,1f),if(importance>=.82f||emotionalIntensity>=.82f)MemoryTier.SEMANTIC else MemoryTier.EPISODIC,embedding.take(1536).map{it.coerceIn(-1f,1f)})
  val kept=(doc.memories+memory).filter{m->val age=(world.elapsedMinutes-m.createdMinute).coerceAtLeast(0L);age<=30L*1440L||m.importance>=.8f||m.emotionalIntensity>=.72f}.sortedWith(compareByDescending<ScopedMemory>{it.importance+it.emotionalIntensity}.thenByDescending{it.createdMinute}).take(500).sortedByDescending{it.createdMinute}
  save(agent.id,doc.copy(memories=kept));return memory
 }
 @Synchronized fun recall(agent:AgentState,world:WorldState,query:String,queryEmbedding:List<Float> = emptyList(),limit:Int=8):List<MemorySearchResult>{
  val mode=currentMode(agent,world);val tokens=tokenize(query)
  return document(agent.id).memories.asSequence().filter{it.agentId==agent.id&&it.mode==mode}.map{m->
   val age=(world.elapsedMinutes-m.createdMinute).coerceAtLeast(0L);val recent=exp(-age.toDouble()/(7.0*1440.0)).toFloat();val lexical=overlap(tokens,tokenize(m.text));val vector=cosine(queryEmbedding,m.embedding);val relevance=if(queryEmbedding.isNotEmpty()&&m.embedding.isNotEmpty())vector else lexical
   MemorySearchResult(m,.38f*m.importance+.22f*m.emotionalIntensity+.20f*recent+.20f*relevance)
  }.sortedByDescending{it.score}.take(limit.coerceIn(1,50)).toList()
 }
 @Synchronized fun soul(agentId:String)=document(agentId).soul
 @Synchronized fun updateSoul(agentId:String,transform:(SoulProfile)->SoulProfile):SoulProfile{val d=document(agentId);val s=transform(d.soul);save(agentId,d.copy(soul=s));return s}
 private fun document(id:String)=cache.getOrPut(id){val f=fileFor(id);if(!f.isFile||f.length()>MAX_BYTES)AgentMemoryDocument() else runCatching{json.decodeFromString<AgentMemoryDocument>(f.readText())}.getOrDefault(AgentMemoryDocument())}
 private fun save(id:String,d:AgentMemoryDocument){directory.mkdirs();val f=fileFor(id);val tmp=File(directory,f.name+".tmp");val bytes=json.encodeToString(d).toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_BYTES);FileOutputStream(tmp).use{it.write(bytes);it.fd.sync()};if(f.exists()&&!f.delete())error("Não foi possível substituir a memória.");if(!tmp.renameTo(f)){tmp.delete();error("Não foi possível confirmar a memória.")};cache[id]=d}
 private fun fileFor(id:String):File{val safe=id.filter{it.isLetterOrDigit()||it=='_'||it=='-'}.take(80);require(safe.isNotBlank());return File(directory,"agent-"+safe+".json")}
 private fun tokenize(s:String)=s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>2}.toSet()
 private fun overlap(a:Set<String>,b:Set<String>)=if(a.isEmpty()||b.isEmpty())0f else a.intersect(b).size.toFloat()/a.union(b).size.coerceAtLeast(1)
 private fun cosine(a:List<Float>,b:List<Float>):Float{if(a.isEmpty()||a.size!=b.size)return 0f;var dot=0.0;var aa=0.0;var bb=0.0;for(i in a.indices){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i]};return if(aa<=0.0||bb<=0.0)0f else (dot/sqrt(aa*bb)).toFloat().coerceIn(0f,1f)}
 companion object {private const val MAX_BYTES=2*1024*1024L;private val WORK_CONTEXTS=setOf(BuildingKind.HEADQUARTERS,BuildingKind.OFFICE,BuildingKind.RESEARCH_LAB,BuildingKind.UNIVERSITY,BuildingKind.DATA_CENTER)}
}
class WorldCognitionCoordinator(private val memory:SeveranceMemoryManager) {
 fun observe(world:WorldState) {
  world.agents.forEach { a ->
   val r=a.activityHistory.firstOrNull()?:return@forEach
   val key=r.simMinute.toString()+":"+r.action.name+":"+r.buildingId.orEmpty()
   val saved=memory.remember(a,world,r.action.name.lowercase()+": "+r.outcome,
    importance=if(r.action in setOf(ActionType.WORK,ActionType.RESEARCH,ActionType.LEARN,ActionType.SOCIALIZE)) .72f else .42f,
    emotionalIntensity=if(a.emotion in setOf(Emotion.ANXIOUS,Emotion.FRUSTRATED,Emotion.FULFILLED)) .78f else .35f,
    sourceKey=key)
   if(saved!=null) {
    val trait=when(r.action) {
     ActionType.WORK -> "discipline"
     ActionType.RESEARCH,ActionType.LEARN -> "curiosity"
     ActionType.SOCIALIZE -> "sociability"
     else -> "care"
    }
    memory.updateSoul(a.id) { it.absorb(trait,true) }
   }
  }
 }
}
