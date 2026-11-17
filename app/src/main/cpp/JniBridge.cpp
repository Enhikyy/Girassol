#include "VulkanRenderer.h"
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>

#define LOG_TAG "GirassolJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace girassol {

static VulkanRenderer* gRenderer = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeInitialize(
    JNIEnv* env, jobject /* this */, jobject surface, jint width, jint height,
    jboolean enableRayTracing, jboolean enablePostProcess, jfloat renderScale) {
    
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow from surface");
        return JNI_FALSE;
    }

    VulkanConfig config;
    config.width = width;
    config.height = height;
    config.enableRayTracing = enableRayTracing;
    config.enablePostProcess = enablePostProcess;
    config.renderScale = renderScale;

    gRenderer = new VulkanRenderer();
    bool result = gRenderer->initialize(window, config);
    
    ANativeWindow_release(window);
    
    if (!result) {
        delete gRenderer;
        gRenderer = nullptr;
        LOGE("VulkanRenderer initialization failed");
        return JNI_FALSE;
    }
    
    LOGD("VulkanRenderer initialized successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeShutdown(
    JNIEnv* env, jobject /* this */) {
    if (gRenderer) {
        gRenderer->shutdown();
        delete gRenderer;
        gRenderer = nullptr;
        LOGD("VulkanRenderer shutdown");
    }
}

JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeRenderFrame(
    JNIEnv* env, jobject /* this */, jfloat deltaTime) {
    if (gRenderer) {
        gRenderer->renderFrame(deltaTime);
    }
}

JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeOnSurfaceChanged(
    JNIEnv* env, jobject /* this */, jint width, jint height) {
    if (gRenderer) {
        gRenderer->onSurfaceChanged(width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeSetKodakParams(
    JNIEnv* env, jobject /* this */,
    jfloat grainAmount, jfloat halationAmount, jfloat bloomThreshold,
    jfloat bloomAmount, jfloat aberrationAmount, jfloat vignetteAmount,
    jfloat intensity, jfloat time) {
    if (gRenderer) {
        VulkanRenderer::KodakParams params;
        params.grainAmount = grainAmount;
        params.halationAmount = halationAmount;
        params.bloomThreshold = bloomThreshold;
        params.bloomAmount = bloomAmount;
        params.aberrationAmount = aberrationAmount;
        params.vignetteAmount = vignetteAmount;
        params.intensity = intensity;
        params.time = time;
        gRenderer->setKodakParams(params);
    }
}

JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeSetHeadlessMode(
    JNIEnv* env, jobject /* this */, jboolean headless) {
    if (gRenderer) {
        gRenderer->setHeadlessMode(headless);
    }
}

JNIEXPORT void JNICALL
Java_com_enhikyy_girassol_vulkan_VulkanRenderer_nativeUpdateAgents(
    JNIEnv* env, jobject /* this */,
    jfloatArray xArray, jfloatArray yArray, jfloatArray zArray,
    jfloatArray energyArray, jfloatArray socialArray, jfloatArray focusArray,
    jbooleanArray innieArray, jint count) {
    if (!gRenderer || count <= 0) return;

    std::vector<VulkanRenderer::AgentRenderData> agents;
    agents.reserve(count);

    jfloat* x = env->GetFloatArrayElements(xArray, nullptr);
    jfloat* y = env->GetFloatArrayElements(yArray, nullptr);
    jfloat* z = env->GetFloatArrayElements(zArray, nullptr);
    jfloat* energy = env->GetFloatArrayElements(energyArray, nullptr);
    jfloat* social = env->GetFloatArrayElements(socialArray, nullptr);
    jfloat* focus = env->GetFloatArrayElements(focusArray, nullptr);
    jboolean* innie = env->GetBooleanArrayElements(innieArray, nullptr);

    for (int i = 0; i < count; ++i) {
        VulkanRenderer::AgentRenderData agent;
        agent.x = x[i];
        agent.y = y[i];
        agent.z = z[i];
        agent.energy = energy[i];
        agent.social = social[i];
        agent.focus = focus[i];
        agent.isInnie = innie[i];
        agents.push_back(agent);
    }

    env->ReleaseFloatArrayElements(xArray, x, JNI_ABORT);
    env->ReleaseFloatArrayElements(yArray, y, JNI_ABORT);
    env->ReleaseFloatArrayElements(zArray, z, JNI_ABORT);
    env->ReleaseFloatArrayElements(energyArray, energy, JNI_ABORT);
    env->ReleaseFloatArrayElements(socialArray, social, JNI_ABORT);
    env->ReleaseFloatArrayElements(focusArray, focus, JNI_ABORT);
    env->ReleaseBooleanArrayElements(innieArray, innie, JNI_ABORT);

    gRenderer->updateAgents(agents);
}

} // extern "C"

} // namespace girassol