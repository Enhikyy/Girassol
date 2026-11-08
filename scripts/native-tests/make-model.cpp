#include "gguf.h"
#include "ggml.h"
#include "llama.h"

#include <random>
#include <string>
#include <vector>

// Synthetic ~3M parameter Llama; only tokenizer metadata comes from a real GGUF.
int main(int argc, char ** argv) {
    if (argc != 3) return 2;
    const std::string directory = argv[2];
    gguf_init_params read_params{true, nullptr};
    auto * source = gguf_init_from_file(argv[1], read_params);
    if (!source) return 1;
    auto * file = gguf_init_empty();
    gguf_set_kv(file, source);
    gguf_set_val_str(file, "general.architecture", "llama");
    gguf_set_val_u32(file, "llama.context_length", 512);
    gguf_set_val_u32(file, "llama.embedding_length", 32);
    gguf_set_val_u32(file, "llama.feed_forward_length", 64);
    gguf_set_val_u32(file, "llama.block_count", 2);
    gguf_set_val_u32(file, "llama.attention.head_count", 4);
    gguf_set_val_u32(file, "llama.attention.head_count_kv", 2);
    gguf_set_val_u32(file, "llama.rope.dimension_count", 8);
    gguf_set_val_u32(file, "general.file_type", 0);
    const auto vocab = gguf_get_arr_n(file, gguf_find_key(file, "tokenizer.ggml.tokens"));
    auto * context = ggml_init({32 * 1024 * 1024, nullptr, false});
    std::mt19937 rng(17);
    std::normal_distribution<float> random(0, 0.02);
    auto add = [&](const std::string & name, int inputs, int outputs = 0) {
        auto * tensor = outputs ? ggml_new_tensor_2d(context, GGML_TYPE_F32, inputs, outputs)
                                : ggml_new_tensor_1d(context, GGML_TYPE_F32, inputs);
        ggml_set_name(tensor, name.c_str());
        auto * data = static_cast<float *>(tensor->data);
        for (int64_t i = 0; i < ggml_nelements(tensor); ++i) data[i] = outputs ? random(rng) : 1.0f;
        gguf_add_tensor(file, tensor);
    };
    add("token_embd.weight", 32, vocab);
    add("output_norm.weight", 32);
    add("output.weight", 32, vocab);
    for (int i = 0; i < 2; ++i) {
        const auto block = "blk." + std::to_string(i) + ".";
        add(block + "attn_norm.weight", 32);
        add(block + "attn_q.weight", 32, 32);
        add(block + "attn_k.weight", 32, 16);
        add(block + "attn_v.weight", 32, 16);
        add(block + "attn_output.weight", 32, 32);
        add(block + "ffn_norm.weight", 32);
        add(block + "ffn_gate.weight", 32, 64);
        add(block + "ffn_down.weight", 64, 32);
        add(block + "ffn_up.weight", 32, 64);
    }
    const bool ok = gguf_write_to_file(file, (directory + "/tiny-f32.gguf").c_str(), false);
    gguf_free(file);
    gguf_free(source);
    ggml_free(context);
    if (!ok) return 1;

    llama_backend_init();
    for (const auto & item : std::vector<std::pair<llama_ftype, std::string>>{
             {LLAMA_FTYPE_MOSTLY_F16, "f16"}, {LLAMA_FTYPE_MOSTLY_BF16, "bf16"}, {LLAMA_FTYPE_MOSTLY_Q8_0, "q8"}}) {
        auto params = llama_model_quantize_default_params();
        params.ftype = item.first;
        params.nthread = 2;
        if (llama_model_quantize((directory + "/tiny-f32.gguf").c_str(),
                                (directory + "/tiny-" + item.second + ".gguf").c_str(), &params)) return 1;
    }
    llama_backend_free();
    return 0;
}
