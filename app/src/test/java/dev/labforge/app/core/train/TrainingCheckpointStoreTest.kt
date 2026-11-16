package dev.labforge.app.core.train

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrainingCheckpointStoreTest {
    @Rule @JvmField val temp = TemporaryFolder()

    @Test
    fun persistsSuccessfulCheckpointWithReportedLossAndDeduplicates() {
        val output = temp.newFile("model-lora.gguf")
        val file = temp.newFile("history.json")
        val store = TrainingCheckpointStore(file)
        store.record("lora", output, 1_000L, 0.125f)
        store.record("lora", output, 1_000L, 0.125f)

        val restored = TrainingCheckpointStore(file).list()
        assertEquals(1, restored.size)
        assertEquals("model-lora.gguf", restored.single().outputName)
        assertEquals(0.125f, restored.single().finalLoss!!)
        assertEquals(1, JSONArray(file.readText()).length())
    }

    @Test
    fun keepsSeparateRealSavesAndNeverInventsDivergence() {
        val output = temp.newFile("adapter.gguf")
        val file = temp.newFile("history.json")
        val store = TrainingCheckpointStore(file)
        store.record("lora", output, 1_000L, null)
        store.record("lora", output, 2_000L, Float.NaN)

        val checkpoints = store.list()
        assertEquals(2, checkpoints.size)
        assertEquals(2_000L, checkpoints.first().savedAt)
        assertNull(checkpoints.first().finalLoss)
        assertTrue(file.readText().contains("outputPath"))
        assertTrue(!file.readText().contains("divergence"))
    }
}
