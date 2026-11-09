// LabForge native engine — GGUF quantization (imatrix-aware) + on-device fine-tuning
// Built on llama.cpp (MIT). JNI surface is intentionally small and stable.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <string>
#include <thread>
#include <vector>

#include <fcntl.h>
#include <unistd.h>

#include "llama.h"
#include "ggml-opt.h"
#include "ggml-backend.h"
#include "common.h"
#include "imatrix-loader.h"

// internal llama.cpp headers, needed to build a LoRA adapter from scratch
#include "llama-adapter.h"
#include "llama-model.h"
#include "llama-arch.h"

#include <gguf.h>
#include <random>
#include <set>

#define LOG_TAG "LabForgeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// JVM / callback plumbing
// -----------------------------------------------------------------------------

static JavaVM * g_vm = nullptr;
static jclass    g_cb_class = nullptr;
static jmethodID g_cb_log = nullptr;
static jmethodID g_cb_epoch = nullptr;

static jobject   g_cb_obj = nullptr;
static JNIEnv  * g_call_env = nullptr;

// stdout/stderr capture so native logs stream into the Compose console
static int  g_saved_out = -1;
static int  g_saved_err = -1;
static int  g_pipe_rd = -1;
static int  g_pipe_wr = -1;
static std::atomic<bool> g_capture_run{false};
static std::thread g_reader;
static void capture_stop();

static std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

// NewStringUTF expects Modified UTF-8: 4-byte UTF-8 (emoji, some CJK) is
// invalid and can abort ART. Decode real UTF-8 into UTF-16 explicitly.
static jstring new_string(JNIEnv * env, const std::string & s) {
    std::u16string out;
    out.reserve(s.size());
    for (size_t i = 0; i < s.size();) {
        const unsigned char c = (unsigned char) s[i];
        uint32_t cp = 0xFFFD;
        size_t len = 1;
        if (c < 0x80) { cp = c; }
        else if ((c & 0xE0) == 0xC0 && i + 1 < s.size()) {
            cp = ((c & 0x1F) << 6) | ((unsigned char) s[i + 1] & 0x3F); len = 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < s.size()) {
            cp = ((c & 0x0F) << 12) | (((unsigned char) s[i + 1] & 0x3F) << 6) |
                 ((unsigned char) s[i + 2] & 0x3F); len = 3;
        } else if ((c & 0xF8) == 0xF0 && i + 3 < s.size()) {
            cp = ((c & 0x07) << 18) | (((unsigned char) s[i + 1] & 0x3F) << 12) |
                 (((unsigned char) s[i + 2] & 0x3F) << 6) | ((unsigned char) s[i + 3] & 0x3F); len = 4;
        }
        if (cp <= 0xFFFF) out.push_back((char16_t) cp);
        else {
            cp -= 0x10000;
            out.push_back((char16_t) (0xD800 + (cp >> 10)));
            out.push_back((char16_t) (0xDC00 + (cp & 0x3FF)));
        }
        i += len;
    }
    return env->NewString(reinterpret_cast<const jchar *>(out.data()), (jsize) out.size());
}

static std::string json_escape(const std::string & in) {
    std::string out;
    out.reserve(in.size() + 8);
    for (char c : in) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if ((unsigned char) c < 0x20) { char b[8]; snprintf(b, sizeof(b), "\\u%04x", c); out += b; }
                else out += c;
        }
    }
    return out;
}

static void emit_log(const std::string & line) {
    if (!g_cb_obj || !g_cb_log || !g_vm) return;
    if (line.empty()) return;
    // JNIEnv is thread-private; this runs on the pipe reader thread.
    JNIEnv * env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jstring js = new_string(env, line);
    env->CallVoidMethod(g_cb_obj, g_cb_log, js);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(js);
    if (attached) g_vm->DetachCurrentThread();
}

static void reader_loop() {
    std::string acc;
    char buf[4096];
    while (true) {
        ssize_t n = read(g_pipe_rd, buf, sizeof(buf));
        if (n <= 0) break;
        for (ssize_t i = 0; i < n; ++i) {
            char c = buf[i];
            if (c == '\n' || c == '\r') {
                if (!acc.empty()) { emit_log(acc); acc.clear(); }
            } else if (c != 0) {
                acc.push_back(c);
                if (acc.size() > 4000) { emit_log(acc); acc.clear(); }
            }
        }
    }
    if (!acc.empty()) emit_log(acc);
}

static void capture_start(JNIEnv * env, jobject cb) {
    if (g_reader.joinable() || g_pipe_wr >= 0 || g_cb_obj || g_cb_class) capture_stop();
    g_call_env = nullptr;
    if (cb) {
        g_cb_obj = env->NewGlobalRef(cb);
        jclass local = env->GetObjectClass(cb);
        g_cb_class = (jclass) env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
        g_cb_log   = env->GetMethodID(g_cb_class, "onLog", "(Ljava/lang/String;)V");
        g_cb_epoch = env->GetMethodID(g_cb_class, "onEpoch", "(IIIDD)V");
        if (!g_cb_epoch) env->ExceptionClear();
    }
    int fds[2];
    if (pipe(fds) != 0) return;
    g_pipe_rd = fds[0];
    g_pipe_wr = fds[1];
    g_saved_out = dup(STDOUT_FILENO);
    g_saved_err = dup(STDERR_FILENO);
    dup2(g_pipe_wr, STDOUT_FILENO);
    dup2(g_pipe_wr, STDERR_FILENO);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IOLBF, 0);
    g_capture_run = true;
    g_reader = std::thread(reader_loop);
}

static void capture_stop() {
    fflush(stdout);
    fflush(stderr);
    if (g_saved_out >= 0) { dup2(g_saved_out, STDOUT_FILENO); close(g_saved_out); g_saved_out = -1; }
    if (g_saved_err >= 0) { dup2(g_saved_err, STDERR_FILENO); close(g_saved_err); g_saved_err = -1; }
    if (g_pipe_wr >= 0) { close(g_pipe_wr); g_pipe_wr = -1; }
    if (g_reader.joinable()) g_reader.join();
    if (g_pipe_rd >= 0) { close(g_pipe_rd); g_pipe_rd = -1; }
    JNIEnv * env = nullptr;
    bool attached = false;
    if (g_vm && g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) attached = true; else env = nullptr;
    }
    if (env && g_cb_obj) { env->DeleteGlobalRef(g_cb_obj); g_cb_obj = nullptr; }
    if (env && g_cb_class) { env->DeleteGlobalRef(g_cb_class); g_cb_class = nullptr; g_cb_log = nullptr; g_cb_epoch = nullptr; }
    if (attached) g_vm->DetachCurrentThread();
    g_capture_run = false;
}

// -----------------------------------------------------------------------------
// ftype mapping
// -----------------------------------------------------------------------------

static int ftype_from_name(const std::string & s) {
    struct Entry { const char * name; int ftype; };
    static const Entry table[] = {
        {"F32",      LLAMA_FTYPE_ALL_F32},
        {"F16",      LLAMA_FTYPE_MOSTLY_F16},
        {"BF16",     LLAMA_FTYPE_MOSTLY_BF16},
        {"Q8_0",     LLAMA_FTYPE_MOSTLY_Q8_0},
        {"Q6_K",     LLAMA_FTYPE_MOSTLY_Q6_K},
        {"Q5_0",     LLAMA_FTYPE_MOSTLY_Q5_0},
        {"Q5_1",     LLAMA_FTYPE_MOSTLY_Q5_1},
        {"Q5_K_S",   LLAMA_FTYPE_MOSTLY_Q5_K_S},
        {"Q5_K_M",   LLAMA_FTYPE_MOSTLY_Q5_K_M},
        {"Q4_0",     LLAMA_FTYPE_MOSTLY_Q4_0},
        {"Q4_1",     LLAMA_FTYPE_MOSTLY_Q4_1},
        {"Q4_K_S",   LLAMA_FTYPE_MOSTLY_Q4_K_S},
        {"Q4_K_M",   LLAMA_FTYPE_MOSTLY_Q4_K_M},
        {"Q3_K_S",   LLAMA_FTYPE_MOSTLY_Q3_K_S},
        {"Q3_K_M",   LLAMA_FTYPE_MOSTLY_Q3_K_M},
        {"Q3_K_L",   LLAMA_FTYPE_MOSTLY_Q3_K_L},
        {"Q2_K",     LLAMA_FTYPE_MOSTLY_Q2_K},
        {"Q2_K_S",   LLAMA_FTYPE_MOSTLY_Q2_K_S},
        {"Q1_0",     LLAMA_FTYPE_MOSTLY_Q1_0},
        {"Q2_0",     LLAMA_FTYPE_MOSTLY_Q2_0},
        {"IQ1_S",    LLAMA_FTYPE_MOSTLY_IQ1_S},
        {"IQ1_M",    LLAMA_FTYPE_MOSTLY_IQ1_M},
        {"IQ2_XXS",  LLAMA_FTYPE_MOSTLY_IQ2_XXS},
        {"IQ2_XS",   LLAMA_FTYPE_MOSTLY_IQ2_XS},
        {"IQ2_S",    LLAMA_FTYPE_MOSTLY_IQ2_S},
        {"IQ2_M",    LLAMA_FTYPE_MOSTLY_IQ2_M},
        {"IQ3_XXS",  LLAMA_FTYPE_MOSTLY_IQ3_XXS},
        {"IQ3_XS",   LLAMA_FTYPE_MOSTLY_IQ3_XS},
        {"IQ3_S",    LLAMA_FTYPE_MOSTLY_IQ3_S},
        {"IQ3_M",    LLAMA_FTYPE_MOSTLY_IQ3_M},
        {"IQ4_NL",   LLAMA_FTYPE_MOSTLY_IQ4_NL},
        {"IQ4_XS",   LLAMA_FTYPE_MOSTLY_IQ4_XS},
        {"TQ1_0",    LLAMA_FTYPE_MOSTLY_TQ1_0},
        {"TQ2_0",    LLAMA_FTYPE_MOSTLY_TQ2_0},
        {"MXFP4_MOE",LLAMA_FTYPE_MOSTLY_MXFP4_MOE},
        {"NVFP4",    LLAMA_FTYPE_MOSTLY_NVFP4},
        {"F8_E4M3",  LLAMA_FTYPE_MOSTLY_F8_E4M3},
    };
    for (const auto & e : table) if (s == e.name) return e.ftype;
    return -1;
}

// -----------------------------------------------------------------------------
// imatrix loading (GGUF imatrix + legacy .dat), mirrors tools/quantize
// -----------------------------------------------------------------------------

static bool load_imatrix(const std::string & file,
                         std::vector<std::string> & names,
                         std::vector<std::vector<float>> & values,
                         int & chunks,
                         std::string & err) {
    common_imatrix loaded;
    if (!common_imatrix_load(file, loaded)) {
        err = "falha ao ler imatrix: " + file;
        return false;
    }
    if (!loaded.is_legacy && !loaded.has_metadata) {
        err = "imatrix GGUF sem metadados (chunk_count/chunk_size)";
        return false;
    }
    for (const auto & kv : loaded.entries) {
        const auto & entry = kv.second;
        std::vector<float> e;
        if (entry.sums.empty()) continue;
        e.resize(entry.sums.size());
        if (!loaded.is_legacy) {
            const int64_t ncounts = (int64_t) entry.counts.size();
            if (ncounts == 0) continue;
            const int64_t ne0 = (int64_t) entry.sums.size() / ncounts;
            for (int64_t j = 0; j < ncounts; ++j) {
                const float count = (float) entry.counts[j];
                for (int64_t i = 0; i < ne0; ++i) {
                    e[j * ne0 + i] = count > 0.0f ? entry.sums[j * ne0 + i] / count : 1.0f;
                }
            }
        } else {
            const int64_t ncall = entry.counts.empty() ? 0 : entry.counts[0];
            for (size_t i = 0; i < entry.sums.size(); ++i) {
                e[i] = ncall > 0 ? entry.sums[i] / (float) ncall : entry.sums[i];
            }
        }
        names.push_back(kv.first);
        values.push_back(std::move(e));
    }
    chunks = loaded.chunk_count;
    return true;
}

// -----------------------------------------------------------------------------
// Quantize
// -----------------------------------------------------------------------------

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeQuantize(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_in, jstring j_out, jstring j_ftype, jstring j_imatrix,
        jint nthreads, jobject cb) {

    const std::string in   = jstr(env, j_in);
    const std::string out  = jstr(env, j_out);
    const std::string fts  = jstr(env, j_ftype);
    const std::string imat = jstr(env, j_imatrix);

    capture_start(env, cb);

    std::string err;
    std::string result;
    uint32_t rc = 1;
    uint64_t out_size = 0;
    const std::string out_tmp = out + ".part";

    const int ftype = ftype_from_name(fts);
    if (ftype < 0) {
        err = "tipo de quantização desconhecido: " + fts;
    } else if (in.empty() || out.empty()) {
        err = "caminho de entrada/saída vazio";
    } else {
        std::vector<std::string> names;
        std::vector<std::vector<float>> values;
        std::vector<llama_model_imatrix_data> idata;
        int chunks = 0;

        if (!imat.empty()) {
            if (!load_imatrix(imat, names, values, chunks, err)) {
                capture_stop();
                result = "{\"ok\":false,\"error\":\"" + json_escape(err) + "\"}";
                return new_string(env, result);
            }
            printf("[imatrix] %zu tensores carregados, %d chunks\n", names.size(), chunks);
            idata.reserve(names.size() + 1);
            for (size_t i = 0; i < names.size(); ++i) {
                idata.push_back({names[i].c_str(), values[i].data(), values[i].size()});
            }
            idata.push_back({nullptr, nullptr, 0});
        }

        llama_backend_init();

        llama_model_quantize_params params = llama_model_quantize_default_params();
        params.ftype   = (llama_ftype) ftype;
        params.nthread = nthreads > 0 ? nthreads : (int32_t) std::thread::hardware_concurrency();
        params.allow_requantize = true;
        params.quantize_output_tensor = true;
        if (!idata.empty()) params.imatrix = idata.data();

        printf("[quantize] %s -> %s  (%s, %d threads)\n", in.c_str(), out.c_str(), fts.c_str(), params.nthread);
        rc = llama_model_quantize(in.c_str(), out_tmp.c_str(), &params);
        if (rc == 0 && std::rename(out_tmp.c_str(), out.c_str()) != 0) { err = "não foi possível finalizar a saída"; rc = 1; }
        if (rc == 0) {
            FILE * f = fopen(out.c_str(), "rb");
            if (f) { fseek(f, 0, SEEK_END); out_size = (uint64_t) ftell(f); fclose(f); }
            printf("[quantize] concluído: %.2f MiB\n", out_size / 1048576.0);
        } else {
            std::remove(out_tmp.c_str());
            printf("[quantize] falhou (código %u)\n", rc);
        }
        llama_backend_free();
    }

    capture_stop();

    result = "{\"ok\":" + std::string(rc == 0 ? "true" : "false") +
             ",\"bytes\":" + std::to_string(out_size) +
             ",\"error\":\"" + json_escape(err) + "\"}";
    return new_string(env, result);
}

// -----------------------------------------------------------------------------
// Fine-tune (llama_opt_* public training API)
// -----------------------------------------------------------------------------

struct TrainFilter {
    std::vector<std::string> include;
    bool all = true;
};

static bool train_param_filter(const ggml_tensor * t, void * ud) {
    auto * f = (TrainFilter *) ud;
    if (f->all) return true;
    if (!t || !t->name[0]) return false;
    const std::string name = t->name;
    for (const auto & s : f->include) {
        if (name.find(s) != std::string::npos) return true;
    }
    return false;
}

static std::atomic<int> g_train_epoch{0};
static std::atomic<float> g_train_lr{0.f};

static void train_epoch_cb(bool train, ggml_opt_context_t, ggml_opt_dataset_t,
                           ggml_opt_result_t result, int64_t ibatch,
                           int64_t ibatch_max, int64_t) {
    if (!train) return; // eval callbacks restart step at 0
    if (!g_cb_obj || !g_cb_epoch || !g_call_env) return;
    double loss = 0.0, unc = 0.0;
    if (result) ggml_opt_result_loss(result, &loss, &unc);
    g_call_env->CallVoidMethod(g_cb_obj, g_cb_epoch, (jint) g_train_epoch.load(), (jint) ibatch,
                               (jint) ibatch_max, (jdouble) loss, (jdouble) g_train_lr.load());
    if (g_call_env->ExceptionCheck()) g_call_env->ExceptionClear();
    (void) train;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeFinetune(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model, jstring j_out, jstring j_dataset,
        jint epochs, jfloat lr0, jint n_ctx, jint n_batch, jint n_ubatch,
        jint nthreads, jstring j_filter, jobject cb) {

    const std::string model_path = jstr(env, j_model);
    const std::string out_path   = jstr(env, j_out);
    const std::string data_path  = jstr(env, j_dataset);
    const std::string filter     = jstr(env, j_filter);

    capture_start(env, cb);
    g_call_env = env;

    std::string err;
    bool ok = false;
    double final_loss = 0.0;
    int n_tokens = 0, n_seq = 0;

    if (model_path.empty() || out_path.empty() || data_path.empty()) {
        err = "parâmetros vazios";
    } else {
        std::ifstream f(data_path, std::ios::binary);
        if (!f) {
            err = "não foi possível abrir o dataset";
        } else {
            std::string text((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
            f.close();
            if (text.size() < 64) {
                err = "dataset muito pequeno";
            } else {
                llama_backend_init();

                llama_model_params mp = llama_model_default_params();
                mp.n_gpu_layers = 0;
                mp.load_mode = LLAMA_LOAD_MODE_NONE;   // writable weights
                mp.use_extra_bufts = false;

                llama_model * model = llama_model_load_from_file(model_path.c_str(), mp);
                if (!model) {
                    err = "falha ao carregar o modelo (use um GGUF F32/F16)";
                } else {
                    const llama_vocab * vocab = llama_model_get_vocab(model);

                    llama_context_params cp = llama_context_default_params();
                    cp.n_ctx = n_ctx > 0 ? n_ctx : (int) llama_model_n_ctx_train(model);
                    // llama.cpp only builds K/V training gradients when n_ubatch == n_ctx.
                    cp.n_batch = cp.n_ctx;
                    cp.n_ubatch = cp.n_ctx;
                    cp.n_threads = nthreads > 0 ? nthreads : (int) std::thread::hardware_concurrency();
                    cp.n_threads_batch = cp.n_threads;
                    cp.type_k = GGML_TYPE_F32;
                    cp.type_v = GGML_TYPE_F32;
                    cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
                    cp.no_perf = true;

                    llama_context * ctx = llama_init_from_model(model, cp);
                    if (!ctx) {
                        err = "falha ao criar o contexto de treino";
                    } else {
                        std::vector<llama_token> tokens(text.size() + 8);
                        int n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                               tokens.data(), (int32_t) tokens.size(), true, true);
                        if (n < 0) {
                            tokens.resize(-n);
                            n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                               tokens.data(), (int32_t) tokens.size(), true, true);
                        }
                        const int64_t ctx_len = llama_n_ctx(ctx);
                        const int64_t stride  = ctx_len / 2;
                        if (n < ctx_len + 1 + 2 * stride) {
                            err = "dataset curto demais para o contexto escolhido (" + std::to_string(n) +
                                  " tokens; mínimo " + std::to_string(ctx_len + 1 + 2 * stride) + ")";
                        } else {
                            tokens.resize(n);
                            n_tokens = n;
                            printf("[train] %d tokens, ctx=%u, batch=%u, epochs=%d, lr=%.2g\n",
                                   n_tokens, llama_n_ctx(ctx), cp.n_batch, (int) epochs, (double) lr0);

                            ggml_opt_dataset_t dataset = common_opt_dataset_init(ctx, tokens, llama_n_ctx(ctx) / 2);
                            n_seq = (int) ggml_opt_dataset_ndata(dataset);

                            TrainFilter filt;
                            if (!filter.empty()) {
                                filt.all = false;
                                size_t start = 0;
                                while (start <= filter.size()) {
                                    size_t p = filter.find(',', start);
                                    std::string tok = filter.substr(start, p == std::string::npos ? std::string::npos : p - start);
                                    if (!tok.empty()) filt.include.push_back(tok);
                                    if (p == std::string::npos) break;
                                    start = p + 1;
                                }
                                printf("[train] filtro de tensores: %s\n", filter.c_str());
                            }

                            int trainable = 0;
                            for (const auto & entry : model->tensors_by_name) {
                                const auto * t = entry.second;
                                if (t && t->type == GGML_TYPE_F32 && train_param_filter(t, &filt)) trainable++;
                            }
                            if (filt.all && llama_model_ftype(model) != LLAMA_FTYPE_ALL_F32) {
                                err = "fine-tune completo exige um GGUF F32; use LoRA para modelos quantizados/F16";
                            } else if (trainable == 0) {
                                err = "nenhum tensor F32 corresponde ao filtro de treino";
                            }

                            if (err.empty()) {
                            const uint32_t orig_ctx_train = model->hparams.n_ctx_train;
                            struct lr_opt lr;
                            lr.lr0 = lr0 > 0 ? lr0 : 1e-5f;
                            lr.epochs = epochs > 0 ? epochs : 2;
                            lr.lr_min = lr.lr0 * 0.1f;
                            lr.decay_epochs = (float) lr.epochs;
                            lr.init();

                            llama_opt_params lop{};
                            lop.n_ctx_train = 0;
                            lop.param_filter = train_param_filter;
                            lop.param_filter_ud = &filt;
                            lop.get_opt_pars = common_opt_lr_pars;
                            lop.get_opt_pars_ud = &lr;
                            lop.optimizer_type = GGML_OPT_OPTIMIZER_TYPE_ADAMW;

                            llama_opt_init(ctx, model, lop);

                            const int64_t idata_split = (int64_t) (ggml_opt_dataset_ndata(dataset) * 0.95);
                            ggml_opt_result_t r_train = ggml_opt_result_init();
                            ggml_opt_result_t r_eval  = ggml_opt_result_init();

                            for (lr.epoch = 0; lr.epoch < lr.epochs; ++lr.epoch) {
                                printf("[train] epoch %u/%u\n", lr.epoch + 1, lr.epochs);
                                g_train_epoch = (int) lr.epoch + 1;
                                g_train_lr = lr.get_lr();
                                llama_opt_epoch(ctx, dataset, r_train, r_eval, idata_split,
                                                train_epoch_cb, train_epoch_cb);
                                double loss = 0.0, unc = 0.0;
                                ggml_opt_result_loss(r_eval, &loss, &unc);
                                final_loss = loss;
                                printf("[train] epoch %u loss=%.6f\n", lr.epoch + 1, loss);
                                ggml_opt_result_reset(r_train);
                                ggml_opt_result_reset(r_eval);
                            }

                            ggml_opt_result_free(r_train);
                            ggml_opt_result_free(r_eval);
                            ggml_opt_dataset_free(dataset);

                            printf("[train] salvando %s\n", out_path.c_str());
                            model->hparams.n_ctx_train = orig_ctx_train;
                            const std::string out_tmp = out_path + ".part";
                            llama_model_save_to_file(model, out_tmp.c_str());
                            FILE * saved = fopen(out_tmp.c_str(), "rb");
                            long saved_size = 0;
                            if (saved) { fseek(saved, 0, SEEK_END); saved_size = ftell(saved); fclose(saved); }
                            if (saved_size > 0 && std::rename(out_tmp.c_str(), out_path.c_str()) == 0) ok = true;
                            else { std::remove(out_tmp.c_str()); err = "falha ao salvar o modelo treinado"; }
                            }
                            if (!err.empty()) ggml_opt_dataset_free(dataset);
                        }
                        llama_free(ctx);
                    }
                    llama_model_free(model);
                }
                llama_backend_free();
            }
        }
    }

    g_call_env = nullptr;
    capture_stop();

    std::string result = "{\"ok\":" + std::string(ok ? "true" : "false") +
                         ",\"tokens\":" + std::to_string(n_tokens) +
                         ",\"sequences\":" + std::to_string(n_seq) +
                         ",\"loss\":" + std::to_string(final_loss) +
                         ",\"error\":\"" + json_escape(err) + "\"}";
    return new_string(env, result);
}

// -----------------------------------------------------------------------------
// Tokenizer stats (dataset preview)
// -----------------------------------------------------------------------------

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeTokenStats(
        JNIEnv * env, jobject /*thiz*/, jstring j_model, jstring j_text, jint n_ctx) {

    const std::string model_path = jstr(env, j_model);
    const std::string text = jstr(env, j_text);
    jlong out[4] = {0, 0, 0, 0};

    if (!model_path.empty() && !text.empty()) {
        llama_backend_init();
        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.vocab_only = true;
        llama_model * model = llama_model_load_from_file(model_path.c_str(), mp);
        if (model) {
            const llama_vocab * vocab = llama_model_get_vocab(model);
            std::vector<llama_token> tokens(text.size() + 8);
            int n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                   tokens.data(), (int32_t) tokens.size(), true, true);
            if (n < 0) {
                tokens.resize(-n);
                n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                   tokens.data(), (int32_t) tokens.size(), true, true);
            }
            out[0] = n > 0 ? n : 0;
            out[1] = n_ctx > 0 ? out[0] / n_ctx : 0;
            out[2] = llama_vocab_n_tokens(vocab);
            out[3] = (jlong) llama_model_n_params(model);
            llama_model_free(model);
        }
        llama_backend_free();
    }
    jlongArray arr = env->NewLongArray(4);
    env->SetLongArrayRegion(arr, 0, 4, out);
    return arr;
}


// -----------------------------------------------------------------------------
// Native LoRA training
//
// Freezes the base weights and trains low-rank A/B matrices attached through
// llama.cpp's own LoRA graph path. The optimizer only owns A/B (no base state),
// and the adapter is exported as a GGUF loadable by llama.cpp / Ollama.
// -----------------------------------------------------------------------------

struct LoraTargets {
    std::vector<std::pair<std::string, ggml_tensor *>> items;
    int rank = 16;
    float alpha = 16.f;
    bool include_ffn = false;
};

static bool lora_param_filter(const ggml_tensor * t, void *) {
    if (!t || !t->name[0]) return false;
    return strstr(t->name, ".lora_a") != nullptr || strstr(t->name, ".lora_b") != nullptr;
}

static std::shared_ptr<llama_adapter_lora> build_lora(llama_model * model, LoraTargets & t, std::string & err) {
    auto adapter = std::make_shared<llama_adapter_lora>(model);
    adapter->alpha = t.alpha;

    std::set<ggml_tensor *> seen;
    auto add = [&](ggml_tensor * w) {
        if (!w || ggml_n_dims(w) != 2 || !w->name[0]) return;
        if (!seen.insert(w).second) return;
        t.items.emplace_back(std::string(w->name), w);
    };
    for (auto & layer : model->layers) {
        // attention projections (arch naming varies, fields are canonical)
        add(layer.wq);  add(layer.wk);  add(layer.wv);  add(layer.wo);
        add(layer.wqkv);
        add(layer.wq_a); add(layer.wq_b);
        add(layer.wkv_a_mqa); add(layer.wkv_b); add(layer.wkv);
        add(layer.wk_b); add(layer.wv_b); add(layer.wqkv_b);
        add(layer.wo_a); add(layer.wo_b);
        if (t.include_ffn) {
            add(layer.ffn_gate);
            add(layer.ffn_down);
            add(layer.ffn_up);
        }
    }
    if (t.items.empty()) {
        err = "nenhum tensor compatível encontrado para LoRA";
        return nullptr;
    }

    ggml_init_params ip = {
        /* .mem_size   = */ (t.items.size() * 2 + 16) * ggml_tensor_overhead(),
        /* .mem_buffer = */ nullptr,
        /* .no_alloc   = */ true,
    };
    ggml_context * gctx = ggml_init(ip);
    if (!gctx) { err = "falha ao criar contexto LoRA"; return nullptr; }

    for (auto & [name, w] : t.items) {
        const int r = std::min<int>(t.rank, std::min<int>((int) w->ne[0], (int) w->ne[1]));
        ggml_tensor * a = ggml_new_tensor_2d(gctx, GGML_TYPE_F32, w->ne[0], r);
        ggml_tensor * b = ggml_new_tensor_2d(gctx, GGML_TYPE_F32, r, w->ne[1]);
        ggml_set_name(a, (name + ".lora_a").c_str());
        ggml_set_name(b, (name + ".lora_b").c_str());
        adapter->ab_map[name] = llama_adapter_lora_weight(a, b);
    }

    adapter->ctxs.emplace_back(gctx);
    ggml_backend_buffer_t buf = ggml_backend_alloc_ctx_tensors_from_buft(gctx, ggml_backend_cpu_buffer_type());
    if (!buf) { err = "falha ao alocar tensores LoRA"; return nullptr; }
    adapter->bufs.emplace_back(buf);

    // A ~ N(0, 0.02), B = 0  =>  delta starts at zero
    std::mt19937 rng(1337);
    std::normal_distribution<float> nd(0.f, 0.02f);
    std::vector<float> tmp;
    for (auto & [name, w] : t.items) {
        auto & lw = adapter->ab_map[name];
        tmp.resize((size_t) ggml_nelements(lw.a));
        for (auto & v : tmp) v = nd(rng);
        ggml_backend_tensor_set(lw.a, tmp.data(), 0, ggml_nbytes(lw.a));
        tmp.assign((size_t) ggml_nelements(lw.b), 0.f);
        ggml_backend_tensor_set(lw.b, tmp.data(), 0, ggml_nbytes(lw.b));
        ggml_set_param(lw.a);
        ggml_set_param(lw.b);
    }
    return adapter;
}

static bool save_lora(const std::shared_ptr<llama_adapter_lora> & adapter, llama_model * model,
                      float alpha, const std::string & path, std::string & err) {
    gguf_context * g = gguf_init_empty();
    if (!g) { err = "falha ao criar GGUF do adaptador"; return false; }

    gguf_set_val_str(g, "general.type", "adapter");
    gguf_set_val_str(g, "general.name", "Girassol LoRA");
    gguf_set_val_str(g, "general.architecture", llm_arch_name(model->arch));
    gguf_set_val_str(g, "adapter.type", "lora");
    gguf_set_val_f32(g, "adapter.lora.alpha", alpha);
    gguf_set_val_u32(g, "general.file_type", 0);

    for (const auto & kv : adapter->ab_map) {
        gguf_add_tensor(g, kv.second.a);
        gguf_add_tensor(g, kv.second.b);
    }
    const bool ok = gguf_write_to_file(g, path.c_str(), false);
    gguf_free(g);
    if (!ok) { err = "falha ao escrever o adaptador GGUF"; std::remove(path.c_str()); }
    return ok;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeLoraTrain(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model, jstring j_out, jstring j_dataset,
        jint epochs, jfloat lr0, jint n_ctx, jint nthreads,
        jint rank, jfloat alpha, jstring j_targets, jobject cb) {

    const std::string model_path = jstr(env, j_model);
    const std::string out_path   = jstr(env, j_out);
    const std::string data_path  = jstr(env, j_dataset);
    const std::string targets    = jstr(env, j_targets);

    capture_start(env, cb);
    g_call_env = env;

    std::string err;
    bool ok = false;
    double final_loss = 0.0;
    int n_tokens = 0, n_seq = 0, n_tensors = 0;

    if (model_path.empty() || out_path.empty() || data_path.empty()) {
        err = "parâmetros vazios";
    } else {
        std::ifstream f(data_path, std::ios::binary);
        if (!f) {
            err = "não foi possível abrir o dataset";
        } else {
            std::string text((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
            f.close();
            if (text.size() < 64) {
                err = "dataset muito pequeno";
            } else {
                llama_backend_init();

                llama_model_params mp = llama_model_default_params();
                mp.n_gpu_layers = 0;
                mp.load_mode = LLAMA_LOAD_MODE_MMAP;

                llama_model * model = llama_model_load_from_file(model_path.c_str(), mp);
                if (!model) {
                    err = "falha ao carregar o modelo";
                } else {
                    const llama_vocab * vocab = llama_model_get_vocab(model);
                    const int ctx_len = n_ctx > 0 ? n_ctx : 512;

                    llama_context_params cp = llama_context_default_params();
                    cp.n_ctx = ctx_len;
                    cp.n_batch = ctx_len;
                    cp.n_ubatch = ctx_len;   // gradients through attention need ubatch == ctx
                    cp.n_threads = nthreads > 0 ? nthreads : (int) std::thread::hardware_concurrency();
                    cp.n_threads_batch = cp.n_threads;
                    cp.type_k = GGML_TYPE_F32;
                    cp.type_v = GGML_TYPE_F32;
                    cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
                    cp.no_perf = true;

                    llama_context * ctx = llama_init_from_model(model, cp);
                    if (!ctx) {
                        err = "falha ao criar contexto";
                    } else {
                        std::vector<llama_token> tokens(text.size() + 8);
                        int n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                               tokens.data(), (int32_t) tokens.size(), true, true);
                        if (n < 0) {
                            tokens.resize(-n);
                            n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                               tokens.data(), (int32_t) tokens.size(), true, true);
                        }
                        const int64_t stride = ctx_len / 2;
                        if (n < ctx_len + 1 + 2 * stride) {
                            err = "dataset curto demais para o contexto escolhido (" + std::to_string(n) +
                                  " tokens; mínimo " + std::to_string(ctx_len + 1 + 2 * stride) + ")";
                        } else {
                            tokens.resize(n);
                            n_tokens = n;

                            LoraTargets lt;
                            lt.rank = rank > 0 ? rank : 16;
                            lt.alpha = alpha > 0 ? alpha : (float) lt.rank;
                            lt.include_ffn = targets.find("ffn") != std::string::npos || targets == "all";

                            auto adapter = build_lora(model, lt, err);
                            if (adapter) {
                                n_tensors = (int) lt.items.size();
                                printf("[lora] %d tensores alvo, rank=%d, alpha=%.1f, ctx=%d\n",
                                       n_tensors, lt.rank, (double) lt.alpha, ctx_len);

                                llama_adapter_lora * raw = adapter.get();
                                float scale = 1.0f;
                                llama_set_adapters_lora(ctx, &raw, 1, &scale);

                                ggml_opt_dataset_t dataset = common_opt_dataset_init(ctx, tokens, ctx_len / 2);
                                n_seq = (int) ggml_opt_dataset_ndata(dataset);

                                struct lr_opt lr;
                                lr.lr0 = lr0 > 0 ? lr0 : 1e-4f;
                                lr.epochs = epochs > 0 ? epochs : 3;
                                lr.lr_min = lr.lr0 * 0.1f;
                                lr.decay_epochs = (float) lr.epochs;
                                lr.init();

                                llama_opt_params lop{};
                                lop.n_ctx_train = ctx_len;
                                lop.param_filter = lora_param_filter;
                                lop.param_filter_ud = nullptr;
                                lop.get_opt_pars = common_opt_lr_pars;
                                lop.get_opt_pars_ud = &lr;
                                lop.optimizer_type = GGML_OPT_OPTIMIZER_TYPE_ADAMW;

                                llama_opt_init(ctx, model, lop);

                                const int64_t split = (int64_t) (ggml_opt_dataset_ndata(dataset) * 0.95);
                                ggml_opt_result_t r_train = ggml_opt_result_init();
                                ggml_opt_result_t r_eval  = ggml_opt_result_init();

                                for (lr.epoch = 0; lr.epoch < lr.epochs; ++lr.epoch) {
                                    printf("[lora] epoch %u/%u\n", lr.epoch + 1, lr.epochs);
                                    g_train_epoch = (int) lr.epoch + 1;
                                    g_train_lr = lr.get_lr();
                                    llama_opt_epoch(ctx, dataset, r_train, r_eval, split,
                                                    train_epoch_cb, train_epoch_cb);
                                    double loss = 0.0, unc = 0.0;
                                    ggml_opt_result_loss(r_eval, &loss, &unc);
                                    final_loss = loss;
                                    printf("[lora] epoch %u loss=%.6f\n", lr.epoch + 1, loss);
                                    ggml_opt_result_reset(r_train);
                                    ggml_opt_result_reset(r_eval);
                                }

                                ggml_opt_result_free(r_train);
                                ggml_opt_result_free(r_eval);
                                ggml_opt_dataset_free(dataset);

                                printf("[lora] salvando adaptador %s\n", out_path.c_str());
                                const std::string lora_tmp = out_path + ".part";
                                if (save_lora(adapter, model, lt.alpha, lora_tmp, err)) {
                                    if (std::rename(lora_tmp.c_str(), out_path.c_str()) == 0) ok = true;
                                    else { std::remove(lora_tmp.c_str()); err = "falha ao finalizar o adaptador LoRA"; }
                                }
                            }
                        }
                        llama_free(ctx);
                    }
                    llama_model_free(model);
                }
                llama_backend_free();
            }
        }
    }

    g_call_env = nullptr;
    capture_stop();

    std::string result = "{\"ok\":" + std::string(ok ? "true" : "false") +
                         ",\"tokens\":" + std::to_string(n_tokens) +
                         ",\"sequences\":" + std::to_string(n_seq) +
                         ",\"tensors\":" + std::to_string(n_tensors) +
                         ",\"loss\":" + std::to_string(final_loss) +
                         ",\"error\":\"" + json_escape(err) + "\"}";
    return new_string(env, result);
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeCountTokens(
        JNIEnv * env, jobject /*thiz*/, jstring j_model, jstring j_text) {
    const std::string model_path = jstr(env, j_model);
    const std::string text = jstr(env, j_text);
    if (model_path.empty() || text.empty()) return 0;

    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.vocab_only = true;
    llama_model * model = llama_model_load_from_file(model_path.c_str(), mp);
    int n = 0;
    if (model) {
        const llama_vocab * vocab = llama_model_get_vocab(model);
        std::vector<llama_token> tokens(text.size() + 8);
        n = llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                           tokens.data(), (int32_t) tokens.size(), true, true);
        if (n < 0) n = -n;
        llama_model_free(model);
    }
    llama_backend_free();
    return n;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeVersion(JNIEnv * env, jobject /*thiz*/) {
    // deliberately does not initialize backends: keeps app startup free of native work
    std::string v = llama_version();
    std::string b = "llama.cpp " + v + " | " + std::to_string(std::thread::hardware_concurrency()) + " threads";
    return new_string(env, b);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}
