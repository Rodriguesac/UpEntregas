#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
grep -q "versionCode 150" app/build.gradle
grep -q "1.5.0-profissional-sem-blaze" app/build.gradle
grep -q "showSystemHealth" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "FOREGROUND_SERVICE_LOCATION" app/src/main/AndroidManifest.xml
! grep -R "org.mindrot.jbcrypt" -n app/src/main/java app/build.gradle >/dev/null
! grep -R "PasswordVerifier" -n app/src/main/java >/dev/null
python - <<'PY'
from pathlib import Path
for p in Path('app/src/main/java').rglob('*.java'):
    s=p.read_text(encoding='utf-8')
    if s.count('{')!=s.count('}'):
        raise SystemExit(f'Chaves desequilibradas: {p}')
print('Java: checagem estrutural OK')
PY
echo "UP Entregas V1.5: validação estática OK"
