#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

test -f app/build.gradle
test -f .github/workflows/build-apk.yml
test -f app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
test -f app/src/main/java/com/rodriguesacai/entregador/Ui.java
test -f app/src/main/res/drawable-nodpi/rodrigues_logo.png

grep -q "versionCode 200" app/build.gradle
grep -q "2.0.0-profissional" app/build.gradle
grep -q "createSheet" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "Aceitar entrega" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "Confirmar retirada" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "Finalizar entrega" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "requireInternetForAction" app/src/main/java/com/rodriguesacai/entregador/MainActivity.java
grep -q "navItemView" app/src/main/java/com/rodriguesacai/entregador/Ui.java

python3 - <<'PY'
from pathlib import Path
for f in Path('app/src/main/java').rglob('*.java'):
    s=f.read_text()
    bal=0; ins=False; esc=False
    for ch in s:
        if ins:
            if esc: esc=False
            elif ch=='\\': esc=True
            elif ch=='"': ins=False
        else:
            if ch=='"': ins=True
            elif ch=='{': bal += 1
            elif ch=='}': bal -= 1
            if bal < 0: raise SystemExit(f'Chaves inválidas em {f}')
    if bal != 0: raise SystemExit(f'Chaves desequilibradas em {f}: {bal}')
print('Java: estrutura de chaves OK')
PY

echo "UP Entregas V2.0: validação estática OK"
