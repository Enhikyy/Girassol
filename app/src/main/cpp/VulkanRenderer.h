#pragma once

#include <vulkan/vulkan.h>
#include <android/native_window.h>
#include <memory>
#include <vector>
#include <functional>

namespace girassol {

struct VulkanConfig {
    uint32_t width = 1920;
    uint32_t height = 1080;
    bool enableRayTracing = true;
    bool enablePostProcess = true;
    float renderScale = 0.5f; // 540p -> 1080p upscaling
};

struct FrameData {
    VkCommandBuffer commandBuffer;
    VkSemaphore imageAcquired;
    VkSemaphore renderFinished;
    VkFence fence;
};

class VulkanRenderer {
public:
    VulkanRenderer();
    ~VulkanRenderer();

    // Initialize with ANativeWindow (from SurfaceView/TextureView)
    bool initialize(ANativeWindow* window, const VulkanConfig& config);
    void shutdown();

    // Main render loop
    void renderFrame(float deltaTime);
    
    // Resize handling
    void onSurfaceChanged(int width, int height);
    
    // Post-process parameters (Kodak film look)
    struct KodakParams {
        float grainAmount = 0.08f;
        float halationAmount = 0.15f;
        float bloomThreshold = 0.85f;
        float bloomAmount = 0.25f;
        float aberrationAmount = 2.0f;
        float vignetteAmount = 0_4f;
        float intensity = 1.0f;
        float time = 0.0f;
    };
    
    void setKodakParams(const KodakParams& params);
    
    // Headless mode (screen off)
    void setHeadlessMode(bool headless);
    
    // Agent data for rendering (positions, states)
    struct AgentRenderData {
        float x, y, z;
        float energy, social, focus;
        bool isInnie;
    };
    
    void updateAgents(const std::vector<AgentRenderData>& agents);

private:
    class Impl;
    std::unique_ptr<Impl> pImpl_;
};

} // namespace girassol