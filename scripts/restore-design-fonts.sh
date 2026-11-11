#!/usr/bin/env bash
# Restaura fontes oficiais do Google Fonts e fixa os pesos usados pela UI.
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="app/src/main/res/font"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$DEST"

curl --fail --location --retry 6 --retry-all-errors \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/figtree/Figtree%5Bwght%5D.ttf" \
  --output "$TMP/Figtree.ttf"
curl --fail --location --retry 6 --retry-all-errors \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/PlusJakartaSans%5Bwght%5D.ttf" \
  --output "$TMP/PlusJakartaSans.ttf"

python3 -c 'import fontTools' 2>/dev/null || \
  python3 -m pip install --quiet --disable-pip-version-check 'fonttools>=4.59,<5'

python3 - "$TMP" "$DEST" <<'PY'
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from hashlib import sha256
from pathlib import Path
import sys

source = Path(sys.argv[1])
dest = Path(sys.argv[2])
jobs = [
    ("Figtree.ttf", "figtree_regular.ttf", 400),
    ("Figtree.ttf", "figtree_medium.ttf", 500),
    ("Figtree.ttf", "figtree_semibold.ttf", 600),
    ("Figtree.ttf", "figtree_bold.ttf", 700),
    ("PlusJakartaSans.ttf", "jakarta_semibold.ttf", 600),
    ("PlusJakartaSans.ttf", "jakarta_bold.ttf", 700),
]
for original, target, weight in jobs:
    font = TTFont(source / original)
    static = instantiateVariableFont(font, {"wght": weight}, inplace=True, updateFontNames=True)
    output = dest / target
    static.save(output)
    data = output.read_bytes()
    if len(data) < 10_000:
        raise RuntimeError(f"Fonte inválida: {output}")
    print(f"{target}: {len(data)} bytes; sha256={sha256(data).hexdigest()}")
PY
