// LabForge native engine — GGUF quantization (imatrix-aware) + on-device fine-tuning
// Built on llama.cpp (MIT). JNI surface is intentionally small and stable.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <algorithm>
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
#include <mutex>

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
static jmethodID g_cb_token = nullptr;

static jobject   g_cb_obj = nullptr;
static std::mutex g_native_mutex;
static std::atomic<bool> g_chat_abort{false};

// stdout/stderr capture so native logs stream into the Compose console
static int  g_saved_out = -1;
static int  g_saved_err = -1;
static int  g_pipe_rd = -1;
static int  g_pipe_wr = -1;
static std::atomic<bool> g_capture_run{false};
static std::thread g_reader;

static std::string jstr(JNIEnv * env, jstring s) {
    if (!s) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
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
    if (!g_cb_obj || !g_cb_log || line.empty() || !g_vm) return;
    // JNIEnv is thread-local: the pipe reader must get its own environment.
    JNIEnv * env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jstring js = env->NewStringUTF(line.c_str());
    if (js) {
        env->CallVoidMethod(g_cb_obj, g_cb_log, js);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(js);
    }
    if (attached) g_vm->DetachCurrentThread();
}

static void emit_token(const std::string & piece) {
    if (!g_cb_obj || !g_cb_token || piece.empty() || !g_vm) return;
    JNIEnv * env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jstring js = env->NewStringUTF(piece.c_str());
    if (js) {
        env->CallVoidMethod(g_cb_obj, g_cb_token, js);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(js);
    }
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
    if (cb) {
        g_cb_obj = env->NewGlobalRef(cb);
        jclass local = env->GetObjectClass(cb);
        g_cb_class = (jclass) env->NewGlobalRef(local);
        env->DeleteLocalRef(local);
        g_cb_log = env->GetMethodID(g_cb_class, "onLog", "(Ljava/lang/String;)V");
        if (!g_cb_log) env->ExceptionClear();
        g_cb_epoch = env->GetMethodID(g_cb_class, "onEpoch", "(IIIDD)V");
        if (!g_cb_epoch) env->ExceptionClear();
        g_cb_token = env->GetMethodID(g_cb_class, "onToken", "(Ljava/lang/String;)V");
        if (!g_cb_token) env->ExceptionClear();
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
    if (g_vm && g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) == JNI_OK) {
        if (g_cb_obj) env->DeleteGlobalRef(g_cb_obj);
        if (g_cb_class) env->DeleteGlobalRef(g_cb_class);
    }
    g_cb_obj = nullptr;
    g_cb_class = nullptr;
    g_cb_log = nullptr;
    g_cb_epoch = nullptr;
    g_cb_token = nullptr;
    g_capture_run = false;
}

// Keep the corpus on disk and sample a bounded window for each autonomous cycle.
static constexpr size_t MAX_TRAIN_WINDOW_BYTES = 512 * 1024;
static bool read_training_window(std::ifstream & file, std::string & text, uint64_t & source_bytes) {
    file.seekg(0, std::ios::end);
    const std::streamoff size = file.tellg();
    if (size < 0) return false;
    source_bytes = (uint64_t) size;
    if (source_bytes <= MAX_TRAIN_WINDOW_BYTES) {
        file.clear();
        file.seekg(0, std::ios::beg);
    } else {
        const uint64_t max_start = source_bytes - MAX_TRAIN_WINDOW_BYTES;
        const uint64_t seed = (uint64_t) std::chrono::steady_clock::now().time_since_epoch().count();
        const uint64_t offset = max_start ? seed % (max_start + 1) : 0;
        file.clear();
        file.seekg((std::streamoff) offset, std::ios::beg);
        if (offset > 0) { std::string partial; std::getline(file, partial); }
    }
    std::vector<char> buffer(MAX_TRAIN_WINDOW_BYTES);
    file.read(buffer.data(), (std::streamsize) buffer.size());
    const std::streamsize count = file.gcount();
    if (count <= 0) return false;
    text.assign(buffer.data(), (size_t) count);
    if (source_bytes > MAX_TRAIN_WINDOW_BYTES) {
        const size_t boundary = text.find_last_of('\n');
        if (boundary != std::string::npos && boundary > 64) text.resize(boundary);
    }
    return !text.empty();
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
    std::lock_guard<std::mutex> native_guard(g_native_mutex);

    const std::string in   = jstr(env, j_in);
    const std::string out  = jstr(env, j_out);
    const std::string fts  = jstr(env, j_ftype);
    const std::string imat = jstr(env, j_imatrix);

    capture_start(env, cb);

    std::string err;
    std::string result;
    uint32_t rc = 1;
    uint64_t out_size = 0;

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
                return env->NewStringUTF(result.c_str());
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
        rc = llama_model_quantize(in.c_str(), out.c_str(), &params);

        if (rc == 0) {
            FILE * f = fopen(out.c_str(), "rb");
            if (f) { fseek(f, 0, SEEK_END); out_size = (uint64_t) ftell(f); fclose(f); }
            printf("[quantize] concluído: %.2f MiB\n", out_size / 1048576.0);
        } else {
            printf("[quantize] falhou (código %u)\n", rc);
        }
        llama_backend_free();
    }

    capture_stop();

    result = "{\"ok\":" + std::string(rc == 0 ? "true" : "false") +
             ",\"bytes\":" + std::to_string(out_size) +
             ",\"error\":\"" + json_escape(err) + "\"}";
    return env->NewStringUTF(result.c_str());
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
static std::atomic<bool> g_train_abort{false};

static void train_epoch_cb(bool train, ggml_opt_context_t, ggml_opt_dataset_t,
                           ggml_opt_result_t result, int64_t ibatch,
                           int64_t ibatch_max, int64_t) {
    if (!g_cb_obj || !g_cb_epoch || !g_vm) return;
    JNIEnv * callback_env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **) &callback_env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&callback_env, nullptr) != JNI_OK) return;
        attached = true;
    }
    double loss = 0.0, unc = 0.0;
    if (result) ggml_opt_result_loss(result, &loss, &unc);
    callback_env->CallVoidMethod(g_cb_obj, g_cb_epoch, (jint) g_train_epoch.load(), (jint) ibatch,
                                 (jint) ibatch_max, (jdouble) loss, (jdouble) g_train_lr.load());
    if (callback_env->ExceptionCheck()) callback_env->ExceptionClear();
    if (attached) g_vm->DetachCurrentThread();
    (void) train;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeFinetune(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model, jstring j_out, jstring j_dataset,
        jint epochs, jfloat lr0, jint n_ctx, jint n_batch, jint n_ubatch,
        jint nthreads, jstring j_filter, jobject cb) {
    std::lock_guard<std::mutex> native_guard(g_native_mutex);

    const std::string model_path = jstr(env, j_model);
    const std::string out_path   = jstr(env, j_out);
    const std::string data_path  = jstr(env, j_dataset);
    const std::string filter     = jstr(env, j_filter);

    capture_start(env, cb);
    g_train_abort = false;

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
            std::string text;
            uint64_t source_bytes = 0;
            const bool has_window = read_training_window(f, text, source_bytes);
            f.close();
            if (!has_window || text.size() < 64) {
                err = has_window ? "dataset muito pequeno" : "não foi possível ler uma janela do dataset";
            } else {
                printf("[train] corpus=%llu bytes, janela=%zu bytes\\n", (unsigned long long) source_bytes, text.size());
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
                    cp.n_batch = n_batch > 0 ? n_batch : 512;
                    cp.n_ubatch = n_ubatch > 0 ? n_ubatch : cp.n_batch;
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
                        if (n <= llama_n_ctx(ctx) + 1) {
                            err = "dataset curto demais para o contexto escolhido (" + std::to_string(n) + " tokens)";
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

                            for (lr.epoch = 0; lr.epoch < lr.epochs && !g_train_abort.load(); ++lr.epoch) {
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

                            if (g_train_abort.load()) {
                                err = "treino interrompido por pressão de memória; nenhum modelo final foi exportado";
                            } else {
                                printf("[train] salvando %s\n", out_path.c_str());
                                llama_model_save_to_file(model, out_path.c_str());
                                std::ifstream saved(out_path, std::ios::binary | std::ios::ate);
                                ok = saved.good() && saved.tellg() > 0;
                                if (!ok) err = "falha ao gravar o modelo treinado";
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

    capture_stop();

    std::string result = "{\"ok\":" + std::string(ok ? "true" : "false") +
                         ",\"tokens\":" + std::to_string(n_tokens) +
                         ",\"sequences\":" + std::to_string(n_seq) +
                         ",\"loss\":" + std::to_string(final_loss) +
                         ",\"error\":\"" + json_escape(err) + "\"}";
    return env->NewStringUTF(result.c_str());
}

// -----------------------------------------------------------------------------
// Tokenizer stats (dataset preview)
// -----------------------------------------------------------------------------

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeTokenStats(
        JNIEnv * env, jobject /*thiz*/, jstring j_model, jstring j_text, jint n_ctx) {
    std::lock_guard<std::mutex> native_guard(g_native_mutex);

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
        std::fill(tmp.begin(), tmp.end(), 0.f);
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
    if (!ok) err = "falha ao escrever o adaptador GGUF";
    return ok;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeLoraTrain(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model, jstring j_out, jstring j_dataset,
        jint epochs, jfloat lr0, jint n_ctx, jint nthreads,
        jint rank, jfloat alpha, jstring j_targets, jobject cb) {
    std::lock_guard<std::mutex> native_guard(g_native_mutex);

    const std::string model_path = jstr(env, j_model);
    const std::string out_path   = jstr(env, j_out);
    const std::string data_path  = jstr(env, j_dataset);
    const std::string targets    = jstr(env, j_targets);

    capture_start(env, cb);
    g_train_abort = false;

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
            std::string text;
            uint64_t source_bytes = 0;
            const bool has_window = read_training_window(f, text, source_bytes);
            f.close();
            if (!has_window || text.size() < 64) {
                err = has_window ? "dataset muito pequeno" : "não foi possível ler uma janela do dataset";
            } else {
                printf("[train] corpus=%llu bytes, janela=%zu bytes\\n", (unsigned long long) source_bytes, text.size());
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
                        if (n <= ctx_len + 1) {
                            err = "dataset curto demais para o contexto escolhido";
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

                                const std::string checkpoint_path = out_path + ".checkpoint.gguf";
                                for (lr.epoch = 0; lr.epoch < lr.epochs && !g_train_abort.load(); ++lr.epoch) {
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
                                    if (!save_lora(adapter, model, lt.alpha, checkpoint_path, err)) break;
                                    printf("[lora] checkpoint da época %u salvo\\n", lr.epoch + 1);
                                }

                                ggml_opt_result_free(r_train);
                                ggml_opt_result_free(r_eval);
                                ggml_opt_dataset_free(dataset);

                                if (g_train_abort.load()) {
                                    std::ifstream checkpoint(checkpoint_path, std::ios::binary | std::ios::ate);
                                    const bool checkpoint_valid = checkpoint.good() && checkpoint.tellg() > 0;
                                    err = checkpoint_valid
                                        ? "treino interrompido; último checkpoint preservado em " + checkpoint_path
                                        : "treino interrompido antes da primeira época; nenhum checkpoint válido foi gerado";
                                } else if (err.empty()) {
                                    printf("[lora] exportando adaptador final %s\n", out_path.c_str());
                                    ok = save_lora(adapter, model, lt.alpha, out_path, err);
                                    if (ok) std::remove(checkpoint_path.c_str());
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

    capture_stop();

    std::string result = "{\"ok\":" + std::string(ok ? "true" : "false") +
                         ",\"tokens\":" + std::to_string(n_tokens) +
                         ",\"sequences\":" + std::to_string(n_seq) +
                         ",\"tensors\":" + std::to_string(n_tensors) +
                         ",\"loss\":" + std::to_string(final_loss) +
                         ",\"error\":\"" + json_escape(err) + "\"}";
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeCountTokens(
        JNIEnv * env, jobject /*thiz*/, jstring j_model, jstring j_text) {
    std::lock_guard<std::mutex> native_guard(g_native_mutex);
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

extern "C" JNIEXPORT void JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeRequestTrainingStop(JNIEnv *, jobject) {
    g_train_abort = true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeVersion(JNIEnv * env, jobject /*thiz*/) {
    // deliberately does not initialize backends: keeps app startup free of native work
    std::string v = llama_version();
    std::string b = "llama.cpp " + v + " | " + std::to_string(std::thread::hardware_concurrency()) + " threads";
    return env->NewStringUTF(b.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeGenerateLocal(
        JNIEnv * env, jobject /*thiz*/,
        jstring j_model, jstring j_system, jstring j_prompt,
        jint n_ctx, jint nthreads, jint max_tokens, jfloat temperature, jobject cb) {
    std::lock_guard<std::mutex> native_guard(g_native_mutex);
    const std::string model_path = jstr(env, j_model);
    const std::string system_text = jstr(env, j_system);
    const std::string user_text = jstr(env, j_prompt);

    capture_start(env, cb);
    g_chat_abort = false;

    std::string err;
    int prompt_tokens = 0;
    int completion_tokens = 0;
    const auto started = std::chrono::steady_clock::now();

    if (model_path.empty() || system_text.empty() || user_text.empty()) {
        err = "modelo ou prompt vazio";
    } else {
        printf("[infer] modelo=%s, ctx solicitado=%d, threads=%d, max_tokens=%d\\n",
               model_path.c_str(), (int) n_ctx, (int) nthreads, (int) max_tokens);
        llama_backend_init();

        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        mp.use_extra_bufts = false;
        llama_model * model = llama_model_load_from_file(model_path.c_str(), mp);
        if (!model) {
            err = "falha ao abrir GGUF para inferência; confira arquivo e arquitetura suportada";
        } else {
            const llama_vocab * vocab = llama_model_get_vocab(model);
            int ctx_len = std::max(256, (int) n_ctx);
            const int train_ctx = llama_model_n_ctx_train(model);
            if (train_ctx > 0) ctx_len = std::min(ctx_len, train_ctx);

            llama_context_params cp = llama_context_default_params();
            cp.n_ctx = (uint32_t) ctx_len;
            cp.n_batch = (uint32_t) ctx_len;
            cp.n_ubatch = (uint32_t) std::min(ctx_len, 256);
            cp.n_threads = std::max(1, (int) nthreads);
            cp.n_threads_batch = cp.n_threads;
            cp.type_k = GGML_TYPE_F16;
            cp.type_v = GGML_TYPE_F16;
            cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;
            cp.no_perf = false;

            llama_context * ctx = llama_init_from_model(model, cp);
            if (!ctx) {
                err = "não foi possível alocar o contexto de inferência";
            } else {
                const llama_chat_message chat[] = {
                    { "system", system_text.c_str() },
                    { "user", user_text.c_str() },
                };
                const char * tmpl = llama_model_chat_template(model, nullptr);
                std::string formatted;
                if (tmpl) {
                    int32_t needed = llama_chat_apply_template(tmpl, chat, 2, true, nullptr, 0);
                    if (needed > 0) {
                        formatted.resize((size_t) needed + 1);
                        int32_t written = llama_chat_apply_template(
                            tmpl, chat, 2, true, formatted.data(), (int32_t) formatted.size());
                        if (written >= 0) formatted.resize((size_t) written);
                        else formatted.clear();
                    }
                }
                if (formatted.empty()) {
                    // Generic ChatML fallback for GGUF models that omit a supported template.
                    formatted = "<|im_start|>system\n" + system_text +
                        "\n<|im_start|>user\n" + user_text +
                        "\n<|im_start|>assistant\n";
                }

                std::vector<llama_token> tokens(formatted.size() + 16);
                int32_t n = llama_tokenize(vocab, formatted.c_str(), (int32_t) formatted.size(),
                                           tokens.data(), (int32_t) tokens.size(), false, true);
                if (n < 0) {
                    tokens.resize((size_t) -n);
                    n = llama_tokenize(vocab, formatted.c_str(), (int32_t) formatted.size(),
                                       tokens.data(), (int32_t) tokens.size(), false, true);
                }
                const int reserved = std::max(16, (int) max_tokens);
                if (n <= 0) {
                    err = "tokenização do prompt falhou";
                } else if (n + reserved >= ctx_len) {
                    err = "o histórico excede a janela de contexto; reduza o histórico ou use um contexto maior";
                } else {
                    tokens.resize((size_t) n);
                    prompt_tokens = n;
                    printf("[infer] prompt=%d tokens, ctx=%d, template=%s\\n",
                           prompt_tokens, ctx_len, tmpl ? "GGUF" : "ChatML fallback");
                    llama_batch batch = llama_batch_get_one(tokens.data(), n);
                    int decode = llama_decode(ctx, batch);
                    if (decode != 0) {
                        err = "falha ao processar o prompt no motor nativo (decode=" + std::to_string(decode) + ")";
                    } else {
                        llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
                        if (!sampler) {
                            err = "não foi possível criar o sampler";
                        } else {
                            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
                            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.92f, 1));
                            if (temperature <= 0.001f) {
                                llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
                            } else {
                                llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
                                llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
                            }

                            for (int i = 0; i < max_tokens && !g_chat_abort.load(); ++i) {
                                const llama_token token = llama_sampler_sample(sampler, ctx, -1);
                                if (llama_vocab_is_eog(vocab, token)) break;

                                char piece_buf[512];
                                int32_t piece_len = llama_token_to_piece(vocab, token, piece_buf,
                                                                         (int32_t) sizeof(piece_buf), 0, false);
                                std::string piece;
                                if (piece_len < 0) {
                                    std::vector<char> larger((size_t) -piece_len + 1);
                                    piece_len = llama_token_to_piece(vocab, token, larger.data(),
                                        (int32_t) larger.size(), 0, false);
                                    if (piece_len > 0) piece.assign(larger.data(), (size_t) piece_len);
                                } else if (piece_len > 0) {
                                    piece.assign(piece_buf, (size_t) piece_len);
                                }
                                if (!piece.empty()) {
                                    emit_token(piece);
                                    completion_tokens++;
                                }
                                if (i + 1 < max_tokens && !g_chat_abort.load()) {
                                    llama_batch next = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
                                    const int next_decode = llama_decode(ctx, next);
                                    if (next_decode != 0) {
                                        err = "falha ao avançar geração nativa (decode=" + std::to_string(next_decode) + ")";
                                        break;
                                    }
                                }
                            }
                            if (g_chat_abort.load()) err = "geração interrompida pelo utilizador";
                            llama_sampler_free(sampler);
                        }
                    }
                }
                llama_free(ctx);
            }
            llama_model_free(model);
        }
        llama_backend_free();
    }

    fflush(stdout);
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - started).count();
    capture_stop();
    const bool ok = err.empty() && completion_tokens > 0;
    if (!ok && err.empty()) err = "o modelo não gerou tokens de resposta";
    const std::string json = "{\"ok\":" + std::string(ok ? "true" : "false") +
        ",\"prompt_tokens\":" + std::to_string(prompt_tokens) +
        ",\"completion_tokens\":" + std::to_string(completion_tokens) +
        ",\"elapsed_ms\":" + std::to_string(elapsed) +
        ",\"error\":\"" + json_escape(err) + "\"}";
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_labforge_app_core_native_LlamaBridge_nativeRequestChatStop(JNIEnv *, jobject) {
    g_chat_abort = true;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}
