#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
REPORT="$ROOT/build/reports/apk-audit.txt"
mkdir -p "$(dirname "$REPORT")"
: > "$REPORT"
APK="$(find "$ROOT/app/build/outputs/apk/debug" -maxdepth 1 -type f -name '*.apk' -print -quit)"
test -n "$APK"
SIZE="$(stat -c %s "$APK")"
test "$SIZE" -ge 419430400
{
  echo "APK=$APK"
  echo "APK_SIZE_BYTES=$SIZE"
} | tee -a "$REPORT"
export APK REPORT
python3 - <<'PY'
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile, ZIP_STORED
import os

apk = Path(os.environ["APK"])
report = Path(os.environ["REPORT"])
model_name = "assets/offline/Qwen3-0.6B-Q8_0.gguf"
required_libs = ["lib/arm64-v8a/liblabforge.so", "lib/arm64-v8a/libllama.so"]
lines = []
with ZipFile(apk) as archive:
    model = archive.getinfo(model_name)
    assert model.file_size == 639_446_688, f"Tamanho inválido do GGUF: {model.file_size}"
    assert model.compress_type == ZIP_STORED, "GGUF não está em ZIP_STORED"
    digest = sha256()
    with archive.open(model) as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    model_hash = digest.hexdigest()
    assert model_hash == "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031", model_hash
    lines.append(f"GGUF={model_name}; ZIP_STORED; SHA256={model_hash}")
    for name in required_libs:
        header = archive.read(name)[:20]
        assert header[:4] == b"\x7fELF", f"Não é ELF: {name}"
        assert header[4] == 2, f"Não é ELF64: {name}"
        machine = int.from_bytes(header[18:20], "little")
        assert machine == 183, f"Não é ARM64: {name}; e_machine={machine}"
        lines.append(f"ARM64_ELF64={name}")
text = "\n".join(lines) + "\n"
with report.open("a") as target:
    target.write(text)
print(text, end="")
PY
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$ANDROID_HOME"
TOOLS="$ANDROID_HOME/build-tools/37.0.0"
"$TOOLS/apksigner" verify --verbose --print-certs "$APK" | tee -a "$REPORT"
BADGING="$($TOOLS/aapt dump badging "$APK")"
printf '%s\n' "$BADGING" | sed -n '1,8p' | tee -a "$REPORT"
printf '%s\n' "$BADGING" | grep -F "package: name='app.girassol' versionCode='30' versionName='3.0.0'"
echo "Auditoria obrigatória do APK concluída."
