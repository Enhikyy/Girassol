package dev.labforge.app.core.gguf

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal, allocation-conscious GGUF v2/v3 reader.
 * Reads only the header + tensor index (never the tensor payload), so it is safe
 * for multi-gigabyte models.
 */
object Gguf {

    const val MAGIC = 0x46554747 // "GGUF" little-endian

    data class Tensor(
        val name: String,
        val dims: LongArray,
        val type: GgmlType,
        val offset: Long,
        val byteSize: Long,
    ) {
        val elements: Long get() = dims.fold(1L) { a, b -> a * b }
        val layer: Int? get() = Regex("""blk\.(\d+)\.|layers\.(\d+)\.|h\.(\d+)\.|model\.layers\.(\d+)\.|transformer\.h\.(\d+)\.""")
            .find(name)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
    }

    data class Metadata(
        val key: String,
        val value: Any?,
        val typeName: String,
    )

    data class Model(
        val file: File,
        val version: Int,
        val tensorCount: Long,
        val kvCount: Long,
        val metadata: List<Metadata>,
        val tensors: List<Tensor>,
        val alignment: Int,
        val headerBytes: Long,
    ) {
        private val map: Map<String, Any?> by lazy { metadata.associate { it.key to it.value } }

        fun str(key: String): String? = map[key]?.toString()
        fun num(key: String): Long? = when (val v = map[key]) {
            is Number -> v.toLong()
            is Boolean -> if (v) 1 else 0
            else -> null
        }

        val architecture: String get() = str("general.architecture") ?: "unknown"
        val name: String get() = str("general.name") ?: file.nameWithoutExtension
        val fileType: String get() = str("general.file_type") ?: "?"
        val quantLabel: String
            get() {
                val t = tensors.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key
                return t?.label ?: "?"
            }
        val contextLength: Long get() = num("$architecture.context_length") ?: 0
        val embeddingLength: Long get() = num("$architecture.embedding_length") ?: 0
        val blockCount: Long get() = num("$architecture.block_count") ?: 0
        val headCount: Long get() = num("$architecture.attention.head_count") ?: 0
        val headCountKv: Long get() = num("$architecture.attention.head_count_kv") ?: 0
        val parameterCount: Long get() = tensors.sumOf { it.elements }
        val totalBytes: Long get() = tensors.sumOf { it.byteSize }
        val chatTemplate: String? get() = str("tokenizer.chat_template")
        val tokenizerModel: String? get() = str("tokenizer.ggml.model")
        val quantMix: Map<GgmlType, Int> get() = tensors.groupingBy { it.type }.eachCount()

        /** "7B", "1.5B", "260K" */
        val parameterLabel: String
            get() {
                val p = parameterCount.toDouble()
                return when {
                    p >= 1e12 -> String.format("%.1fT", p / 1e12)
                    p >= 1e9 -> String.format("%.1fB", p / 1e9)
                    p >= 1e6 -> String.format("%.1fM", p / 1e6)
                    p >= 1e3 -> String.format("%.0fK", p / 1e3)
                    else -> p.toLong().toString()
                }
            }
    }

    fun read(file: File): Model {
        RandomAccessFile(file, "r").use { raf ->
            val reader = LeReader(raf)
            val magic = reader.u32()
            require(magic == MAGIC.toLong()) { "Arquivo não é GGUF (magic=0x${magic.toString(16)})" }
            val version = reader.u32()
            require(version in 2L..3L) { "Versão GGUF $version não suportada (use v2 ou v3)" }
            val tensorCount = reader.u64()
            val kvCount = reader.u64()

            // Reject absurd counts instead of silently truncating the loop: a capped
            // repeat() would leave the reader misaligned and corrupt every tensor.
            require(tensorCount in 0..1_000_000) { "tensor_count inválido ($tensorCount)" }
            require(kvCount in 0..1_000_000) { "kv_count inválido ($kvCount)" }

            val metadata = ArrayList<Metadata>(kvCount.coerceAtMost(512).toInt())
            var alignment = 32
            repeat(kvCount.toInt()) {
                val key = reader.string()
                val vtype = reader.u32()
                val value = readValue(reader, vtype.toInt(), 0)
                if (key == "general.alignment" && value is Number) alignment = value.toInt()
                metadata += Metadata(key, value, typeName(vtype.toInt()))
            }

            val tensors = ArrayList<Tensor>(tensorCount.coerceAtMost(100_000).toInt())
            repeat(tensorCount.toInt()) {
                val name = reader.string()
                val nDims = reader.u32()
                require(nDims in 0L..8L) { "Dimensões inválidas ($nDims) em $name" }
                val dims = LongArray(nDims.toInt()) { reader.u64() }
                val type = GgmlType.of(reader.u32().toInt())
                val offset = reader.u64()
                val elements = dims.fold(1L) { a, b -> a * b }
                tensors += Tensor(name, dims, type, offset, type.byteSize(elements))
            }

            val headerBytes = align(raf.filePointer, alignment.toLong())
            return Model(file, version.toInt(), tensorCount, kvCount, metadata, tensors, alignment, headerBytes)
        }
    }

    private fun readValue(r: LeReader, type: Int, depth: Int): Any? = when (type) {
        0 -> r.u8()
        1 -> r.u8().toByte()
        2 -> r.u16()
        3 -> r.u16().toShort()
        4 -> r.u32()
        5 -> r.u32().toInt()
        6 -> Float.fromBits(r.u32().toInt())
        7 -> r.u8() != 0
        8 -> r.string()
        9 -> {
            val itemType = r.u32().toInt()
            val n = r.u64()
            if (depth >= 2) {
                // skip nested arrays without materializing
                var i = 0L
                while (i < n) { readValue(r, itemType, depth + 1); i++ }
                emptyList<Any?>()
            } else {
                val keep = n.coerceAtMost(if (itemType == 8) 16L else 64L).toInt()
                val items = ArrayList<Any?>(keep)
                var i = 0L
                while (i < n) {
                    val v = readValue(r, itemType, depth + 1)
                    if (i < keep) items += v
                    i++
                }
                items
            }
        }
        10 -> r.u64()
        11 -> r.u64().toLong()
        12 -> Double.fromBits(r.u64())
        else -> null
    }

    private fun typeName(type: Int) = when (type) {
        0 -> "u8"; 1 -> "i8"; 2 -> "u16"; 3 -> "i16"; 4 -> "u32"; 5 -> "i32"; 6 -> "f32"
        7 -> "bool"; 8 -> "string"; 9 -> "array"; 10 -> "u64"; 11 -> "i64"; 12 -> "f64"; else -> "?"
    }

    fun align(v: Long, a: Long): Long = if (a <= 1) v else (v + a - 1) / a * a

    private class LeReader(private val raf: RandomAccessFile) {
        fun bytes(n: Int): ByteArray {
            val b = ByteArray(n)
            raf.readFully(b)
            return b
        }
        fun u8(): Int = raf.readUnsignedByte()
        fun u16(): Int = ByteBuffer.wrap(bytes(2)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        fun u32(): Long = ByteBuffer.wrap(bytes(4)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        fun u64(): Long = ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
        fun string(): String {
            val len = u64()
            require(len in 0..64_000_000) { "String GGUF inválida (len=$len)" }
            return String(bytes(len.toInt()), Charsets.UTF_8)
        }
    }
}

enum class GgmlType(val id: Int, val label: String, val block: Int, val bytesPerBlock: Int) {
    F32(0, "F32", 1, 4),
    F16(1, "F16", 1, 2),
    Q4_0(2, "Q4_0", 32, 18),
    Q4_1(3, "Q4_1", 32, 20),
    Q5_0(6, "Q5_0", 32, 22),
    Q5_1(7, "Q5_1", 32, 24),
    Q8_0(8, "Q8_0", 32, 34),
    Q8_1(9, "Q8_1", 32, 36),
    Q2_K(10, "Q2_K", 256, 84),
    Q3_K(11, "Q3_K", 256, 110),
    Q4_K(12, "Q4_K", 256, 144),
    Q5_K(13, "Q5_K", 256, 176),
    Q6_K(14, "Q6_K", 256, 210),
    Q8_K(15, "Q8_K", 256, 292),
    IQ2_XXS(16, "IQ2_XXS", 256, 66),
    IQ2_XS(17, "IQ2_XS", 256, 74),
    IQ3_XXS(18, "IQ3_XXS", 256, 98),
    IQ1_S(19, "IQ1_S", 256, 50),
    IQ4_NL(20, "IQ4_NL", 32, 18),
    IQ3_S(21, "IQ3_S", 256, 110),
    IQ2_S(22, "IQ2_S", 256, 82),
    IQ4_XS(23, "IQ4_XS", 256, 136),
    I8(24, "I8", 1, 1),
    I16(25, "I16", 1, 2),
    I32(26, "I32", 1, 4),
    I64(27, "I64", 1, 8),
    F64(28, "F64", 1, 8),
    IQ1_M(29, "IQ1_M", 256, 56),
    BF16(30, "BF16", 1, 2),
    Q4_0_4_4(31, "Q4_0_4_4", 32, 18),
    Q4_0_4_8(32, "Q4_0_4_8", 32, 18),
    Q4_0_8_8(33, "Q4_0_8_8", 32, 18),
    TQ1_0(34, "TQ1_0", 256, 54),
    TQ2_0(35, "TQ2_0", 256, 66),
    MXFP4(39, "MXFP4", 32, 17),
    NVFP4(40, "NVFP4", 32, 20),
    F8_E4M3(41, "F8_E4M3", 1, 1),
    UNKNOWN(-1, "?", 1, 0);

    fun byteSize(elements: Long): Long =
        if (bytesPerBlock == 0) 0 else (elements / block) * bytesPerBlock + (elements % block) * bytesPerBlock / block

    fun bitsPerWeight(): Double = if (block == 0) 0.0 else bytesPerBlock * 8.0 / block

    companion object {
        private val byId = entries.associateBy { it.id }
        fun of(id: Int): GgmlType = byId[id] ?: UNKNOWN
    }
}
