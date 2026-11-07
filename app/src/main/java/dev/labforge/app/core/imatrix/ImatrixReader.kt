package dev.labforge.app.core.imatrix

import dev.labforge.app.core.gguf.Gguf
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Reads both imatrix flavours produced by llama.cpp:
 *  - GGUF imatrix (current): one <tensor>.in_sum / .in_sum2 / .counts trio per tensor
 *  - legacy `.dat` (still common on HF): n_entries + per-entry name/ncall/nval + float sums
 */
object ImatrixReader {

    data class Entry(
        val name: String,
        val values: Int,
        val ncall: Long,
        val meanAbs: Double,
        val maxAbs: Double,
        val p95: Double,
        val layer: Int?,
    ) {
        val shortName: String get() = name.substringAfterLast('.')
    }

    data class Report(
        val file: File,
        val legacy: Boolean,
        val entries: List<Entry>,
        val chunkCount: Int,
        val chunkSize: Int,
        val datasets: List<String>,
        val bytes: Long,
    ) {
        val tensorCount get() = entries.size
        val totalValues get() = entries.sumOf { it.values.toLong() }
        val meanImportance get() = if (entries.isEmpty()) 0.0 else entries.map { it.meanAbs }.average()
        val layers: List<LayerStat>
            get() = entries.filter { it.layer != null }
                .groupBy { it.layer!! }
                .map { (layer, list) ->
                    LayerStat(
                        layer = layer,
                        tensors = list.size,
                        importance = list.map { it.meanAbs }.average(),
                        peak = list.maxOf { it.maxAbs },
                    )
                }
                .sortedBy { it.layer }

        fun coverageAgainst(model: Gguf.Model): Coverage {
            val modelNames = model.tensors.map { it.name }.toSet()
            val matched = entries.count { e -> model.tensors.any { it.name == e.name || it.name.startsWith(e.name) } }
            val missingInImatrix = model.tensors
                .filter { t -> t.dims.size >= 2 && entries.none { e -> t.name == e.name || t.name.startsWith(e.name) } }
                .map { it.name }
            return Coverage(matched, modelNames.size, missingInImatrix)
        }
    }

    data class LayerStat(val layer: Int, val tensors: Int, val importance: Double, val peak: Double)

    data class Coverage(val matched: Int, val totalModelTensors: Int, val missing: List<String>) {
        val ratio: Float get() = if (totalModelTensors == 0) 0f else matched.toFloat() / totalModelTensors
    }

    fun read(file: File): Report {
        val head = ByteArray(4)
        RandomAccessFile(file, "r").use { it.readFully(head) }
        return if (head[0] == 'G'.code.toByte() && head[1] == 'G'.code.toByte() &&
            head[2] == 'U'.code.toByte() && head[3] == 'F'.code.toByte()
        ) readGguf(file) else readLegacy(file)
    }

    // ---------------------------------------------------------------- GGUF imatrix

    private fun readGguf(file: File): Report {
        val model = Gguf.read(file)
        val sums = model.tensors.filter { it.name.endsWith(".in_sum") }
        val counts = model.tensors.filter { it.name.endsWith(".counts") }
            .associateBy { it.name.substringBeforeLast(".counts") }

        val entries = ArrayList<Entry>(sums.size)
        RandomAccessFile(file, "r").use { raf ->
            for (t in sums) {
                val base = t.name.substringBeforeLast(".in_sum")
                val countTensor = counts[base]
                val ncall = if (countTensor != null) readFirstInt(raf, model, countTensor) else 0L
                val stats = sampleStats(raf, model, t)
                entries += Entry(
                    name = base,
                    values = t.elements.toInt(),
                    ncall = ncall,
                    meanAbs = stats.first,
                    maxAbs = stats.second,
                    p95 = stats.third,
                    layer = layerOf(base),
                )
            }
        }
        val datasets = model.metadata.firstOrNull { it.key == "imatrix.datasets" }?.value as? List<*>
        return Report(
            file = file,
            legacy = false,
            entries = entries.sortedBy { it.name },
            chunkCount = model.num("imatrix.chunk_count")?.toInt() ?: 0,
            chunkSize = model.num("imatrix.chunk_size")?.toInt() ?: 0,
            datasets = datasets?.mapNotNull { it?.toString() } ?: emptyList(),
            bytes = file.length(),
        )
    }

    private fun readFirstInt(raf: RandomAccessFile, model: Gguf.Model, t: Gguf.Tensor): Long {
        raf.seek(model.headerBytes + t.offset)
        return when {
            t.type.id == 26 -> { // I32
                val b = ByteArray(4)
                raf.readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
            }
            t.type.id == 24 -> { // I8
                raf.readByte().toLong()
            }
            else -> {
                val b = ByteArray(8)
                raf.readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
            }
        }
    }

    /** Reads at most 262144 floats per tensor and returns (mean|v|, max|v|, p95). */
    private fun sampleStats(raf: RandomAccessFile, model: Gguf.Model, t: Gguf.Tensor): Triple<Double, Double, Double> {
        val total = t.elements.coerceAtLeast(1)
        val take = total.coerceAtMost(262_144).toInt()
        val bytes = ByteArray(take * 4)
        raf.seek(model.headerBytes + t.offset)
        raf.readFully(bytes)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        var sum = 0.0
        var mx = 0.0
        val vals = FloatArray(take)
        for (i in 0 until take) {
            val v = abs(buf.get(i).toDouble())
            vals[i] = v.toFloat()
            sum += v
            if (v > mx) mx = v
        }
        vals.sort()
        val p95 = if (take == 0) 0.0 else vals[((take - 1) * 0.95).toInt()].toDouble()
        return Triple(sum / take, mx, p95)
    }

    // -------------------------------------------------------------- legacy .dat

    private fun readLegacy(file: File): Report {
        val entries = ArrayList<Entry>()
        var chunkCount = 0
        val datasets = ArrayList<String>()
        RandomAccessFile(file, "r").use { raf ->
            val n = readIntLe(raf)
            require(n in 1..2_000_000) { "imatrix .dat inválido (n_entries=$n)" }
            repeat(n) {
                val len = readIntLe(raf)
                require(len in 1..4096) { "Nome de tensor inválido no imatrix" }
                val name = String(ByteArray(len).also { raf.readFully(it) }, Charsets.UTF_8)
                val ncall = readIntLe(raf).toLong()
                val nval = readIntLe(raf)
                require(nval in 1..100_000_000) { "nval inválido para $name" }
                val take = nval.coerceAtMost(262_144)
                val buf = ByteArray(take * 4)
                raf.readFully(buf)
                if (nval > take) raf.seek(raf.filePointer + (nval - take) * 4L)
                val fb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                var sum = 0.0
                var mx = 0.0
                var sumSq = 0.0
                val vals = FloatArray(take)
                for (i in 0 until take) {
                    // legacy stores sums; normalize by call count for readability
                    val raw = abs(fb.get(i).toDouble())
                    val v = if (ncall > 0) raw / ncall else raw
                    vals[i] = v.toFloat()
                    sum += v
                    sumSq += v * v
                    if (v > mx) mx = v
                }
                vals.sort()
                val p95 = vals[((take - 1) * 0.95).toInt()].toDouble()
                entries += Entry(
                    name = name,
                    values = nval,
                    ncall = ncall,
                    meanAbs = sum / take,
                    maxAbs = mx,
                    p95 = p95,
                    layer = layerOf(name),
                )
                @Suppress("UNUSED_EXPRESSION") sumSq
            }
            if (raf.filePointer < raf.length()) {
                chunkCount = readIntLe(raf)
                if (raf.filePointer < raf.length()) {
                    val len = readIntLe(raf)
                    if (len in 1..4096 && raf.filePointer + len <= raf.length()) {
                        datasets += String(ByteArray(len).also { raf.readFully(it) }, Charsets.UTF_8)
                    }
                }
            }
        }
        return Report(
            file = file,
            legacy = true,
            entries = entries.sortedBy { it.name },
            chunkCount = chunkCount,
            chunkSize = 0,
            datasets = datasets,
            bytes = file.length(),
        )
    }

    private fun readIntLe(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun layerOf(name: String): Int? =
        Regex("""(?:blk|layers|h|blocks)\.(\d+)\.|model\.layers\.(\d+)\.|transformer\.h\.(\d+)\.""")
            .find(name)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()

    fun summarize(report: Report): String = buildString {
        append("${report.tensorCount} tensores · ${fmt(report.totalValues.toDouble())} valores")
        if (report.chunkCount > 0) append(" · ${report.chunkCount} chunks")
        if (report.legacy) append(" · formato legado .dat")
    }

    private fun fmt(v: Double): String = when {
        v >= 1e9 -> String.format("%.1fB", v / 1e9)
        v >= 1e6 -> String.format("%.1fM", v / 1e6)
        v >= 1e3 -> String.format("%.1fK", v / 1e3)
        else -> v.toLong().toString()
    }

    @Suppress("unused")
    private fun stddev(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val m = values.average()
        return sqrt(values.sumOf { (it - m) * (it - m) } / values.size)
    }

    @Suppress("unused")
    private fun safeDiv(a: Double, b: Double) = if (b == 0.0) 0.0 else a / b

    @Suppress("unused")
    private fun peak(values: DoubleArray) = values.maxOrNull() ?: 0.0

    @Suppress("unused")
    private fun spread(values: DoubleArray) = max(0.0, (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0))
}
