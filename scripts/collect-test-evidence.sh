#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
REPORT="app/build/reports/tests/testDebugUnitTest"
mkdir -p "$REPORT/design"
for name in modelos-escuro.png modelos-claro.png modelos-oled.png chat-escuro.png; do
  test -s "app/build/reports/design/$name"
  cp "app/build/reports/design/$name" "$REPORT/design/$name"
  echo "Compose capture: $name"
done
test -s build/reports/apk-audit.txt
cp build/reports/apk-audit.txt "$REPORT/apk-audit.txt"
echo "Capturas Compose e auditoria reunidas no relatório de testes."
