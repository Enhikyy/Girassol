# Native inference backends

Girassol builds the ARM64 llama.cpp engine for Android with:

- CPU backend enabled, with KleidiAI kernels for AArch64.
- Vulkan backend enabled and linked against the Android NDK Vulkan loader stub.
- Runtime backend discovery through llama.cpp, rather than assuming that a device has a usable GPU.
- Automatic CPU fallback if loading a model with GPU offload fails.
- GGUF model caching between chat turns and an explicit release method.

## OpenCL

OpenCL is deliberately not enabled in this Mali-targeted build. The upstream llama.cpp OpenCL backend is primarily documented and validated for recent Qualcomm Adreno GPUs; it is not a verified acceleration path for the Galaxy A22 5G's Mali GPU. Enabling it without a compatible vendor OpenCL runtime would create a false capability claim and could break loading.

## Device validation

A successful CI build proves that the Android ARM64 native libraries compile and link. It does not prove that a particular phone's Vulkan driver supports every shader or tensor operation. On-device verification must check:

1. Vulkan device enumeration in Girassol's backend diagnostics.
2. A short GGUF inference with GPU offload enabled.
3. Correct CPU fallback when Vulkan is unavailable or rejects an operation.
4. Sustained inference while monitoring memory pressure and Android process termination.

The app reports GPU offload only when llama.cpp reports a compiled GPU backend. A CPU-only runtime is shown as CPU, never mislabeled as GPU.
