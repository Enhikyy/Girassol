#include "llama.h"
#include "ggml-opt.h"
#include "ggml-backend.h"
#include "common.h"
#include "llama-adapter.h"
#include "llama-model.h"
#include "llama-arch.h"
#include <gguf.h>

#include <cstdio>
#include <cstring>
#include <fstream>
#include <random>
#include <set>
#include <string>
#include <vector>

static bool lora_filter(const ggml_tensor * t, void *) {
    return t && t->name[0] && (strstr(t->name, ".lora_a") || strstr(t->name, ".lora_b"));
}

struct Targets { std::vector<std::pair<std::string, ggml_tensor *>> items; int rank; float alpha; bool ffn; };

static std::shared_ptr<llama_adapter_lora> build_lora(llama_model * model, Targets & t, std::string & err) {
    auto adapter = std::make_shared<llama_adapter_lora>(model);
    adapter->alpha = t.alpha;
    std::set<ggml_tensor *> seen;
    auto add = [&](ggml_tensor * w) {
        if (!w || ggml_n_dims(w) != 2 || !w->name[0]) return;
        if (!seen.insert(w).second) return;
        t.items.emplace_back(std::string(w->name), w);
    };
    for (auto & layer : model->layers) {
        add(layer.wq); add(layer.wk); add(layer.wv); add(layer.wo); add(layer.wqkv);
        add(layer.wq_a); add(layer.wq_b); add(layer.wkv_a_mqa); add(layer.wkv_b); add(layer.wkv);
        add(layer.wk_b); add(layer.wv_b); add(layer.wqkv_b); add(layer.wo_a); add(layer.wo_b);
        if (t.ffn) { add(layer.ffn_gate); add(layer.ffn_down); add(layer.ffn_up); }
    }
    if (t.items.empty()) { err = "no targets"; return nullptr; }
    ggml_init_params ip = { (t.items.size()*2 + 16) * ggml_tensor_overhead(), nullptr, true };
    ggml_context * gctx = ggml_init(ip);
    for (auto & [name, w] : t.items) {
        int r = std::min<int>(t.rank, std::min<int>((int)w->ne[0], (int)w->ne[1]));
        auto * a = ggml_new_tensor_2d(gctx, GGML_TYPE_F32, w->ne[0], r);
        auto * b = ggml_new_tensor_2d(gctx, GGML_TYPE_F32, r, w->ne[1]);
        ggml_set_name(a, (name + ".lora_a").c_str());
        ggml_set_name(b, (name + ".lora_b").c_str());
        adapter->ab_map[name] = llama_adapter_lora_weight(a, b);
    }
    adapter->ctxs.emplace_back(gctx);
    auto * buf = ggml_backend_alloc_ctx_tensors_from_buft(gctx, ggml_backend_cpu_buffer_type());
    if (!buf) { err = "alloc failed"; return nullptr; }
    adapter->bufs.emplace_back(buf);
    std::mt19937 rng(1337); std::normal_distribution<float> nd(0.f, 0.02f);
    std::vector<float> tmp;
    for (auto & [name, w] : t.items) {
        auto & lw = adapter->ab_map[name];
        tmp.resize((size_t) ggml_nelements(lw.a)); for (auto & v : tmp) v = nd(rng);
        ggml_backend_tensor_set(lw.a, tmp.data(), 0, ggml_nbytes(lw.a));
        std::fill(tmp.begin(), tmp.end(), 0.f);
        ggml_backend_tensor_set(lw.b, tmp.data(), 0, ggml_nbytes(lw.b));
        ggml_set_param(lw.a); ggml_set_param(lw.b);
    }
    return adapter;
}

static bool save_lora(const std::shared_ptr<llama_adapter_lora> & ad, llama_model * model, float alpha, const std::string & path) {
    gguf_context * g = gguf_init_empty();
    gguf_set_val_str(g, "general.type", "adapter");
    gguf_set_val_str(g, "general.name", "Girassol LoRA test");
    gguf_set_val_str(g, "general.architecture", llm_arch_name(model->arch));
    gguf_set_val_str(g, "adapter.type", "lora");
    gguf_set_val_f32(g, "adapter.lora.alpha", alpha);
    gguf_set_val_u32(g, "general.file_type", 0);
    for (auto & kv : ad->ab_map) { gguf_add_tensor(g, kv.second.a); gguf_add_tensor(g, kv.second.b); }
    bool ok = gguf_write_to_file(g, path.c_str(), false);
    gguf_free(g);
    return ok;
}

static void cb(bool train, ggml_opt_context_t, ggml_opt_dataset_t, ggml_opt_result_t r,
               int64_t ib, int64_t imax, int64_t) {
    if (!r) return;
    double loss = 0, unc = 0; ggml_opt_result_loss(r, &loss, &unc);
    printf("  %s %lld/%lld loss=%.6f\n", train ? "train" : "eval", (long long) ib, (long long) imax, loss);
}

int main(int argc, char ** argv) {
    if (argc < 4) { printf("usage: %s model.gguf data.txt out.gguf\n", argv[0]); return 1; }
    std::ifstream f(argv[2], std::ios::binary);
    std::string text((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
    printf("dataset chars=%zu\n", text.size());

    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    llama_model * model = llama_model_load_from_file(argv[1], mp);
    if (!model) { printf("model load failed\n"); return 2; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 64; cp.n_batch = 64; cp.n_ubatch = 64;
    cp.n_threads = 2; cp.n_threads_batch = 2;
    cp.type_k = GGML_TYPE_F32; cp.type_v = GGML_TYPE_F32;
    cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
    cp.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) { printf("ctx failed\n"); return 3; }

    const llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<llama_token> tokens(text.size() + 8);
    int n = llama_tokenize(vocab, text.c_str(), (int) text.size(), tokens.data(), (int) tokens.size(), true, true);
    if (n < 0) { tokens.resize(-n); n = llama_tokenize(vocab, text.c_str(), (int)text.size(), tokens.data(), (int)tokens.size(), true, true); }
    tokens.resize(n);
    printf("tokens=%d\n", n);

    Targets t; t.rank = 8; t.alpha = 8.f; t.ffn = false;
    std::string err;
    auto adapter = build_lora(model, t, err);
    if (!adapter) { printf("lora build failed: %s\n", err.c_str()); return 4; }
    printf("targets=%zu\n", t.items.size());

    llama_adapter_lora * raw = adapter.get();
    float scale = 1.0f;
    llama_set_adapters_lora(ctx, &raw, 1, &scale);

    ggml_opt_dataset_t ds = common_opt_dataset_init(ctx, tokens, 32);
    printf("ndata=%lld\n", (long long) ggml_opt_dataset_ndata(ds));

    struct lr_opt lr;
    lr.lr0 = 3e-4f; lr.epochs = 8; lr.lr_min = 3e-5f; lr.decay_epochs = 8; lr.init();

    llama_opt_params lop{};
    lop.n_ctx_train = 64;
    lop.param_filter = lora_filter;
    lop.param_filter_ud = nullptr;
    lop.get_opt_pars = common_opt_lr_pars;
    lop.get_opt_pars_ud = &lr;
    lop.optimizer_type = GGML_OPT_OPTIMIZER_TYPE_ADAMW;
    llama_opt_init(ctx, model, lop);
    printf("opt initialized\n");

    int64_t split = (int64_t)(ggml_opt_dataset_ndata(ds) * 0.95);
    auto rt = ggml_opt_result_init();
    auto re = ggml_opt_result_init();
    for (lr.epoch = 0; lr.epoch < lr.epochs; ++lr.epoch) {
        printf("epoch %u\n", lr.epoch + 1);
        llama_opt_epoch(ctx, ds, rt, re, split, cb, cb);
        double loss = 0, unc = 0; ggml_opt_result_loss(re, &loss, &unc);
        printf("epoch %u eval_loss=%.6f\n", lr.epoch + 1, loss);
        ggml_opt_result_reset(rt); ggml_opt_result_reset(re);
    }

    bool saved = save_lora(adapter, model, t.alpha, argv[3]);
    printf("saved=%d\n", saved ? 1 : 0);

    llama_adapter_lora * loaded = llama_adapter_lora_init(model, argv[3]);
    printf("reloaded=%d\n", loaded ? 1 : 0);
    if (loaded) llama_adapter_lora_free(loaded);

    ggml_opt_result_free(rt); ggml_opt_result_free(re);
    ggml_opt_dataset_free(ds);
    llama_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    return saved && loaded ? 0 : 5;
}
