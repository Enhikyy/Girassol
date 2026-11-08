#include <jni.h>
#include "llama.h"
#include "nlohmann/json.hpp"
#include <memory>
#include <string>
#include <vector>
#include <stdexcept>
using json = nlohmann::ordered_json;
static std::string utf8(JNIEnv *env, jbyteArray bytes) {
    if (!bytes) return {};
    std::string text(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<jbyte *>(text.data()));
    return text;
}
static jbyteArray bytes(JNIEnv *env, const std::string &s) {
    auto out = env->NewByteArray(s.size());
    env->SetByteArrayRegion(out, 0, s.size(), reinterpret_cast<const jbyte *>(s.data()));
    return out;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeGenerate(JNIEnv *env, jobject,
    jbyteArray path, jbyteArray conversation, jbyteArray adapterPath,
    jint contextSize, jint maxTokens, jint threads, jfloat temperature, jobject callback) {
    json result;
    try {
        llama_backend_init();
        struct Backend { ~Backend() { llama_backend_free(); } } backend;
        auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
        std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
            llama_model_load_from_file(utf8(env, path).c_str(), mp), llama_model_free);
        if (!model) throw std::runtime_error("Não foi possível carregar o GGUF");
        auto cp = llama_context_default_params();
        cp.n_ctx = contextSize; cp.n_batch = 256; cp.n_ubatch = 256;
        cp.n_threads = threads; cp.n_threads_batch = threads;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
        if (!ctx) throw std::runtime_error("RAM insuficiente para o contexto");
        const auto adapterFile = utf8(env, adapterPath);
        if (!adapterFile.empty()) {
            json specs = adapterFile[0] == '[' ? json::parse(adapterFile) : json::array({{{"path", adapterFile}, {"scale", 1.0f}}});
            std::vector<llama_adapter_lora *> adapters;
            std::vector<float> scales;
            for (const auto &spec : specs) {
                auto *adapter = llama_adapter_lora_init(model.get(), spec.at("path").get<std::string>().c_str());
                if (!adapter) throw std::runtime_error("Adaptador LoRA incompatível");
                adapters.push_back(adapter); scales.push_back(spec.value("scale", 1.0f));
            }
            if (llama_set_adapters_lora(ctx.get(), adapters.data(), adapters.size(), scales.data()) != 0)
                throw std::runtime_error("Falha ao aplicar adaptadores LoRA");
        }
        auto messagesJson = json::parse(utf8(env, conversation));
        std::vector<std::string> roles, contents;
        for (const auto &m : messagesJson) {
            roles.push_back(m.value("role", "user"));
            contents.push_back(m.value("content", std::string{}));
        }
        std::vector<llama_chat_message> messages;
        for (size_t i = 0; i < roles.size(); ++i) messages.push_back({roles[i].c_str(), contents[i].c_str()});
        const char *tmpl = llama_model_chat_template(model.get(), nullptr);
        if (!tmpl) throw std::runtime_error("GGUF sem chat template; use um modelo instruct");
        int length = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
        if (length < 0) throw std::runtime_error("Chat template não suportado pelo motor");
        std::vector<char> prompt(length + 1);
        llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, prompt.data(), prompt.size());
        auto *vocab = llama_model_get_vocab(model.get());
        int n = -llama_tokenize(vocab, prompt.data(), length, nullptr, 0, true, true);
        if (n <= 0 || n + maxTokens > (int)llama_n_ctx(ctx.get()))
            throw std::runtime_error("Conversa excede o contexto local; inicie uma nova sessão ou aumente o contexto");
        std::vector<llama_token> tokens(n);
        n = llama_tokenize(vocab, prompt.data(), length, tokens.data(), tokens.size(), true, true);
        if (n <= 0) throw std::runtime_error("Falha ao tokenizar conversa");
        const auto callbackClass = env->GetObjectClass(callback);
        const auto tokenMethod = env->GetMethodID(callbackClass, "onToken", "([B)Z");
        if (!tokenMethod) return nullptr;
        auto active = [&](const std::string &piece) {
            auto b = bytes(env, piece);
            bool ok = env->CallBooleanMethod(callback, tokenMethod, b);
            env->DeleteLocalRef(b);
            return ok && !env->ExceptionCheck();
        };
        for (int i = 0; i < n; i += 256) {
            if (!active("")) return nullptr;
            auto batch = llama_batch_get_one(tokens.data() + i, std::min(256, n-i));
            if (llama_decode(ctx.get(), batch) != 0) throw std::runtime_error("Falha no prefill local");
        }
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        if (temperature <= 0) llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
        else {
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.95f, 1));
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }
        std::string output; int generated = 0;
        for (int i = 0; i < maxTokens; ++i) {
            auto token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            std::vector<char> piece(256);
            int count = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (count < 0) { piece.resize(-count); count = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
            if (count < 0) throw std::runtime_error("Falha ao decodificar token");
            output.append(piece.data(), count); ++generated;
            if (!active(std::string(piece.data(), count))) return nullptr;
            if (i + 1 < maxTokens) {
                auto batch = llama_batch_get_one(&token, 1);
                if (llama_decode(ctx.get(), batch) != 0) throw std::runtime_error("Falha na geração local");
            }
        }
        result = {{"content", output}, {"prompt_tokens", n}, {"completion_tokens", generated}};
    } catch (const std::exception &e) { result = {{"error", e.what()}}; }
    return bytes(env, result.dump());
}
