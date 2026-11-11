#!/usr/bin/env bash
# Fetches the exact llama.cpp revision LabForge expects into app/src/main/cpp/llama.cpp
set -euo pipefail
cd "$(dirname "$0")/.."

REV="${LLAMA_REV:-f7591a6a01f9273de03331732bdc0b39721168be}"
DEST="app/src/main/cpp/llama.cpp"

if [ -d "$DEST/.git" ]; then
  echo "llama.cpp já existe em $DEST"
  exit 0
fi

mkdir -p "$DEST"
git -C "$DEST" init
git -C "$DEST" remote add origin https://github.com/ggml-org/llama.cpp.git
git -C "$DEST" fetch --depth 1 origin "$REV"
git -C "$DEST" checkout --detach FETCH_HEAD
rm -rf "$DEST/.git"
echo "llama.cpp $REV pronto em $DEST"
