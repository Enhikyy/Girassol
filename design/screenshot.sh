#!/usr/bin/env bash
# Regenera os mockups de design a partir de design/preview.html.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p design/screenshots
CHROME="${CHROME:-google-chrome}"
# capa 1600x900
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=1 --window-size=1600,900 --virtual-time-budget=3500 \
  --screenshot="design/cover.png" "file://$PWD/design/cover.html" >/dev/null 2>&1
echo "design/cover.png"
for s in home autodidact models train codex chat llamatik settings; do
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=2 --window-size=390,844 --virtual-time-budget=3500 \
    --screenshot="design/screenshots/$s.png" \
    "file://$PWD/design/preview.html?s=$s" >/dev/null 2>&1
  echo "design/screenshots/$s.png"
done
