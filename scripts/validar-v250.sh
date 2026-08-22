#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

grep -q "versionCode 250" app/build.gradle
grep -q "2.5.0-rastreamento-opcional" app/build.gradle
grep -q "applicationId 'com.rodriguesacai.entregador'" app/build.gradle
grep -q "state.terminal()" app/src/main/java/com/rodriguesacai/entregador/TrackingService.java
grep -q "rastreamentoPedidosHabilitados" app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java
grep -q "routeTrackingEnabledForOrder" app/src/main/java/com/rodriguesacai/entregador/DriverRepository.java
grep -q "entregadorAccuracy" firestore.rules
grep -q "entregadorSpeed" firestore.rules

python3 - <<'PY'
from pathlib import Path
for path in Path('app/src/main/java').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    balance = 0
    in_string = False
    escaped = False
    for char in text:
        if in_string:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == '"':
                in_string = False
        else:
            if char == '"':
                in_string = True
            elif char == '{':
                balance += 1
            elif char == '}':
                balance -= 1
            if balance < 0:
                raise SystemExit(f'Chaves inválidas em {path}')
    if balance:
        raise SystemExit(f'Chaves desequilibradas em {path}: {balance}')
print('Java: estrutura de chaves OK')
PY

echo "UP Entregas 2.5.0: validação estática OK"
