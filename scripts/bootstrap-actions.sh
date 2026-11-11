#!/usr/bin/env bash
# Reconstrói a árvore 3.0 somente na cópia efêmera do GitHub Actions.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
LOG=/tmp/girassol-bootstrap.log
: > "$LOG"
exec > >(tee -a "$LOG") 2>&1
report_failure() {
  status=$?
  trap - ERR
  echo "::endgroup::"
  tail -n 12 "$LOG" | while IFS= read -r line; do
    line=${line//%/%25}
    line=${line//$'\r'/%0D}
    printf '::error::bootstrap: %s\n' "$line"
  done
  exit "$status"
}
trap report_failure ERR

SOURCE_BASE="ee2b99f545b302da13ff09f7a5025644944f0261"
CHECKPOINT_REV="3adb89ee562e65a0a7265d0a02c27640adb0bc5a"
CHECKPOINT_URL="https://raw.githubusercontent.com/Enhikyy/Girassol/${CHECKPOINT_REV}/.ci/source-checkpoint"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

echo "::group::Recuperar e verificar o checkpoint imutável"
mkdir -p "$STAGE/checkpoint" "$STAGE/scripts"
cp "$ROOT/build.gradle.kts" "$STAGE/build.gradle.kts"
for script in bootstrap-actions.sh build-native.sh fetch-llama.sh restore-design-fonts.sh; do
  cp "$ROOT/scripts/$script" "$STAGE/scripts/$script"
done
for index in 00 01 02 03 04 05 06 07 08 09; do
  curl --fail --location --retry 6 --retry-all-errors --retry-delay 3 \
    --connect-timeout 30 \
    "$CHECKPOINT_URL/part-$index" --output "$STAGE/checkpoint/part-$index"
done
PARTS=("$STAGE"/checkpoint/part-*)
test "${#PARTS[@]}" = "10"
cat "${PARTS[@]}" | base64 --decode > "$STAGE/source.patch.xz"
echo "d0f0b69d277971b02069704d74bbb40921bac63a7287332de8c627eb41500388  $STAGE/source.patch.xz" | sha256sum --check --strict
xz -dc "$STAGE/source.patch.xz" > "$STAGE/source.patch"
echo "5ba1364cc81bc4d9f4ae6d336037e2e5af7e9256951a0faca1846ee86b8cb766  $STAGE/source.patch" | sha256sum --check --strict
test "$(grep -c '^diff --git' "$STAGE/source.patch")" = "55"
! grep -q '^GIT binary patch' "$STAGE/source.patch"
echo "Checkpoint íntegro: 10 partes, 55 diffs, hashes verificados."
echo "::endgroup::"

echo "::group::Restaurar a base exata e aplicar a integração"
# Este reset atinge apenas o workspace descartável do runner. Nenhuma ref remota muda.
git fetch --depth 1 origin "$SOURCE_BASE"
git reset --hard "$SOURCE_BASE"
git clean -fdx
git apply --check "$STAGE/source.patch"
git apply "$STAGE/source.patch"
# O Gradle é invocado novamente para os testes; preserve os hooks e scripts atuais.
cp "$STAGE/build.gradle.kts" "$ROOT/build.gradle.kts"
mkdir -p "$ROOT/scripts"
cp "$STAGE/scripts/"*.sh "$ROOT/scripts/"
echo "Base $SOURCE_BASE reconstruída; checkpoint aplicado sem conflitos."
echo "::endgroup::"

echo "::group::Restaurar fontes e reconstruir bibliotecas ARM64"
bash scripts/restore-design-fonts.sh
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install "ndk;26.1.10909125"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
bash scripts/build-native.sh
echo "::endgroup::"

echo "::group::Baixar e auditar o modelo Qwen3 offline"
MODEL=app/src/main/assets/offline/Qwen3-0.6B-Q8_0.gguf
mkdir -p "$(dirname "$MODEL")"
if [ ! -f "$MODEL" ]; then
  curl --fail --location --retry 6 --retry-all-errors --retry-delay 5 \
    --connect-timeout 30 \
    "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf" \
    --output "$MODEL"
fi
test "$(stat -c %s "$MODEL")" = "639446688"
echo "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031  $MODEL" | sha256sum --check --strict
echo "::endgroup::"

# O git clean já removeu o marcador; nenhuma segunda aplicação ocorrerá.
test ! -d "$ROOT/.ci/source-checkpoint"
echo "Bootstrap nativo concluído; código, fontes, ARM64 e GGUF prontos."
