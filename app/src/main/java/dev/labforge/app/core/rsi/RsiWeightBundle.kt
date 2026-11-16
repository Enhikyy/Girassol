package dev.labforge.app.core.rsi

import dev.labforge.app.core.native.LlamaBridge
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Hash-verified tensor bundle for native RSI hidden-state adapters.
 * This is an adapter weight pack, not a complete tokenizer/decoder language model.
 */
data class RsiWeightBundle(
    val architecture: String,
    val hiddenSize: Int,
    val experts: Int,
    val topK: Int,
    val reasoningSteps: Int,
    val decay: Float,
    val learningRate: Float,
    val tensors: Map<String, FloatArray>,
) {
    fun runRdt(input: FloatArray, anchor: FloatArray): FloatArray {
        require(architecture == "rdt" && input.size == hiddenSize && anchor.size == hiddenSize)
        val wi = tensors.getValue("input_weights")
        val wr = tensors.getValue("recurrent_weights")
        return LlamaBridge.rdtRefine(input, anchor, wi, wr, tensors.getValue("bias"),
            tensors.getValue("gate"), reasoningSteps)
            .also { require(it.size == hiddenSize) { "falha no kernel RDT" } }
    }

    fun runModusX(input: FloatArray, state: FloatArray, memory: FloatArray): LlamaBridge.ModusXState {
        require(architecture == "modusx" && input.size == hiddenSize)
        return requireNotNull(LlamaBridge.modusXStep(input, state, memory, decay, learningRate)) {
            "falha no kernel Modus_X"
        }
    }

    fun runLunaris(input: FloatArray): FloatArray {
        require(architecture == "lunaris_moc" && input.size == hiddenSize)
        return LlamaBridge.lunarisMoc(
            input,
            tensors.getValue("router_weights"), tensors.getValue("router_bias"),
            tensors.getValue("expert_weights"), tensors.getValue("expert_bias"),
            tensors.getValue("mediator_weights"), tensors.getValue("mediator_bias"),
            tensors.getValue("fusion_weights"), tensors.getValue("fusion_bias"),
            experts, topK, reasoningSteps,
        ).also { require(it.size == hiddenSize) { "falha no kernel Lunaris MoC" } }
    }

    companion object {
        private const val MAX_BUNDLE_BYTES = 128 * 1024 * 1024
        private const val FORMAT = "girassol-rsiw"

        fun load(file: File): RsiWeightBundle {
            require(file.isFile && file.length() in 1L..(MAX_BUNDLE_BYTES * 2L)) { "arquivo RSIW ausente ou acima do limite" }
            val entries = HashMap<String, ByteArray>()
            var total = 0
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entry.name in setOf("manifest.json", "tensors.f32")) { "entrada ZIP não permitida: ${entry.name}" }
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_BUNDLE_BYTES) { "conteúdo RSIW acima de 128 MiB" }
                        out.write(buffer, 0, n)
                    }
                    require(entries.put(entry.name, out.toByteArray()) == null) { "entrada ZIP duplicada" }
                    zip.closeEntry()
                }
            }
            val manifestBytes = requireNotNull(entries["manifest.json"]) { "manifest.json ausente" }
            val payload = entries["tensors.f32"] ?: ByteArray(0)
            require(payload.size % 4 == 0) { "payload FP32 desalinhado" }
            val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
            require(manifest.optString("format") == FORMAT && manifest.optInt("version") == 1) {
                "formato RSIW não suportado"
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(sha.equals(manifest.optString("tensor_sha256"), ignoreCase = true)) { "SHA-256 dos tensores não confere" }

            val architecture = manifest.optString("architecture")
            require(architecture in setOf("rdt", "modusx", "lunaris_moc")) { "arquitetura RSIW desconhecida" }
            val hidden = manifest.optInt("hidden_size")
            require(hidden in 1..512 || (architecture == "rdt" && hidden in 1..2048)) { "hidden_size fora do limite nativo" }
            val experts = manifest.optInt("experts", 0)
            val topK = manifest.optInt("top_k", 1)
            val steps = manifest.optInt("reasoning_steps", 1)
            require(steps in 1..(if (architecture == "rdt") 16 else 8)) { "reasoning_steps inválido" }
            if (architecture == "lunaris_moc") require(experts in 2..16 && topK in 1..experts) { "configuração MoC inválida" }

            val descriptors = manifest.optJSONObject("tensors") ?: JSONObject()
            val tensors = HashMap<String, FloatArray>()
            val tensorRanges = ArrayList<Pair<Long, Long>>()
            val required = when (architecture) {
                "rdt" -> mapOf("input_weights" to hidden*hidden, "recurrent_weights" to hidden*hidden,
                    "bias" to hidden, "gate" to hidden)
                "lunaris_moc" -> mapOf("router_weights" to experts*hidden, "router_bias" to experts,
                    "expert_weights" to experts*hidden*hidden, "expert_bias" to experts*hidden,
                    "mediator_weights" to hidden*hidden, "mediator_bias" to hidden,
                    "fusion_weights" to hidden*hidden, "fusion_bias" to hidden)
                else -> emptyMap()
            }
            required.forEach { (name, expectedCount) ->
                val descriptor = descriptors.optJSONObject(name) ?: error("tensor obrigatório ausente: $name")
                val offset = descriptor.optLong("offset", -1)
                val count = descriptor.optInt("count", -1)
                require(offset >= 0 && count == expectedCount && offset + count <= payload.size / 4L) {
                    "dimensão/offset inválido no tensor $name"
                }
                val floats = FloatArray(count)
                ByteBuffer.wrap(payload, (offset * 4).toInt(), count * 4)
                    .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                require(floats.all { it.isFinite() }) { "tensor $name contém NaN/Inf" }
                tensors[name] = floats
                tensorRanges.add(offset to (offset + count))
            }
            var cursor = 0L
            tensorRanges.sortedBy { it.first }.forEach { range ->
                require(range.first == cursor) { "tensores sobrepostos ou com lacunas" }
                cursor = range.second
            }
            require(cursor * 4L == payload.size.toLong()) { "payload contém dados não referenciados" }
            val decay = manifest.optDouble("decay", 0.95).toFloat()
            val learningRate = manifest.optDouble("learning_rate", 0.001).toFloat()
            require(decay.isFinite() && decay in 0f..1f) { "decay inválido" }
            require(learningRate.isFinite() && learningRate in 0f..1f) { "learning_rate inválido" }
            return RsiWeightBundle(
                architecture, hidden, experts, topK, steps, decay, learningRate, tensors,
            )
        }
    }
}
