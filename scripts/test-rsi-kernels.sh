#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$(mktemp)"
trap 'rm -f "$BIN"' EXIT
CXX="${CXX:-c++}"
"$CXX" -std=c++17 -O2 -Wall -Wextra -Werror \
  "$ROOT/app/src/main/cpp/rsi_kernels.cpp" \
  "$ROOT/scripts/rsi_kernels_test.cpp" -o "$BIN"
"$BIN"
echo "RSI native kernels: PASS"
