#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <fstream>
#include <cmath>

#define LOG_TAG "GirassolLLM"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace girassol {

// Estrutura simplificada de modelo GGUF
// (Implementação real exigiria llama.cpp completo - aqui é uma versão mínima funcional)
struct GGUFModel {
    std::string path;
    bool loaded = false;
    size_t fileSize = 0;
    int nTokens = 0;

    // Dados mock para demonstração
    std::vector<float> embeddings;

    bool load(const std::string& modelPath) {
        std::ifstream file(modelPath, std::ios::binary | std::ios::ate);
        if (!file.is_open()) {
            LOGE("Failed to open model file: %s", modelPath.c_str());
            return false;
        }

        fileSize = file.tellg();
        file.seekg(0, std::ios::beg);

        // Verificar magic bytes GGUF ("GGUF")
        char magic[4];
        file.read(magic, 4);
        if (std::string(magic, 4) != "GGUF") {
            LOGW("File is not GGUF format, but continuing anyway");
        }

        loaded = true;
        path = modelPath;

        // Inicializar embeddings com valores aleatórios determinísticos
        embeddings.resize(384);
        for (size_t i = 0; i < 384; ++i) {
            embeddings[i] = std::sin(i * 0.1f) * 0.5f;
        }

        LOGI("Model loaded: %s (%.2f MB)", modelPath.c_str(), fileSize / (1024.0 * 1024.0));
        return true;
    }

    void unload() {
        loaded = false;
        path.clear();
        embeddings.clear();
        LOGI("Model unloaded");
    }

    // Geração mock (em produção, usar llama.cpp real)
    std::string generate(const std::string& prompt, int maxTokens) {
        if (!loaded) {
            return "";
        }

        // Simular geração baseada no prompt
        std::string response = "Resposta gerada para: " + prompt.substr(0, std::min<size_t>(50, prompt.length()));

        // Adicionar tokens "gerados"
        for (int i = 0; i < std::min(maxTokens, 10); ++i) {
            response += " token" + std::to_string(i);
        }

        return response;
    }

    std::vector<float> embed(const std::string& text) {
        if (!loaded) {
            return std::vector<float>(384, 0.0f);
        }

        // Simular embedding determinístico baseado no texto
        std::vector<float> result = embeddings;
        for (size_t i = 0; i < result.size() && i < text.length(); ++i) {
            result[i] += static_cast<float>(text[i]) / 255.0f * 0.1f;
        }

        // Normalizar
        float norm = 0.0f;
        for (float v : result) norm += v * v;
        norm = std::sqrt(norm);
        if (norm > 0) {
            for (auto& v : result) v /= norm;
        }

        return result;
    }
};

// Singleton do modelo
class LLMEngine {
public:
    static LLMEngine& getInstance() {
        static LLMEngine instance;
        return instance;
    }

    bool loadModel(const std::string& path) {
        std::lock_guard<std::mutex> lock(mutex);
        if (model.loaded) {
            model.unload();
        }
        return model.load(path);
    }

    void unloadModel() {
        std::lock_guard<std::mutex> lock(mutex);
        model.unload();
    }

    std::string generate(const std::string& prompt, int maxTokens) {
        std::lock_guard<std::mutex> lock(mutex);
        return model.generate(prompt, maxTokens);
    }

    std::vector<float> embed(const std::string& text) {
        std::lock_guard<std::mutex> lock(mutex);
        return model.embed(text);
    }

    bool isLoaded() const {
        return model.loaded;
    }

private:
    LLMEngine() = default;
    GGUFModel model;
    mutable std::mutex mutex;
};

// Callbacks JNI
struct StreamCallback {
    jobject callbackObj;
    jmethodID onDeltaMethod;
    jmethodID onCompleteMethod;
    jmethodID onErrorMethod;
    JNIEnv* env;

    void onDelta(const std::string& text) {
        if (env && callbackObj && onDeltaMethod) {
            jstring jtext = env->NewStringUTF(text.c_str());
            env->CallVoidMethod(callbackObj, onDeltaMethod, jtext);
            env->DeleteLocalRef(jtext);
        }
    }

    void onComplete() {
        if (env && callbackObj && onCompleteMethod) {
            env->CallVoidMethod(callbackObj, onCompleteMethod);
        }
    }

    void onError(const std::string& error) {
        if (env && callbackObj && onErrorMethod) {
            jstring jerror = env->NewStringUTF(error.c_str());
            env->CallVoidMethod(callbackObj, onErrorMethod, jerror);
            env->DeleteLocalRef(jerror);
        }
    }
};

} // namespace girassol

extern "C" {

// Inicializar modelo
JNIEXPORT jboolean JNICALL
Java_com_enhikyy_girassol_ai_NativeLlmEngine_nativeLoadModel(
    JNIEnv* env, jobject /* this */, jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    bool ok = girassol::LLMEngine::getInstance().loadModel(path);

    env->ReleaseStringUTFChars(modelPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Descarregar modelo
JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_ai_NativeLlmEngine_nativeUnloadModel(
    JNIEnv* env, jobject /* this */) {

    girassol::LLMEngine::getInstance().unloadModel();
}

// Verificar se modelo está carregado
JNIEXPORT jboolean JNICALL
Java_com_enhikyy_girassol_ai_NativeLlmEngine_nativeIsLoaded(
    JNIEnv* env, jobject /* this */) {

    return girassol::LLMEngine::getInstance().isLoaded() ? JNI_TRUE : JNI_FALSE;
}

// Gerar texto
JNIEXPORT jstring JNICALL
Java_com_enhikyy_girassol_ai_NativeLlmEngine_nativeGenerate(
    JNIEnv* env, jobject /* this */, jstring prompt, jint maxTokens) {

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) return nullptr;

    std::string result = girassol::LLMEngine::getInstance().generate(promptStr, maxTokens);

    env->ReleaseStringUTFChars(prompt, promptStr);
    return env->NewStringUTF(result.c_str());
}

// Gerar com streaming
JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_ai_NativeLlmEngine_nativeGenerateStream(
    JNIEnv* env, jobject /* this */, jstring prompt, jint maxTokens, jobject callback) {

    // Obter métodos do callback
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onDelta = env->GetMethodID(callbackClass, "onDelta", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(callbackClass, "onComplete", "()V");
    jmethodID onError = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");

    if (!onDelta || !onComplete || !onError) {
        LOGE("Callback methods not found");
        return;
    }

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) {
        jstring error = env->NewStringUTF("Invalid prompt");
        env->CallVoidMethod(callback, onError, error);
        env->DeleteLocalRef(error);
        return;
    }

    // Configurar callback
    girassol::StreamCallback streamCallback;
    streamCallback.env = env;
    streamCallback.callbackObj = callback;
    streamCallback.onDeltaMethod = onDelta;
    streamCallback.onCompleteMethod = onComplete;
    streamCallback.onErrorMethod = onError;

    // Gerar e fazer streaming
    std::string fullResult = girassol::LLMEngine::getInstance().generate(promptStr, maxTokens);

    // Simular streaming por partes
    size_t pos = 0;
    size_t chunkSize = 10;
    while (pos < fullResult.length()) {
        size_t end = std::min(pos + chunkSize, fullResult.length());
        std::string chunk = fullResult.substr(pos, end - pos);
        streamCallback.onDelta(chunk);
        pos = end;
    }

    streamCallback.onComplete();

    env->ReleaseStringUTFChars(prompt, promptStr);
}

// Gerar embedding
JNIEXPORT jfloatArray JNICALL
Java_com_enhikyy_girassol_ai_NativeLlmEngine_nativeEmbed(
    JNIEnv* env, jobject /* this */, jstring text) {

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    if (!textStr) return nullptr;

    std::vector<float> embedding = girassol::LLMEngine::getInstance().embed(textStr);

    jfloatArray result = env->NewFloatArray(embedding.size());
    env->SetFloatArrayRegion(result, 0, embedding.size(), embedding.data());

    env->ReleaseStringUTFChars(text, textStr);
    return result;
}

} // extern "C"
