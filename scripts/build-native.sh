#!/usr/bin/env bash
# Builds liblabforge.so + llama.cpp shared libs for arm64 and copies them to jniLibs.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/26.1.10909125}"
CMAKE="${CMAKE_BIN:-cmake}"
BUILD="$ROOT/.native-build"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
JOBS="${JOBS:-2}"

if [ ! -d "$ROOT/app/src/main/cpp/llama.cpp" ]; then
  "$ROOT/scripts/fetch-llama.sh"
fi

mkdir -p "$BUILD" "$DEST"
VULKAN_LIBRARY="$(find "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android" -name libvulkan.so -type f | sort -V | tail -n 1)"
if [ -z "$VULKAN_LIBRARY" ]; then
  echo "Android NDK Vulkan loader stub not found" >&2
  exit 1
fi

"$CMAKE" -S "$ROOT/app/src/main/cpp" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DANDROID_STL=c++_shared \
  -DVulkan_LIBRARY="$VULKAN_LIBRARY" \
  -DVulkan_INCLUDE_DIR="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include" \
  -DVulkan_GLSLC_EXECUTABLE="$(command -v glslc)"

ninja -C "$BUILD" -j "$JOBS"

cp "$BUILD"/bin/libggml-base.so "$BUILD"/bin/libggml-cpu.so "$BUILD"/bin/libggml-vulkan.so "$BUILD"/bin/libggml.so \
   "$BUILD"/bin/libllama.so "$BUILD"/bin/libllama-common.so "$BUILD"/liblabforge.so "$DEST"/
cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$DEST"/

STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
for f in "$DEST"/*.so; do "$STRIP" --strip-unneeded "$f"; done
ls -lh "$DEST"
