#!/usr/bin/env bash
# Reconstrói a árvore 3.0 somente na cópia efêmera do GitHub Actions.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
die() {
  echo "::error::bootstrap: $*"
  exit 1
}

SOURCE_BASE="ee2b99f545b302da13ff09f7a5025644944f0261"
CHECKPOINT_REV="3adb89ee562e65a0a7265d0a02c27640adb0bc5a"
CHECKPOINT_URL="https://raw.githubusercontent.com/Enhikyy/Girassol/${CHECKPOINT_REV}/.ci/source-checkpoint"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

echo "::group::Recuperar e verificar o checkpoint imutável"
mkdir -p "$STAGE/checkpoint" "$STAGE/scripts"
cp "$ROOT/build.gradle.kts" "$STAGE/build.gradle.kts"
for script in bootstrap-actions.sh build-native.sh fetch-llama.sh restore-design-fonts.sh audit-apk.sh collect-test-evidence.sh; do
  cp "$ROOT/scripts/$script" "$STAGE/scripts/$script" || die "script ausente: $script"
done
for index in 00 01 02 03 04 05 06 07 08 09; do
  curl --fail --location --retry 6 --retry-all-errors --retry-delay 3 \
    --connect-timeout 30 \
    "$CHECKPOINT_URL/part-$index" --output "$STAGE/checkpoint/part-$index" || die "download da parte $index"
done
PARTS=("$STAGE"/checkpoint/part-*)
[ "${#PARTS[@]}" = "10" ] || die "quantidade de partes: ${#PARTS[@]}"
cat "${PARTS[@]}" | base64 --decode > "$STAGE/source.patch.xz" || die "Base64 inválido"
echo "d0f0b69d277971b02069704d74bbb40921bac63a7287332de8c627eb41500388  $STAGE/source.patch.xz" | sha256sum --check --strict || die "SHA-256 do XZ"
xz -dc "$STAGE/source.patch.xz" > "$STAGE/source.patch" || die "XZ inválido"
echo "5ba1364cc81bc4d9f4ae6d336037e2e5af7e9256951a0faca1846ee86b8cb766  $STAGE/source.patch" | sha256sum --check --strict || die "SHA-256 do patch"
DIFFS="$(grep -c '^diff --git' "$STAGE/source.patch")"
[ "$DIFFS" = "55" ] || die "quantidade de diffs: $DIFFS"
if grep -q '^GIT binary patch' "$STAGE/source.patch"; then die "patch binário inesperado"; fi
echo "::notice::bootstrap: checkpoint íntegro; 10 partes e 55 diffs"
echo "::endgroup::"

echo "::group::Restaurar a base exata e aplicar a integração"
# Este reset atinge apenas o workspace descartável do runner. Nenhuma ref remota muda.
git fetch --depth 1 origin "$SOURCE_BASE" || die "fetch da base $SOURCE_BASE"
git reset --hard "$SOURCE_BASE" || die "reset da base no runner"
git clean -fdx || die "limpeza do workspace do runner"
git apply --check "$STAGE/source.patch" || die "git apply --check"
git apply "$STAGE/source.patch" || die "git apply"
cp "$STAGE/build.gradle.kts" "$ROOT/build.gradle.kts" || die "restauração do build principal"
mkdir -p "$ROOT/scripts"
cp "$STAGE/scripts/"*.sh "$ROOT/scripts/" || die "restauração dos scripts"
echo "::notice::bootstrap: base restaurada e checkpoint aplicado"
echo "::endgroup::"

echo "::group::Restaurar fontes e reconstruir bibliotecas ARM64"
bash scripts/restore-design-fonts.sh || die "restauração das fontes"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install "ndk;26.1.10909125" || die "instalação do NDK"
yes | sdkmanager --licenses > /dev/null 2>&1 || true
bash scripts/build-native.sh || die "build nativo ARM64"
echo "::notice::bootstrap: fontes e bibliotecas ARM64 prontas"
echo "::endgroup::"

echo "::group::Baixar e auditar o modelo Qwen3 offline"
MODEL=app/src/main/assets/offline/Qwen3-0.6B-Q8_0.gguf
mkdir -p "$(dirname "$MODEL")"
if [ ! -f "$MODEL" ]; then
  curl --fail --location --retry 6 --retry-all-errors --retry-delay 5 \
    --connect-timeout 30 \
    "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf" \
    --output "$MODEL" || die "download do Qwen3"
fi
[ "$(stat -c %s "$MODEL")" = "639446688" ] || die "tamanho do Qwen3"
echo "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031  $MODEL" | sha256sum --check --strict || die "SHA-256 do Qwen3"
echo "::notice::bootstrap: Qwen3 validado"
echo "::endgroup::"

[ ! -d "$ROOT/.ci/source-checkpoint" ] || die "marcador de checkpoint não foi removido"
echo "Bootstrap nativo concluído; código, fontes, ARM64 e GGUF prontos."
