package dev.labforge.app

import dev.labforge.app.core.rsi.RsiWeightBundle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RsiWeightBundleTest {
    private fun pack(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun writeBundle(payload: ByteArray, manifest: JSONObject): File {
        val file = File.createTempFile("girassol-rsi", ".rsiw")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("tensors.f32"))
            zip.write(payload)
            zip.closeEntry()
        }
        return file
    }

    @Test fun sha256UsesStandardUnsignedHexEncoding() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha("abc".toByteArray()),
        )
    }

    @Test fun loadsRdtTensorPackWithVerifiedHashAndShapes() {
        val values = FloatArray(12) { (it + 1) / 10f }
        val payload = pack(values)
        val descriptors = JSONObject().apply {
            put("input_weights", JSONObject().put("offset", 0).put("count", 4))
            put("recurrent_weights", JSONObject().put("offset", 4).put("count", 4))
            put("bias", JSONObject().put("offset", 8).put("count", 2))
            put("gate", JSONObject().put("offset", 10).put("count", 2))
        }
        val manifest = JSONObject().apply {
            put("format", "girassol-rsiw")
            put("version", 1)
            put("architecture", "rdt")
            put("hidden_size", 2)
            put("reasoning_steps", 3)
            put("tensor_sha256", sha(payload))
            put("tensors", descriptors)
        }
        val loaded = RsiWeightBundle.load(writeBundle(payload, manifest))
        assertEquals("rdt", loaded.architecture)
        assertEquals(2, loaded.hiddenSize)
        assertEquals(4, loaded.tensors.getValue("input_weights").size)
        assertTrue(kotlin.math.abs(loaded.tensors.getValue("gate")[1] - 1.2f) < 0.0001f)
    }

    @Test fun rejectsTamperedTensorPayload() {
        val payload = pack(floatArrayOf(1f))
        val manifest = JSONObject().apply {
            put("format", "girassol-rsiw")
            put("version", 1)
            put("architecture", "modusx")
            put("hidden_size", 1)
            put("tensor_sha256", "00")
            put("tensors", JSONObject())
        }
        assertTrue(runCatching { RsiWeightBundle.load(writeBundle(payload, manifest)) }.isFailure)
    }

    @Test fun rejectsUnknownArchitectureAndInvalidHiddenSize() {
        val payload = ByteArray(0)
        val manifest = JSONObject().apply {
            put("format", "girassol-rsiw")
            put("version", 1)
            put("architecture", "invented")
            put("hidden_size", 2)
            put("tensor_sha256", sha(payload))
        }
        assertTrue(runCatching { RsiWeightBundle.load(writeBundle(payload, manifest)) }.isFailure)
    }
}
