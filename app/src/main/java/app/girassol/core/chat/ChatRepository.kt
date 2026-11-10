package app.girassol.core.chat

import app.girassol.core.data.SafeFiles
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ChatRole { USER, ASSISTANT }
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

class ChatRepository(private val file: File) {
    private val lock = Any()
    private val mutableMessages = MutableStateFlow(load())
    val messages: StateFlow<List<ChatMessage>> = mutableMessages.asStateFlow()

    fun append(role: ChatRole, content: String): ChatMessage {
        val message = ChatMessage(role = role, content = content)
        synchronized(lock) { update(mutableMessages.value + message) }
        return message
    }

    fun update(id: String, content: String, persist: Boolean = true) {
        synchronized(lock) {
            update(mutableMessages.value.map { if (it.id == id) it.copy(content = content) else it }, persist)
        }
    }

    fun clear() = synchronized(lock) { update(emptyList()) }

    private fun update(messages: List<ChatMessage>, persist: Boolean = true) {
        val bounded = messages.takeLast(120)
        mutableMessages.value = bounded
        if (!persist) return
        runCatching {
            val array = JSONArray()
            bounded.forEach { message ->
                array.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("content", message.content)
                    put("createdAt", message.createdAt)
                })
            }
            SafeFiles.writeAtomic(file, array.toString())
        }
    }

    private fun load(): List<ChatMessage> = runCatching {
        if (!file.isFile) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(ChatMessage(
                    id = item.getString("id"),
                    role = ChatRole.valueOf(item.getString("role")),
                    content = item.getString("content"),
                    createdAt = item.getLong("createdAt"),
                ))
            }
        }.takeLast(120)
    }.getOrDefault(emptyList())
}
