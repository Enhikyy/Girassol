package app.girassol

import app.girassol.core.chat.ChatRepository
import app.girassol.core.chat.ChatRole
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun streamingCanStayInMemoryUntilFinalPersist() {
        val file = File(folder.root, "chat.json")
        val repository = ChatRepository(file)
        val assistant = repository.append(ChatRole.ASSISTANT, "")
        repository.update(assistant.id, "parcial", persist = false)
        assertEquals("", ChatRepository(file).messages.value.single().content)
        repository.update(assistant.id, "resposta final")
        assertEquals("resposta final", ChatRepository(file).messages.value.single().content)
    }
}
