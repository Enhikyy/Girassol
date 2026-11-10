package dev.labforge.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.labforge.app.core.model.ModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LaunchTest {
    @Test
    fun applicationStarts() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        app.onCreate()
        assertNotNull(app)
    }

    @Test
    fun modelCacheInvalidatesWhenLengthChangesAtSameTimestamp() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        val store = ModelStore(app)
        val file = java.io.File(store.modelsDir, "cache-invalidation.gguf")
        fun u32(out: ByteArrayOutputStream, value: Long) {
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array())
        }
        fun u64(out: ByteArrayOutputStream, value: Long) {
            out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
        }
        fun text(out: ByteArrayOutputStream, value: String) {
            val bytes = value.toByteArray()
            u64(out, bytes.size.toLong())
            out.write(bytes)
        }
        fun writeModel(architecture: String) {
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
            u32(out, 3)
            u64(out, 0)
            u64(out, 1)
            text(out, "general.architecture")
            u32(out, 8)
            text(out, architecture)
            file.writeBytes(out.toByteArray())
        }
        try {
            writeModel("llama")
            assertEquals("llama", store.readModel(file).meta?.architecture)
            val timestamp = file.lastModified()
            writeModel("qwen2-long")
            assertTrue(file.setLastModified(timestamp))
            assertEquals("qwen2-long", store.readModel(file).meta?.architecture)
        } finally {
            file.delete()
        }
    }
}
