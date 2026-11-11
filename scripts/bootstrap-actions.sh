#!/usr/bin/env bash
# Executed by the existing GitHub Actions workflow through build.gradle.kts.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

cat .ci/source-checkpoint/part-* | base64 --decode > /tmp/girassol-source.patch.xz
echo "d0f0b69d277971b02069704d74bbb40921bac63a7287332de8c627eb41500388  /tmp/girassol-source.patch.xz" | sha256sum --check --strict
xz -dc /tmp/girassol-source.patch.xz > /tmp/girassol-source.patch
echo "5ba1364cc81bc4d9f4ae6d336037e2e5af7e9256951a0faca1846ee86b8cb766  /tmp/girassol-source.patch" | sha256sum --check --strict
test "$(grep -c '^diff --git' /tmp/girassol-source.patch)" = "55"
! grep -q '^GIT binary patch' /tmp/girassol-source.patch
git apply --check /tmp/girassol-source.patch
git apply /tmp/girassol-source.patch

bash scripts/restore-design-fonts.sh
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install "ndk;26.1.10909125"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
bash scripts/build-native.sh

MODEL=app/src/main/assets/offline/Qwen3-0.6B-Q8_0.gguf
mkdir -p "$(dirname "$MODEL")"
if [ ! -f "$MODEL" ]; then
  curl --fail --location --retry 6 --retry-all-errors \
    "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf" \
    --output "$MODEL"
fi
test "$(stat -c %s "$MODEL")" = "639446688"
echo "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031  $MODEL" | sha256sum --check --strict

# The same runner invokes Gradle again for tests; do not apply the patch twice.
rm -rf "$ROOT/.ci/source-checkpoint"
echo "Bootstrap nativo concluído; código, fontes, ARM64 e GGUF prontos."
