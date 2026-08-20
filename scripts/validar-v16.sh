#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
grep -q "versionCode 160" app/build.gradle
grep -q "1.6.0-cadastro-aprovacao" app/build.gradle
grep -q "firebase-auth" app/build.gradle
grep -q "RegisterActivity" app/src/main/AndroidManifest.xml
grep -q "createUserWithEmailAndPassword" app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java
grep -q "signInWithEmailAndPassword" app/src/main/java/com/rodriguesacai/entregador/LoginActivity.java
grep -q "AGUARDANDO_APROVACAO" app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java
grep -q "CloudinaryUploader" app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java
grep -q "request.auth.uid" firestore.rules
python - <<'PY'
from pathlib import Path
for p in Path('app/src/main/java').rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 if s.count('{')!=s.count('}'):
  raise SystemExit(f'Chaves desequilibradas: {p}')
print('Java: checagem estrutural OK')
PY
echo "UP Entregas V1.6: validação estática OK"
