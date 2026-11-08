package dev.labforge.app

import dev.labforge.app.core.device.ActionJournal
import dev.labforge.app.core.device.DeviceActionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActionJournalTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun record(index: Int, ok: Boolean = true) = DeviceActionRecord(
        at = index.toLong(),
        action = "device_tap",
        detail = "text=item-$index",
        ok = ok,
        before = "aaaa",
        after = "bbbb",
    )

    @Test
    fun `recent returns newest first and parses fields`() {
        val journal = ActionJournal(temp.newFile("journal.jsonl"))
        journal.append(record(1))
        journal.append(record(2, ok = false))

        val recent = journal.recent(5)
        assertEquals(2, recent.size)
        assertEquals(2L, recent[0].at)
        assertFalse(recent[0].ok)
        assertEquals("device_tap", recent[0].action)
        assertEquals("aaaa", recent[0].before)
    }

    @Test
    fun `journal is bounded and keeps the newest entries`() {
        val journal = ActionJournal(temp.newFile("bounded.jsonl"), maxLines = 5)
        repeat(12) { journal.append(record(it)) }

        val recent = journal.recent(50)
        assertEquals(5, recent.size)
        assertEquals(11L, recent[0].at)
        assertEquals(7L, recent.last().at)
    }

    @Test
    fun `clear removes the file`() {
        val file = temp.newFile("clear.jsonl")
        val journal = ActionJournal(file)
        journal.append(record(1))
        assertTrue(file.exists())
        journal.clear()
        assertFalse(file.exists())
        assertTrue(journal.recent().isEmpty())
    }
}
