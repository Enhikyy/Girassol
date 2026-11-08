#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/runtime/android/sdk}/ndk/26.1.10909125}"
DEST="app/src/main/jniLibs/arm64-v8a"
LLAMA="app/src/main/cpp/llama.cpp"
[ -f "$LLAMA/include/llama.h" ] || ./scripts/fetch-llama.sh
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang++" \
  -std=c++17 -O3 -fPIC -shared -static-libstdc++ \
  -I"$LLAMA/include" -I"$LLAMA/ggml/include" -I"$LLAMA/vendor" \
  app/src/main/cpp/inference_jni.cpp -L"$DEST" -lllama \
  -Wl,-soname,libgirassol_inference.so -o "$DEST/libgirassol_inference.so"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$DEST/libgirassol_inference.so"
