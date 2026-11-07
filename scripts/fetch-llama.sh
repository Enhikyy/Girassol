#!/usr/bin/env bash
# Fetches the exact llama.cpp revision LabForge expects into app/src/main/cpp/llama.cpp
set -euo pipefail
cd "$(dirname "$0")/.."

REV="${LLAMA_REV:-f7591a6}"
DEST="app/src/main/cpp/llama.cpp"

if [ -d "$DEST/.git" ]; then
  echo "llama.cpp já existe em $DEST"
  exit 0
fi

mkdir -p "$DEST"
git clone --depth 1 https://github.com/ggml-org/llama.cpp.git "$DEST"
if [ "$REV" != "HEAD" ]; then
  (cd "$DEST" && git fetch --depth 1 origin "$REV" && git checkout "$REV")
fi
rm -rf "$DEST/.git"
echo "llama.cpp pronto em $DEST"
