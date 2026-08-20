#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
grep -q "versionCode 161" app/build.gradle
grep -q "1.6.1-cadastro-recuperacao" app/build.gradle
grep -q "FirebaseAuthUserCollisionException" app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java
grep -q "continueExistingAccount" app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java
grep -q "signInWithEmailAndPassword(email,pass)" app/src/main/java/com/rodriguesacai/entregador/RegisterActivity.java
python - <<'PY2'
from pathlib import Path
for p in Path('app/src/main/java').rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 if s.count('{')!=s.count('}'):
  raise SystemExit(f'Chaves desequilibradas: {p}')
print('Java: checagem estrutural OK')
PY2
echo "UP Entregas V1.6.1: validação estática OK"
