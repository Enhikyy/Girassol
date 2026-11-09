package dev.labforge.app

import dev.labforge.app.core.gguf.Gguf
import dev.labforge.app.core.imatrix.ImatrixReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GgufParserTest {

    private fun u32(out: ByteArrayOutputStream, v: Long) {
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array())
    }

    private fun u64(out: ByteArrayOutputStream, v: Long) {
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
    }

    private fun str(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray()
        u64(out, b.size.toLong())
        out.write(b)
    }

    @Test
    fun `parses gguf v3 header, metadata and tensors`() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        u32(out, 3)
        u64(out, 2) // tensors
        u64(out, 4) // kv

        str(out, "general.architecture"); u32(out, 8); str(out, "llama")
        str(out, "general.alignment"); u32(out, 4); u32(out, 32)
        str(out, "llama.block_count"); u32(out, 4); u32(out, 2)
        str(out, "tokenizer.chat_template"); u32(out, 8); str(out, "{{ messages }}")

        // tensor 0: blk.0.attn_q.weight [32, 2] Q4_0
        str(out, "blk.0.attn_q.weight")
        u32(out, 2); u64(out, 32); u64(out, 2); u32(out, 2); u64(out, 0)
        // tensor 1: output_norm.weight [32] F32
        str(out, "output_norm.weight")
        u32(out, 1); u64(out, 32); u32(out, 0); u64(out, 288)

        val headerLen = out.size()
        val pad = (32 - headerLen % 32) % 32
        repeat(pad) { out.write(0) }

        val file = File.createTempFile("labforge-test", ".gguf")
        file.writeBytes(out.toByteArray())

        val model = Gguf.read(file)
        assertEquals(3, model.version)
        assertEquals(2, model.tensorCount)
        assertEquals("llama", model.architecture)
        assertEquals(32, model.alignment)
        assertEquals(2, model.blockCount)
        assertEquals("{{ messages }}", model.chatTemplate)
        assertEquals(2, model.tensors.size)
        assertEquals(listOf(32L, 2L), model.tensors[0].dims.toList())
        assertEquals("Q4_0", model.tensors[0].type.label)
        assertEquals(96L, model.parameterCount)
        assertEquals(0, model.headerBytes % 32)
        file.delete()
    }

    @Test(timeout = 2_000)
    fun `rejects malformed array instead of looping forever`() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        u32(out, 3)
        u64(out, 0)
        u64(out, 1)
        str(out, "bad")
        u32(out, 9)
        u32(out, 99)
        u64(out, Long.MAX_VALUE)

        val file = File.createTempFile("labforge-bad", ".gguf")
        try {
            file.writeBytes(out.toByteArray())
            Gguf.read(file)
            throw AssertionError("GGUF malformado deveria falhar")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("array"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parses legacy dat imatrix`() {
        val out = ByteArrayOutputStream()
        fun i32(v: Int) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()) }
        fun f32(v: Float) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array()) }

        i32(2)
        i32("blk.0.attn_q.weight".length); out.write("blk.0.attn_q.weight".toByteArray())
        i32(10); i32(3); f32(10f); f32(20f); f32(30f)
        i32("blk.1.attn_v.weight".length); out.write("blk.1.attn_v.weight".toByteArray())
        i32(2); i32(2); f32(4f); f32(8f)
        i32(7) // chunk count
        i32("wiki".length); out.write("wiki".toByteArray())

        val file = File.createTempFile("labforge-imat", ".dat")
        file.writeBytes(out.toByteArray())

        val report = ImatrixReader.read(file)
        assertTrue(report.legacy)
        assertEquals(2, report.tensorCount)
        assertEquals(7, report.chunkCount)
        assertEquals("wiki", report.datasets.first())
        assertEquals(2.0, report.entries[0].meanAbs, 0.0001)
        assertEquals(3.0, report.entries[1].meanAbs, 0.0001)
        assertEquals(0, report.entries[0].layer)
        assertEquals(1, report.entries[1].layer)
        file.delete()
    }
    @Test
    fun `parses gguf imatrix with in_sum and counts`() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        u32(out, 3)
        u64(out, 2)
        u64(out, 4)

        str(out, "imatrix.chunk_count"); u32(out, 4); u32(out, 3)
        str(out, "imatrix.chunk_size"); u32(out, 4); u32(out, 512)
        str(out, "imatrix.datasets"); u32(out, 9)
        u32(out, 8); u64(out, 1); str(out, "wiki")
        str(out, "general.architecture"); u32(out, 8); str(out, "llama")

        str(out, "blk.0.attn_q.weight.in_sum")
        u32(out, 1); u64(out, 4); u32(out, 0); u64(out, 0)
        str(out, "blk.0.attn_q.weight.counts")
        u32(out, 1); u64(out, 1); u32(out, 27); u64(out, 16)

        val headerLen = out.size()
        repeat((32 - headerLen % 32) % 32) { out.write(0) }
        val floats = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(1f).putFloat(2f).putFloat(3f).putFloat(4f).array()
        out.write(floats)
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(5L).array())

        val file = File.createTempFile("labforge-imat", ".gguf")
        file.writeBytes(out.toByteArray())
        val report = ImatrixReader.read(file)
        assertTrue(!report.legacy)
        assertEquals(1, report.tensorCount)
        assertEquals(3, report.chunkCount)
        assertEquals(512, report.chunkSize)
        assertEquals("wiki", report.datasets.first())
        assertEquals(2.5, report.entries[0].meanAbs, 0.0001)
        assertEquals(5L, report.entries[0].ncall)
        assertEquals(0, report.entries[0].layer)
        file.delete()
    }

}
