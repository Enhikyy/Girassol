#!/usr/bin/env bash
# Host regression tests for the production training bridge, without an Android device.
# Pass an existing Llama GGUF solely for tokenizer metadata; weights are synthetic.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
[ "$#" -eq 1 ] || { echo 'Usage: scripts/test-native-training.sh /path/to/tokenizer-reference.gguf' >&2; exit 2; }
reference="$(realpath "$1")"
llama="$(pwd)/app/src/main/cpp/llama.cpp"
host_build="${NATIVE_HOST_BUILD_DIR:-$HOME/runtime/girassol-native-tests/llama}"
test_dir="${NATIVE_TEST_DIR:-$(mktemp -d /tmp/girassol-training-tests.XXXXXX)}"
mkdir -p "$test_dir/android"
printf 'Native regression artifacts: %s\n' "$test_dir"
cmake -S "$llama" -B "$host_build" -DLLAMA_BUILD_COMMON=ON \
  -DLLAMA_OPENSSL=OFF -DLLAMA_CURL=OFF -DLLAMA_SUBPROCESS=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
  -DGGML_CPU_KLEIDIAI=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build "$host_build" --target llama-common -j "${JOBS:-2}"
cat > "$test_dir/android/log.h" <<'HEADER'
#pragma once
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_ERROR 6
inline int __android_log_print(int, const char *, const char *, ...) { return 0; }
HEADER
# Java cannot use the keyword "native" in a package name. Only remap JNI export
# names for the host harness. Keep stderr visible for fatal JVM/ggml diagnostics;
# stdout still exercises the production pipe-reader thread and callbacks.
sed -e 's/Java_dev_labforge_app_core_native_LlamaBridge_/Java_dev_labforge_app_core_probe_NativeTrainingProbe_/g' \
    -e 's/dup2(g_pipe_wr, STDERR_FILENO);/\/\* Host: keep fatal diagnostics visible. \*\//g' \
    app/src/main/cpp/labforge_jni.cpp > "$test_dir/bridge.cpp"
c++ -std=c++17 -O1 -g -fPIC -shared "$test_dir/bridge.cpp" \
  -I"$test_dir" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -I"$llama/include" -I"$llama/ggml/include" -I"$llama/src" -I"$llama/ggml/src" -I"$llama/common" \
  -L"$host_build/bin" -lllama -lllama-common -lggml -lggml-base \
  -Wl,-rpath,"$host_build/bin" -o "$test_dir/liblabforge.so"
c++ -std=c++17 scripts/native-tests/make-model.cpp \
  -I"$llama/include" -I"$llama/ggml/include" -L"$host_build/bin" -lllama -lggml-base \
  -Wl,-rpath,"$host_build/bin" -o "$test_dir/make-model"
"$test_dir/make-model" "$reference" "$test_dir"
"$JAVA_HOME/bin/javac" -d "$test_dir" scripts/native-tests/NativeTrainingProbe.java
ulimit -c 0
"$JAVA_HOME/bin/java" -Xcheck:jni -XX:+DisplayVMOutputToStderr \
  -Djava.library.path="$test_dir" -cp "$test_dir" dev.labforge.app.core.probe.NativeTrainingProbe "$test_dir"
